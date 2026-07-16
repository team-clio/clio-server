# 분석 파이프라인 그래프화 — langgraph4j (roadmap #12 / LangGraph 적용)

## 이 문서의 목적

지금 절차적으로 이어붙인 분석 파이프라인을 **명시적 그래프(노드+엣지)**로 재구성해, 단계별 입출력을
저장하고 실패한 단계만 재시도할 수 있게 만든다. roadmap "추천 개발 순서" #12 (5. LangGraph 적용).

목표(roadmap 5):

> - 분석 단계를 분리해서 디버깅하기 쉽게 만든다.
> - 각 단계의 입력과 출력을 저장한다.
> - 실패한 단계만 재시도할 수 있게 만든다.

## 방향 결정: langgraph4j (사용자와 확정)

> **왜 langgraph4j인가** (A. 순수 Spring 직접 구현 vs B. langgraph4j 비교 후 B 선택):
> - 사용자가 **"조만간 rule-based를 다 걷어내고 풀 LLM/에이전트형으로 간다"**고 방향을 명시.
> - 흐름이 고정 순차로 남으면 A가 비례적이나, **LLM이 분기를 결정하는 순환·에이전트형**으로 갈 거면 그 그래프
>   엔진을 직접 만드느니 검증된 langgraph4j가 값을 한다(A로 짜고 나중에 갈아엎는 낭비 회피).
> - langgraph4j = LangGraph의 Java 포팅. **코어 단독 사용 가능**(Spring AI/langchain4j 불필요) → 기존 커스텀
>   `LlmClient`를 노드 안에서 그대로 호출. 체크포인트 saver(Postgres 등)로 "단계 저장+재시도"를 거의 공짜로 얻음.

> **⚠️ 순서·범위 주의(중요)**: "rule-based 다 없애기"는 #12보다 더 근본적인 전환이다. **#12는 rule-based를
> 걷어내는 작업이 아니라**, langgraph4j로 파이프라인을 그래프 골격으로 재구성하는 것(기존 단계를 노드로 감쌈).
> 그 골격 위에서 rule-based 노드를 하나씩 LLM 노드로 교체하는 건 **이후 단계**. #12의 완료 기준은 **동작 동일 +
> 단계 분리·저장·재시도 인프라**다.

## 배경 / 현재 구현 상태

### 지금의 분석 실행 (`AnalysisWorker#run`, 단일 `@Transactional` 메서드)

```
job.start / report ANALYZING
 → prepare (rule-based rawOnly | LlmReportSearchPreparer)   # 리포트 구조화
 → searchInputBuilder.build                                  # 검색 입력
 → codeCandidateRanker.rank                                  # 관련 코드 검색
 → flowTracer.trace                                          # 영향 흐름
 → issueMemoryService.findSimilar                            # 유사 이슈(#8)
 → decisionMemoryService.findRelevant                        # 관련 결정(#9)
 → buildDraft (점수·근거)                                     # 점수화
 → enrichWithLlmReport (LLM 리포트 + 근거검증)                # 리포트 생성(#10)+검증(#11)
 → job.complete / report COMPLETED / issueMemory.remember
 (예외 시: job.fail / report FAILED — 전체 재실행뿐)
```

### 문제점 (그래프화가 필요한 이유)

1. **단계가 코드 흐름에만 존재**한다. 각 단계의 입력·출력이 저장되지 않아 "어느 단계에서 뭐가 나왔나"를
   사후에 볼 수 없다(디버깅 난이도↑).
2. **부분 재시도 불가.** 마지막 LLM 단계에서 실패해도 검색·점수화까지 전부 다시 돈다(비용·시간 낭비).
3. **에이전트형 확장 불가.** 순차 하드코딩이라 "검증 실패 → 재검색 결정" 같은 순환·조건 분기를 넣기 어렵다.
   rule-based 제거 후 LLM 주도 흐름으로 가려면 그래프 골격이 선행돼야 한다.

### 없는 것 (이번에 만들어야 하는 것)

