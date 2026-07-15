# 패키지 컨벤션 정리 결정 히스토리

각 항목: **선택 → 이유**. plan의 결정 포인트 P1~P8.
전제: 동작 보존 1순위(순수 리팩터). 수렴할 규칙 — **컨트롤러(HTTP 표면)를 가진 도메인은 레이어드,
내부 컴포넌트 패키지는 평평.**

---

## 용어: "계약(contract)"이란 (P1 논의 중 정리)

`analysis/pipeline` 의 21개 파일은 성격이 셋이다.

- **포트(6, 인터페이스)** — 누가 무슨 일을 하는가. `CandidateSearcher`·`Scorer` 등.
- **오케스트레이터(2)** — `AnalysisGraph`·`AnalysisState`. 포트를 순서대로 호출하는 몸통.
- **계약(13, record·enum)** — **단계와 단계 사이에 오가는 데이터의 모양.**

계약의 정의는 이 두 줄이 보여준다.

```java
List<RankedCodeCandidate> search(BugReport report, ...);            // 검색 단계가 내놓는 것
AnalysisResultDraft score(..., List<RankedCodeCandidate> candidates, ...);  // 점수화 단계가 받는 것
```

`RankedCodeCandidate` 는 search 도 scoring 도 소유하지 않는, **둘이 주고받기로 약속한 데이터 모양**이다.
**위치가 중요한 이유**: 이게 `analysis/search/` 안에 있었다면 scoring 이 `import ax.clio.analysis.search.*`
를 해야 하고, 그건 S2에서 제거한 바로 그 결합이다. 중립 자리에 둬야 모든 단계가 `pipeline` 만 의존한다.

## P1. `contract` 내부 구성
**선택: `contract/` 에 13개 평평하게.**
**이유**: 계약 전체가 한 눈에 읽힌다. 단계별 그룹핑은 소유권 착시를 만드는데, 애초에 **중립이라는 전제와
모순**된다 — `RankedCodeCandidate` 를 `contract/search/` 에 넣는 순간 "search 것"이 되지만 실제로는
scoring·flow 도 함께 쓴다. 경계에 걸친 계약을 억지로 한 단계에 배치해야 하는 문제가 생긴다.

## P2. `AnalysisGraph`·`AnalysisState` 위치
**선택: `pipeline/` 루트 유지** (`pipeline/port`·`pipeline/contract` 와 형제).
**이유**: 오케스트레이터는 포트도 계약도 아니고 "pipeline 그 자체"라는 게 구조에서 드러난다.
`pipeline/graph/` 는 파일 2개짜리 디렉터리가 되어 얻는 게 없다.

## P3. `scoring → flow` 결합 해소 방법
**선택: `layerOf`/`UNKNOWN_LAYER` 를 계약(`FlowNode`)으로 이동.**
**이유**: `layerOf(String role)` 는 그래프 상태와 무관한 순수 함수이고, 그 role 어휘의 소유자는 이미
계약인 `FlowNode.role()` 이다. 즉 `CodeDependencyGraph` 에 있던 게 **잘못된 자리**였고 제자리를 찾아주는 것.
결과적으로 flow·scoring 둘 다 계약에만 의존 → 의존 규칙 충족. 중복 없음(복제안 대비), 계약·파서·테스트를
건드리는 enum 승격안 대비 순수 리팩터 범위 유지.

## P4. 포트의 JPA 엔티티 유출 / `pipeline ↔ job` 순환
**선택: 이번 범위 제외 — 별도 태스크로 기록.**
**이유**: 포트 5개(`ReportPreparer`·`ReportGenerator`·`Scorer`·`CandidateSearcher`·`MemoryRetriever`)의
시그니처 + 그래프 + 단계 impl + 테스트가 전부 바뀌는 **설계 변경**이지 컨벤션 정리가 아니다. 구조 수렴과
섞으면 리뷰가 불가능해지고 "동작 보존" 안전망도 약해진다.
**남는 것**: 이번 작업 후에도 `pipeline ↔ job` 순환은 그대로다. `task-pipeline-ports/04-result.md` 의
"한계 1번"을 **정확한 원인(포트 시그니처의 엔티티 유출)** 으로 갱신해 후속 태스크의 출발점으로 남긴다.

## P8. 커밋 분할
**선택: S1~S7 단계별 7커밋.**
**이유**: PR 커밋 목록만으로 작업 전개가 읽혀야 한다는 워크플로우 규칙에 부합하고, **이동 커밋과 로직
커밋이 섞이지 않아** 리뷰 가능. 매 커밋 컴파일·테스트 그린으로 위험 관리.

---
