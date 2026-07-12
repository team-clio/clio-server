# 분석 파이프라인 그래프화 결정 히스토리

각 항목: **후보 → 선택 → 이유**. plan의 결정 포인트 G1~G8.
전제: 동작 보존 1순위. langgraph4j-core(Java 17+, 프로젝트 21). 노드=`NodeAction`, state=`AgentState`,
체크포인트=saver + `RunnableConfig.threadId`.

---

## G1. 의존성 범위

**선택: core + `langgraph4j-postgres-saver`.** (studio는 제외.)

**이유**: G3에서 Postgres 영속을 택함 → core에 더해 postgres-saver 필요. studio(dev 시각화)는 이번 범위 밖.

---

## G2. State 스키마 — 무엇을 담고 어떻게

**후보**
- (a) 리치 도메인 객체 그대로.
- (b) 경량 DTO/직렬화 형태.

**선택: (a) 리치 도메인 객체 — 단, 직렬화 가능하게(G3 Postgres 영속 때문).**

**이유·제약**
- 노드 사이로 preparation·candidates·flows·similarIssues·relatedDecisions·draft를 전달.
- **Postgres saver는 state를 직렬화·저장**하므로, state에 담는 값은 직렬화 가능해야 한다:
  - state에는 **JPA 엔티티(BugReport·Project 등) 대신 reportId·projectId 등 식별자/DTO**를 담는다(엔티티·프록시 직렬화 회피).
  - 도메인 record(RankedCodeCandidate·CodeFlow·RelatedCodeEntry·AnalysisResultDraft 등)는 직렬화 가능해야
    한다(`Serializable` 부여 또는 langgraph4j 직렬화기 설정). 세부는 구현에서 확정.

---

## G3. 체크포인트 saver (사용자 선택: 영속)

**후보**
- (a) MemorySaver(우선, in-memory).
- (b) Postgres 영속(postgres-saver).

**선택: (b) Postgres 영속.** 단, **테스트는 MemorySaver로 대체**한다.

**이유·전략**
- 사용자 선택: 앱 재시작 후에도 재개 가능한 **영속 체크포인트**.
- **테스트/CI는 MemorySaver**: postgres-saver는 실제 Postgres 필요 → H2에서 못 돎(#7 pgvector와 동일 제약).
  saver를 빈으로 분리해 **프로덕션=postgres-saver / 테스트=MemorySaver**. Postgres 실경로는 pgvector처럼
  **CI 제외**(별도 검증 몫).
- 함의: G2의 직렬화 제약, saver 빈 구성(프로필/조건부).

---

## G4. 노드 분해 입도

**선택: 논리단계 ≈ 1:1** — prepare / rank / flow / memory(issue+decision) / draft / report(enrich+verify) / finalize.

**이유**: 로드맵 흐름과 매칭돼 디버깅·재시도 단위로 자연스럽다. memory는 둘 다 리포트 임베딩 조회라 한 노드.
report 노드는 #10 enrich + #11 근거검증을 포함(내부 폴백·검증 유지).

---

## G5. 트랜잭션 경계

**선택: 그래프 실행 전체를 기존 `run`의 단일 `@Transactional` 안에서**(동기 실행).

**이유**: 동작 보존이 목적이라 현재 경계 유지. 체크포인트 saver의 쓰기와 JPA 트랜잭션 경계 상호작용은
postgres-saver 구현 시 확인(별도 커넥션/트랜잭션일 수 있음) — 문제 시 saver 쓰기를 트랜잭션 밖으로 조정.

---

## G6. 재시도 API (사용자 선택)

**선택: (a) threadId=jobId로 재개 capability만 배선.** 전용 REST는 이번에 안 만듦.

**이유**: 뼈대는 "실패 단계부터 재개가 된다"를 서비스·워커 레벨 + 테스트로 증명하는 데 집중. API 표면·기존
재분석 경로 연동은 수요 생기면 후속.

---

## G7. 동작 보존 검증

**선택**: (1) 그래프 실행 결과 draft가 기존 `run` 산출과 동일 필드값(동작 보존) 통합 테스트, (2) 노드 단위 테스트,
(3) **부분 재시도 테스트**(중간 노드 실패→재실행 시 앞 노드 재실행 안 함) — **MemorySaver로**. 외부 LLM mock, H2 그린.
Postgres saver 실경로는 CI 제외(별도 검증).

---

## G8. 에러 / 폴백

**선택**: 노드 예외 시 그래프 중단 → 워커가 잡아 `job.fail` + report FAILED(기존 try/catch 보존).
enrich 내부 폴백(#10 L3)·근거검증(#11)은 report 노드 안에 그대로 유지.

---

## 결정 완료 — 다음 단계

G1~G8 전부 확정. 구현(S1~S6, `02-plan.md`) 착수. Postgres 영속(G3)에 따른 직렬화·saver 빈 구성 유의.
`04-result.md`는 구현 진행에 따라 작성.