1. **그래프 정의** — 위 단계를 langgraph4j 노드로 감싸고 엣지로 연결.
2. **공유 상태(State)** — 단계 간 넘기는 중간 산출물(preparation·candidates·flows·memory·draft…)을 담는 state 스키마.
3. **체크포인트** — 단계별 상태 저장 + 실패 단계 재개(saver: 우선 in-memory/Postgres 중 결정).
4. **워커 연동** — `AnalysisWorker#run`이 그래프를 실행하도록 교체(동작 동일).

## 개선 방향 (뼈대)

### A. 의존성
`org.bsc.langgraph4j:langgraph4j-core`(Java 17+, 프로젝트는 21). 체크포인트는 `langgraph4j-postgres-saver`
또는 코어 내장 in-memory saver. 코어만으로 노드 안에서 기존 서비스(LlmClient·ranker·memory)를 호출.

### B. 그래프 골격
현재 순차 단계를 그대로 노드화(동작 보존). 지금은 **선형 그래프**(순환 없음). 이후 에이전트형으로 조건부 엣지·
순환을 추가할 토대만 마련.

### C. 상태 저장 + 재시도
체크포인트 saver로 각 단계 후 state 저장. 실패 시 마지막 성공 체크포인트부터 재개. 저장 대상·직렬화 형태는
결정 포인트(도메인 객체 직렬화 vs 요약 저장).

### D. 트랜잭션 경계
현재 `run`은 하나의 `@Transactional`. 그래프+체크포인트(별도 저장)와 JPA 트랜잭션 경계를 어떻게 맞출지 결정 포인트.

## 범위 (초안 — plan에서 확정)

포함(예정): langgraph4j 도입, 분석 파이프라인을 선형 그래프로 재구성(동작 동일), 단계별 상태 저장·실패단계 재시도,
워커 연동, 테스트(그래프 실행이 기존과 같은 결과·부분 재시도 검증).
제외(초안): rule-based 제거(별도·이후), 순환·LLM 주도 분기(에이전트형은 골격 이후), Studio UI 상시 배포,
멀티에이전트, MCP(#13).

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **의존성 범위**: core만 / + postgres-saver / + studio(dev).
- **State 스키마**: 무엇을 state에 담고 어떻게 직렬화할지(도메인 객체 vs 경량 DTO).
- **체크포인트 saver**: in-memory(우선) / Postgres(영속·실경로).
- **노드 분해 입도**: 현재 단계 1:1 노드 / 일부 묶음.
- **트랜잭션 경계**: 그래프 전체 1 트랜잭션 / 노드별 / 체크포인트는 트랜잭션 밖.
- **재시도 API**: 어떻게 "실패 단계부터 재개"를 노출할지(재분석 엔드포인트 재사용/신설).
- **동작 보존 검증**: 그래프 결과가 기존 `run`과 동일함을 어떻게 테스트할지.
- **에러/폴백**: 노드 실패 시 job.fail 매핑, 기존 try/catch 동작 유지.

## 관련 파일

```
build.gradle                                             - langgraph4j 의존성 추가
src/main/java/ax/clio/analysis/AnalysisWorker.java       - 그래프 실행으로 교체
src/main/java/ax/clio/analysis/AnalysisJob.java          - 상태·재시도 연동 후보
src/main/java/ax/clio/analysis/ (prepare·rank·flow·memory·report·verify) - 노드로 감쌀 기존 단계
src/main/java/ax/clio/analysis/graph/                    - (신규) 그래프 정의·노드·state
```

## 주의사항

1. **동작 보존이 1순위.** #12는 리팩터+인프라다. 그래프 전환 후에도 분석 결과가 기존과 같아야 한다
   (rule-based 제거·에이전트화는 이후).
2. **새 의존성 리스크**: langgraph4j 버전 추적, state 직렬화 제약. 코어만 얇게 도입해 결합을 최소화.
3. **트랜잭션/영속 경계**가 미묘하다(JPA + 체크포인트 saver). 처음엔 in-memory saver로 배선 검증, 영속은 결정 후.
4. **점진 전환 토대**: 지금은 선형 그래프로 동작만 옮기고, 조건부 엣지·순환은 rule-based 제거와 함께 이후.
5. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```