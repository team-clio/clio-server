# Issue Memory (RAG 3.2 / roadmap #8)

## 이 문서의 목적

과거 버그 리포트와 그 분석 결과를 **검색 가능하게 저장**하고, 새 리포트가 들어오면 **의미적으로 유사한
과거 이슈**를 찾아 분석에 참고로 붙인다. roadmap "추천 개발 순서" #8 (3.2 Issue Memory).

목표(roadmap 3.2):

> "이전에 비슷한 문제가 있었는지"를 분석 결과에 포함할 수 있어야 한다.

> **#7과의 관계**: Code Memory(#7)에서 깐 substrate를 재사용한다. `EmbeddingClient`(임의 텍스트 embed)는
> 그대로 재사용 가능(D0-B). 단, **벡터 검색은 코드 chunk 전용**(`CodeChunkVectorSearch`)이라 리포트용은
> 병렬 구현이 필요하다 — "범용 substrate"는 embed까지만 진짜 범용이고 store/search는 엔티티별로 갈린다(정직히 인정).
>
> **가짜 embedding 상속**: #7의 활성 embedding은 로컬 해싱(가짜 semantic, API 미활성 D3-1). Issue Memory의
> 의미검색도 **API를 켜기 전까진 같은 한계**를 물려받는다. 지금은 배선을 세우는 단계.

## 배경 / 현재 구현 상태

### 이미 있는 것 (Issue Memory가 얹힐 기반)

| 요소 | 위치 | 비고 |
|------|------|------|
| 버그 리포트 | `report.BugReport` | project, title, description, status, createdAt |
| 분석 잡·결과 | `analysis.AnalysisJob` | 완료 시 scores·issueType·keywords·domains·summary·relatedCode·flows·recommendedFix/Tests 보유 |
| 분석 워커 | `analysis.AnalysisWorker#run` | prepare → ranker → flow → draft → job 완료 |
| embed substrate | `memory.EmbeddingClient` | 임의 텍스트 embed (로컬 기본/API 옵션). **그대로 재사용** |
| 벡터검색 substrate | `memory.CodeChunkVectorSearch` | **코드 chunk 전용** — 리포트용은 병렬 구현 필요 |
| pgvector 패턴 | `memory.PgVectorCodeChunkVectorSearch` | `real[]` 컬럼 + 쿼리 시 `::vector` 캐스팅(D4-1) 재사용 가능 |

### 없는 것 (이번에 만들어야 하는 것)

1. **이슈 임베딩 저장소** — 과거 리포트(+분석결과)의 임베딩 + 메타를 담는 저장소.
2. **이슈 벡터 검색** — 새 리포트 임베딩으로 유사 과거 이슈 top-k.
3. **임베딩 시점** — 리포트 등록 시 / 분석 완료 시 / 별도 트리거 중 언제 임베딩할지.
4. **분석 연동** — 유사 과거 이슈를 분석 결과(참고 섹션)에 어떻게 노출할지.

## 문제점 / 필요

1. 지금은 매 리포트를 **과거 맥락 없이** 처음부터 분석한다 — 같은/비슷한 버그가 반복돼도 못 알아본다.
2. 과거에 "이 리포트는 이 파일을 고쳐서 해결됐다"는 정보가 쌓여도 재사용되지 않는다.
3. #9 Decision Memory, LLM 요약도 결국 "과거 이슈 검색"을 재사용하므로 여기서 뼈대를 세운다.

## 개선 방향 (뼈대)

### A. 이슈 임베딩 소스
리포트 title+description(+분석결과 요약?)을 임베딩한다. 무엇을 임베딩 텍스트에 넣을지는 결정 포인트.

### B. 저장 + 검색
임베딩을 저장하고, 새 리포트 임베딩으로 코사인 top-k. #7의 pgvector 패턴(`real[]`+캐스팅) 재사용.

### C. 임베딩 시점
리포트 등록 시 vs 분석 완료 시 vs 별도 트리거. (분석 결과까지 넣으려면 완료 시점이어야 함 → 결정 포인트.)

### D. 분석 연동
유사 이슈를 분석 결과에 **참고 섹션**으로 붙인다(자기 자신·같은 리포트 제외). 어떻게/얼마나는 결정 포인트.

### E. "쓸수록 좋아지는" 학습 루프 — 이번에 넣을지 (중대 결정)
D0-B에서 #8로 미뤄둔 것: 과거 리포트 ↔ 실제 수정 위치(ground truth)를 피드백 신호로 삼아 retrieval을
개선하는 루프. 이건 **outcome 캡처(분석 후보가 실제 맞았는지 기록)**가 선행돼야 한다. 이번 #8에서
(a) 유사이슈 검색만 / (b) 최소 outcome 캡처까지 / (c) 학습 루프까지 — 어디까지 할지는 결정 포인트.

## 범위 (초안 — plan에서 확정)

포함(예정): 이슈 임베딩 저장, 유사 이슈 벡터검색, 분석 결과에 유사이슈 참고 노출(최소 경로).
제외(초안): 본격 학습 루프·정교한 outcome 파이프라인(E에서 축소되면), LLM 기반 이슈 요약(#10),
대규모 성능 최적화, ANN 인덱스.

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **임베딩 텍스트 구성**: title+description만 / +분석결과(summary·domains 등) 포함.
- **임베딩 시점**: 리포트 등록 시 / 분석 완료 시 / 별도 트리거.
- **저장소 형태**: 별도 `issue_embedding` 엔티티 / 기존 리포트·잡에 임베딩 컬럼 추가.
- **벡터검색 구현**: `CodeChunkVectorSearch`를 제네릭화 / 리포트 전용 병렬 구현.
- **분석 연동 지점**: `AnalysisWorker`에 유사이슈 조회 추가 / 별도 조회 API만.
- **학습 루프 범위(E)**: 검색만 / 최소 outcome 캡처 / 학습 루프.
- **테스트 전략**: #7과 동일(pgvector 실경로는 CI 제외·벤치마크, 로컬 embedding으로 배선 검증).

## 관련 파일

```
src/main/java/ax/clio/memory/EmbeddingClient.java        - 재사용(임의 텍스트 embed)
src/main/java/ax/clio/memory/CodeChunkVectorSearch.java  - 제네릭화 후보(또는 병렬 구현 참고)
src/main/java/ax/clio/memory/PgVectorCodeChunkVectorSearch.java - pgvector 패턴 참고
src/main/java/ax/clio/report/BugReport.java              - 임베딩 소스
src/main/java/ax/clio/analysis/AnalysisJob.java          - 분석결과(임베딩 소스 후보)
src/main/java/ax/clio/analysis/AnalysisWorker.java       - 유사이슈 연동 지점 후보
src/main/java/ax/clio/memory/                            - (신규) 이슈 임베딩 저장·검색
```

## 주의사항

1. **테스트는 H2, pgvector는 CI 제외**(#7의 D4/D4-1과 동일 전략). 실경로는 벤치마크 몫.
2. **embedding은 API 켜기 전까진 가짜 semantic**(#7 D3 한계 상속). 이번은 배선·구조가 목적.
3. 기존 분석 흐름을 **깨지 않는 것**이 목표(유사이슈는 추가 참고지 대체 아님).
4. **자기 자신/같은 리포트를 유사이슈로 반환하지 않도록** 제외 처리 필요.
5. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```
