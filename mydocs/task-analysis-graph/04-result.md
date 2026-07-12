# 분석 파이프라인 그래프화 결과 (roadmap #12 / LangGraph 적용)

결정(G1~G8) 확정 후 S1~S5 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.

## 무엇을 만들었나 (S1~S5)

절차적 `AnalysisWorker.run` 파이프라인을 **langgraph4j StateGraph**로 재구성했다(동작 동일). 각 단계가 노드가
되고, 체크포인트로 단계별 상태가 저장돼 실패 단계부터 재개할 수 있다.

| 산출물 | 위치 |
|--------|------|
| `langgraph4j-core:1.5.14` 의존성 | `build.gradle` |
| `AnalysisState`(AgentState, 공유 상태) | `analysis/AnalysisState.java` |
| `AnalysisGraph`(그래프 정의·노드·MemorySaver·run) | `analysis/AnalysisGraph.java` |
| `AnalysisWorker`(얇은 드라이버: 상태전이·영속·실패) | `analysis/AnalysisWorker.java` |
| state 운반 record에 `Serializable` | `analysis/*.java`(8개 record) |
| 테스트(그래프 동작 보존 + 재개) | `test/.../AnalysisGraphTest.java`, `GraphResumeTest.java` |

**적용된 결정**: G1(core 1.5.14) · G2(리치 record + Serializable) · G3(MemorySaver, 재정) · G4(노드 6개:
prepare→search→flow→memory→draft→report) · G5(단일 @Transactional 유지) · G6(threadId=jobId 재개) ·
G7(동작 보존+재개 테스트) · G8(노드 예외→job.fail).

## 동작 방식 요약

- `AnalysisWorker.run`: job.start·ANALYZING → `analysisGraph.run(jobId)` → job.complete·COMPLETED·remember.
  노드 예외는 밖으로 전파돼 워커가 job.fail·FAILED(기존 try/catch 보존).
- `AnalysisGraph`: 그래프를 시작 시 1회 compile(MemorySaver 부착). `run(jobId)`는 threadId=jobId로 invoke.
  노드는 기존 서비스를 호출하고 산출물을 state에 축적(prepare/search/flow/memory/draft/report).
- **재개(G6)**: `checkpointSaver.get(config)`로 체크포인트 존재를 확인 → 있으면 `invoke(null, config)`로 마지막
  단계부터 재개, 없으면 jobId를 실어 처음부터.

## 구현 중 밝혀진 것 (정직한 기록)

1. **MemorySaver도 state를 직렬화한다.** plan/결정에서 "in-memory라 직렬화 불필요"라 적었으나 **틀렸다.**
   langgraph4j는 saver 종류와 무관하게 체크포인트 시 Java 직렬화한다. → state 운반 record에 `Serializable`
   부여, 엔티티를 감싼 `ScoredIssue`/`ScoredDecision`은 state에서 빼고 memory 노드에서 plain entry로 매핑.
   (결과적으로 G3에서 Postgres 대신 MemorySaver를 고른 것과 무관하게 직렬화는 필요했다.)
2. **재개 관용구는 `invoke(null, config)`.** 빈 Map(`Map.of()`)을 주면 처음부터 재실행된다. 저장 체크포인트에서
   이어가려면 입력을 `null`로 줘야 한다(GraphResumeTest로 확정).
3. **langgraph4j 최신 버전은 문서/요약과 달랐다.** 웹 요약은 "1.8.20"이라 했으나 Maven Central 실측은 정식
   1.5.14 / postgres-saver는 베타(1.6.0-beta5)만. 그래서 G3를 MemorySaver로 재정.

## 테스트 / 검증 상태

- `AnalysisGraphTest`: 서비스 mock + RAW_ONLY 잡으로 그래프 실행 → rule-based 점수(importance 50·difficulty 33·
  risk 20)·relatedCode·summary가 기존 계산과 동일(동작 보존).
- `GraphResumeTest`: 최소 그래프+MemorySaver로 노드 b 첫 실패→재개 시 완료 노드 a는 재실행 안 되고 b만
  재실행됨을 검증(G6/G7 재개 메커니즘).
- 전체 스위트 그린. `@SpringBootTest contextLoads`가 `AnalysisGraph` 빈(그래프 compile 포함) 부팅.

## 정직한 한계 / 미완 (backlog)

1. **MemorySaver=in-memory → 앱 재시작 후 재개 불가(G3).** 영속 재개는 postgres-saver 정식화(현재 베타뿐) 또는
   에이전트형 전환 시점에 도입. 그때 G2 직렬화가 그대로 활용됨.
2. **선형 그래프뿐.** 순환·조건부 엣지·LLM 주도 분기는 미사용 — rule-based 제거·에이전트화와 함께 이후.
   #12는 그 골격만 세웠다.
3. **재시도는 capability만(G6).** threadId=jobId 재개가 되지만 전용 REST·재분석 경로 연동은 미노출.
4. **동작 보존은 테스트로 보증**(단위 wiring 테스트 + 기존 스위트 그린). 실제 코드베이스 대상 종단 비교 벤치는 없음.
5. **beta postgres-saver 미도입.** 영속 원하면 core까지 beta로 올려야 함(리스크).

## 다음(범위 밖)

- rule-based 노드를 LLM 노드로 점진 교체(사용자 방향: 조만간 rule-based 제거) — 그때 조건부 엣지·순환 도입.
- postgres-saver 정식화 시 영속 체크포인트 전환(직렬화는 이미 대응).
- #13 MCP 적용.
