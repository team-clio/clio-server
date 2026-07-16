# 분석 파이프라인 그래프화 구현 계획

overview 컨펌 완료. 이 문서는 **구현 단계**와 **결정 포인트(G1~G8)**를 담는다.
결정은 3단계에서 하나씩 확정하고 `03-decisions.md`에 기록한다.

> **동작 보존이 1순위.** #12는 langgraph4j로 파이프라인을 그래프 골격으로 옮기는 리팩터+인프라다.
> rule-based 제거·순환·에이전트화는 이 골격 위에서 이후 단계.

## langgraph4j 핵심 API (확인됨)

- `new StateGraph<>(SCHEMA, initData -> new MyState(initData))` → `.addNode(name, NodeAction)` →
  `.addEdge(START, name)` / `.addEdge(name, END)` → `.compile()` 또는 `.compile(CompileConfig.builder()
  .checkpointSaver(saver).build())`.
- 노드: `NodeAction<S>` 구현, `Map<String,Object> apply(S state)` — **임의 커스텀 코드 호출 가능**(기존 서비스).
- State: `AgentState` 상속, `SCHEMA = Map<String,Channel<?>>`. 단일값 갱신은 기본 채널, 누적은 `Channels.appender`.
- 체크포인트: `MemorySaver`(코어 내장, in-memory) / `langgraph4j-postgres-saver`(영속). `RunnableConfig.builder()
  .threadId(id)` 로 세션 지정 → **같은 threadId로 재실행 시 마지막 체크포인트부터 재개**.

## 전체 그림 (선형 그래프, 동작 동일)

```
START → prepare → search(rank) → flow → memory → score(draft) → report(enrich+verify) → finalize → END
        (각 노드가 기존 서비스를 호출, state에 산출물 축적, 노드 후 체크포인트 저장)
```

## 구현 단계 (결정에 종속)

- **S1. 의존성 + 빈 그래프 골격** — build.gradle에 `langgraph4j-core`. `AnalysisState`(AgentState) + 최소
  StateGraph 컴파일·실행 스모크. 관련: **G1, G2, G3**
- **S2. 단계 노드화** — prepare/rank/flow/memory/draft/report/finalize를 `NodeAction`으로 감싸 기존 서비스 호출.
  관련: **G2, G4**
- **S3. 체크포인트 + 재시도** — saver 배선(threadId=jobId), 실패 단계부터 재개 경로. 관련: **G3, G5, G6**
- **S4. 워커 연동** — `AnalysisWorker#run`을 그래프 실행으로 교체, 예외→job.fail 매핑 유지. 관련: **G5, G8**
- **S5. 테스트** — 그래프 결과가 기존과 동일(동작 보존) + 부분 재시도 검증. 관련: **G7**
- **S6. Result** — `04-result.md` + roadmap #12 상태 갱신.

---

## 결정 포인트 (하나씩 확정)

### G1. 의존성 범위
- (a) **core만 + 내장 MemorySaver.**
- (b) + `postgres-saver`(영속 체크포인트).
- (c) + studio(dev 시각화).
- **AI 추천: (a) core + MemorySaver.** 뼈대는 그래프 배선·동작 보존이 목적. 영속(재시작 후 재개)은 postgres-saver로
  나중에. studio는 dev 편의라 필요 시. 의존성 얇게.

### G2. State 스키마 — 무엇을 담고 어떻게
- (a) **리치 도메인 객체**(preparation·candidates·flows·similarIssues·relatedDecisions·draft…)를 state에 그대로.
- (b) 경량 DTO/직렬화 형태로 축소.
- **AI 추천: (a) 리치 객체.** MemorySaver는 in-memory라 직렬화 불필요 → 기존 객체 그대로 노드 사이 전달. 단일값
  갱신 채널 사용. (postgres-saver로 갈 때 직렬화 대응은 그때. G3와 연동.)

