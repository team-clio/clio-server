# 분석 파이프라인 그래프화 결정 히스토리

각 항목: **후보 → 선택 → 이유**. plan의 결정 포인트 G1~G8.
전제: 동작 보존 1순위. langgraph4j-core(Java 17+, 프로젝트 21). 노드=`NodeAction`, state=`AgentState`,
체크포인트=saver + `RunnableConfig.threadId`.

---

## G1. 의존성 범위

**선택: `org.bsc.langgraph4j:langgraph4j-core:1.5.14`(정식)만.** (postgres-saver·studio 제외.)

**이유**: G3 재정(아래) — postgres-saver는 베타(1.6.0-beta5)만 존재해 핵심 파이프라인에 베타를 넣지 않기로 함.
→ core 정식 1.5.14 + 내장 MemorySaver. studio(dev 시각화)는 범위 밖.

---

## G2. State 스키마 — 무엇을 담고 어떻게

**후보**
- (a) 리치 도메인 객체 그대로.
- (b) 경량 DTO/직렬화 형태.

**선택: (a) 리치 도메인 객체.** (MemorySaver라 직렬화 강제 아님 — 단 JPA 엔티티는 state에 안 담음.)

**이유·제약**
- 노드 사이로 preparation·candidates·flows·similarIssues·relatedDecisions·draft를 전달.
- MemorySaver(in-memory)라 직렬화가 강제되진 않으나, **JPA 엔티티(BugReport·Project) 대신 reportId·projectId
  등 식별자를 state에 담는다**(엔티티·프록시·지연로딩 얽힘 회피, 후일 postgres-saver 전환 대비). 리포트 본문 등
  노드가 필요로 하는 값은 조회하거나 초기 state에 실어 전달. 세부는 구현에서 확정.

---

## G3. 체크포인트 saver (사용자 선택: 재정됨)

**후보**
- (a) MemorySaver(in-memory).
- (b) Postgres 영속(postgres-saver).

**최초 선택: (b) → 재정: (a) MemorySaver.**

**재정 이유(새 사실)**: postgres-saver는 Maven Central에 **베타(1.6.0-beta5)만** 존재하고 정식이 없다. Postgres를
쓰려면 core까지 베타로 맞춰야 하는데, **동작 보존이 목적인 핵심 파이프라인 리팩터에 베타 의존성을 넣는 것**은
과한 리스크다. 사용자 재확인 후 **정식 core 1.5.14 + MemorySaver**로 확정.
- 함의: 프로세스 내 분할 재시도는 검증되지만 **앱 재시작 후 재개는 안 됨**(in-memory). **영속 재개는 backlog**
  (postgres-saver 정식화 또는 에이전트형 전환 시점에 도입). 그때 G2 직렬화 대응.

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
(3) **부분 재시도 테스트**(중간 노드 실패→재실행 시 앞 노드 재실행 안 함) — MemorySaver로. 외부 LLM mock, H2 그린.
(G3 재정으로 MemorySaver가 프로덕션 겸용이라 CI 제외 경로 없음.)

---

## G8. 에러 / 폴백

**선택**: 노드 예외 시 그래프 중단 → 워커가 잡아 `job.fail` + report FAILED(기존 try/catch 보존).
enrich 내부 폴백(#10 L3)·근거검증(#11)은 report 노드 안에 그대로 유지.

---

## 결정 완료 — 다음 단계

G1~G8 전부 확정. 구현(S1~S6, `02-plan.md`) 착수. Postgres 영속(G3)에 따른 직렬화·saver 빈 구성 유의.
`04-result.md`는 구현 진행에 따라 작성.