### G3. 체크포인트 saver
- (a) **MemorySaver(우선).**
- (b) postgres-saver(영속·실경로).
- **AI 추천: (a) MemorySaver.** 배선·부분재시도 메커니즘을 외부 의존 없이 검증(#7·#8 테스트 철학과 동일 결).
  프로세스 내 재시도엔 충분. 재시작 넘어선 영속 재개는 postgres-saver backlog(직렬화 필요).

### G4. 노드 분해 입도
- (a) **기존 논리 단계 ≈ 1:1**: prepare / rank / flow / memory / draft / report / finalize (약 7노드).
- (b) 더 잘게(build/rank 분리, issue/decision 분리 등).
- (c) 더 굵게(묶음).
- **AI 추천: (a) 논리단계 1:1.** 로드맵 흐름(구조화→검색→흐름→Memory→점수→리포트생성→근거검증)과 매칭돼
  디버깅·재시도 단위로 자연스럽다. memory 노드는 issue+decision 함께(둘 다 리포트 임베딩 조회라 한 단위).

### G5. 트랜잭션 경계
- (a) **그래프 실행 전체를 기존 `run`의 단일 `@Transactional` 안에서**(동기 실행).
- (b) 노드별 트랜잭션.
- **AI 추천: (a) 단일 트랜잭션 유지.** 동작 보존이 목적이라 현재 경계 그대로. MemorySaver는 in-memory라 JPA
  트랜잭션과 충돌 없음. 노드별 트랜잭션·postgres-saver의 tx 상호작용은 영속화 결정 시 재검토.

### G6. 재시도 API — "실패 단계부터 재개"를 어떻게 노출
- (a) **threadId = analysisJobId. 재분석 요청 시 같은 threadId로 재실행 → 마지막 체크포인트부터 재개.**
- (b) 새 재시도 엔드포인트 신설.
- **AI 추천: (a) threadId=jobId로 capability 배선.** 우선 서비스/워커 레벨에서 재개가 되게 하고, 전용 REST
  노출은 필요 시(기존 재분석 경로 재사용 검토). 뼈대는 메커니즘 확보까지.

### G7. 동작 보존 검증
- **AI 추천**: (1) 그래프 실행 결과 draft가 기존 `run` 산출과 동일 필드값인지 통합 테스트, (2) 노드 단위 테스트(각
  노드가 기존 서비스 호출·state 갱신), (3) **부분 재시도 테스트**(중간 노드 강제 실패→재실행 시 앞 노드 재실행 안 함).
  외부 LLM은 mock. H2 그린 유지.

### G8. 에러 / 폴백
- **AI 추천**: 노드에서 예외 발생 시 그래프 실행이 중단되고, 워커가 잡아 `job.fail` + report FAILED(기존 try/catch
  동작 보존). enrich(LLM 리포트)의 내부 폴백(#10 L3)·근거검증(#11)은 노드 안에 그대로 유지.

---

## 결정 포인트 요약표

| ID | 주제 | AI 추천 |
|----|------|---------|
| G1 | 의존성 | core + MemorySaver |
| G2 | State | 리치 도메인 객체 |
| G3 | saver | MemorySaver 우선(postgres backlog) |
| G4 | 노드 입도 | 논리단계 1:1 (약 7노드) |
| G5 | 트랜잭션 | 단일 @Transactional 유지 |
| G6 | 재시도 | threadId=jobId 재개 capability |
| G7 | 검증 | 동작 보존 + 부분 재시도 테스트 |
| G8 | 에러 | 노드 예외→job.fail 매핑 보존 |

## 완료 기준(이 작업)

- 분석이 langgraph4j 그래프로 실행되고 결과가 기존과 **동일**(동작 보존).
- 각 단계 후 상태가 체크포인트로 저장되고, 실패 단계부터 재개할 수 있다(threadId=jobId).
- 노드 예외 시 기존처럼 job.fail/FAILED. 전체 스위트 그린(LLM mock, H2, 외부 의존 없음).

## 열린 질문 / 리스크

- **가장 리스크 큰 작업**: 핵심 파이프라인 리팩터 + 새 의존성. 동작 보존 테스트가 안전망.
- MemorySaver는 재시작 후 재개 불가 — 영속 재개는 postgres-saver 후속(직렬화 필요).
- 선형 그래프라 지금은 langgraph4j의 순환·조건분기 미사용 — rule-based 제거·에이전트화 때 활용(토대만).
- langgraph4j 버전 추적·API 변화 주의(코어만 얇게).
