# Decision Memory (RAG 3.3 / roadmap #9)

## 이 문서의 목적

설계 결정이나 운영 메모를 **등록·검색 가능하게 저장**하고, 새 리포트를 분석할 때 **관련된 과거 결정**을
찾아 분석에 참고로 붙인다. roadmap "추천 개발 순서" #9 (3.3 Decision Memory).

목표(roadmap 3.3):

> - 설계 결정 메모를 등록할 수 있게 한다.
> - 분석 시 관련 설계 결정을 검색한다.
> - 추천 수정 방향이 기존 결정과 충돌하지 않게 한다.
> → "코드만 보고 판단하기 어려운 설계 맥락을 분석에 반영한다."

> **#7·#8과의 관계**: Code Memory(#7)·Issue Memory(#8)에서 깐 substrate를 그대로 재사용한다.
> `EmbeddingClient`(임의 텍스트 embed)는 재사용, 벡터검색은 **엔티티별 병렬 구현** 패턴(#8 `IssueVectorSearch`)을
> 따른다. 저장 엔티티(`real[]` 컬럼 + pgvector `::vector` 캐스팅), 서비스(remember/findSimilar),
> 분석 연동(`AnalysisWorker` → Response record) 구조를 미러링한다.
>
> **가짜 embedding 상속**: #7·#8과 동일하게 API embedding(D3-1)을 켜기 전엔 로컬 해싱(가짜 semantic).
> 이번도 배선·구조가 목적.

## #8과의 결정적 차이 (이번에 새로 생기는 것)

Issue Memory는 "분석된 리포트"가 자동으로 소스였다(사람 입력 없음). Decision Memory는 다르다:

1. **사람이 직접 등록하는 입력**이 소스다 — 설계 결정 메모를 넣는 **등록 경로(엔티티 + REST + 서비스)**가 새로 필요하다.
   (#8은 리포트가 이미 있었지만, "decision"은 지금 저장소 자체가 없다.)
2. **충돌 감지(3.3의 셋째 목표)** — "추천 수정 방향이 기존 결정과 충돌하지 않게 한다"는 단순 검색을 넘어선다.
   충돌을 어디까지 판정할지는 **중대 결정 포인트**(rule-based 신호 / 표시만 / LLM은 #10~11 몫).

## 배경 / 현재 구현 상태

### 이미 있는 것 (Decision Memory가 얹힐 기반)

| 요소 | 위치 | 비고 |
|------|------|------|
| embed substrate | `memory.EmbeddingClient` | 임의 텍스트 embed(로컬 기본/API 옵션). **그대로 재사용** |
| 이슈 임베딩 엔티티 | `memory.IssueEmbedding` | `real[]` 임베딩 컬럼 + project/report FK. **미러링 대상** |
| 이슈 벡터검색 seam | `memory.IssueVectorSearch` (+ InMemory/PgVector 구현) | 엔티티별 병렬 구현 패턴. **미러링 대상** |
| 이슈 메모리 서비스 | `memory.IssueMemoryService` | remember/findSimilar + 대칭 임베딩 텍스트. **미러링 대상** |
| 분석 연동 | `analysis.AnalysisWorker#run` | 유사이슈 조회 → draft에 주입 → Response. **연동 지점** |
| 결과 노출 record | `analysis.SimilarIssueEntry`, `AnalysisResultDraft`, `AnalysisJobResponse` | 참고 섹션 노출 패턴. **미러링 대상** |
| 프로젝트 스코프 | `project.Project` | 결정도 프로젝트 단위로 스코프 |
| 등록 REST 패턴 | `report.BugReportController`/`Service`/`CreateRequest`/`Response` | 결정 메모 등록 CRUD의 참고 |

### 없는 것 (이번에 만들어야 하는 것)

1. **결정 메모 엔티티** — 설계 결정 텍스트(제목/본문) + 임베딩 + 프로젝트 메타 저장소.
2. **결정 메모 등록 경로** — 사람이 결정 메모를 등록/조회(/수정·삭제?)하는 REST + 서비스.
3. **결정 벡터 검색** — 리포트 임베딩으로 관련 결정 top-k(#8 패턴 병렬 구현).
4. **분석 연동** — 관련 결정을 분석 결과(참고 섹션)에 노출.
5. **충돌 신호(선택)** — 추천 방향 ↔ 기존 결정 충돌을 어디까지 표시할지.

## 문제점 / 필요

1. 지금 분석은 **코드와 리포트 텍스트만** 본다 — "이 모듈은 일부러 이렇게 설계했다" 같은 맥락을 모른다.
2. 그래서 추천 수정 방향이 **기존 설계 결정을 거스르는** 제안을 할 수 있다(예: "여기 트랜잭션 걸어라"가
   이미 "성능상 의도적으로 뺐다"고 결정된 지점일 수 있음).
3. 설계 결정이 문서/머릿속에만 있고 **분석 파이프라인에 연결되지 않는다.**

## 개선 방향 (뼈대)

### A. 결정 메모 소스 / 등록
사람이 title + body(결정 내용/이유)를 등록한다. 임베딩 텍스트 구성(title만 / title+body)은 결정 포인트.
등록 범위(생성·조회만 / 수정·삭제까지)도 결정 포인트.

### B. 저장 + 검색
결정 임베딩을 저장하고, 리포트 임베딩으로 코사인 top-k. #8의 `real[]`+`::vector` 패턴 재사용.
쿼리 임베딩 소스(리포트 title+description / 분석 후보 도메인·검색어까지)는 결정 포인트.

### C. 분석 연동
관련 결정을 분석 결과에 **참고 섹션**으로 붙인다(#8 similarIssues 옆). 어떻게/얼마나(topK·임계)는 결정 포인트.

### D. 충돌 감지 범위 (중대 결정)
3.3 셋째 목표. 이번 #9에서 어디까지 하냐:
(a) **관련 결정 검색·표시만** (충돌 판정은 사람 몫) /
(b) 최소 rule-based 충돌 **신호**(예: 추천 문구 키워드 ↔ 결정 키워드 반대어) /
(c) LLM 기반 충돌 판정(→ #10~11 몫, 이번 범위 밖 유력).
→ 결정 포인트. **추천: (a)** (뼈대 단계·가짜 embedding 한계상 (b)도 신뢰도 낮음).

## 범위 (초안 — plan에서 확정)

포함(예정): 결정 메모 엔티티·등록 REST, 결정 벡터검색, 분석 결과에 관련 결정 참고 노출(최소 경로).
제외(초안): LLM 기반 충돌 판정(#10~11), 정교한 충돌 룰, 결정 버전관리/승인 워크플로우,
대규모 성능 최적화·ANN 인덱스, API embedding 활성화(#7 D3-1 backlog).

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **결정 메모 스키마**: 필드(title, body, 그 외 category/status/author 등 넣을지).
- **등록 범위**: 생성·조회만 / 수정·삭제까지 / 프로젝트별 목록.
- **임베딩 텍스트 구성**: title만 / title+body.
- **쿼리(리포트) 임베딩 소스**: #8과 동일 title+description / 분석 준비물(도메인·검색어)까지.
- **저장·검색 구현**: `IssueVectorSearch` 패턴 병렬 구현(예상) — 제네릭화는 하지 않음(#8 정직 원칙).
- **분석 연동 지점**: `AnalysisWorker`에 관련결정 조회 추가 + Response record 신설.
- **충돌 감지 범위(D)**: 검색·표시만 / rule 신호 / LLM(범위밖).
- **테스트 전략**: #7·#8과 동일(H2·InMemory로 배선 검증, pgvector 실경로는 CI 제외·벤치마크 몫).

## 관련 파일

```
src/main/java/ax/clio/memory/EmbeddingClient.java          - 재사용(임의 텍스트 embed)
src/main/java/ax/clio/memory/IssueEmbedding.java           - 미러링(결정 임베딩 엔티티)
src/main/java/ax/clio/memory/IssueVectorSearch.java        - 미러링(결정 벡터검색 seam)
src/main/java/ax/clio/memory/IssueMemoryService.java       - 미러링(결정 메모리 서비스)
src/main/java/ax/clio/analysis/AnalysisWorker.java         - 관련결정 연동 지점
src/main/java/ax/clio/analysis/SimilarIssueEntry.java      - 미러링(관련결정 Response record)
src/main/java/ax/clio/report/BugReportController.java 등   - 등록 REST/서비스 패턴 참고
src/main/java/ax/clio/memory/                              - (신규) 결정 메모 저장·검색·등록
```

## 주의사항

1. **테스트는 H2·InMemory, pgvector는 CI 제외**(#7 D4/#8과 동일 전략). 실경로는 벤치마크 몫.
2. **embedding은 API 켜기 전까진 가짜 semantic**(#7 D3 한계 상속). 이번은 배선·구조가 목적.
3. 기존 분석 흐름을 **깨지 않는 것**이 목표(관련결정은 추가 참고지 대체 아님).
4. 결정 메모가 **없어도** 분석이 정상 동작해야 한다(빈 리스트 = 참고 없음).
5. **충돌 판정을 과신하지 않는다** — 가짜 embedding + rule 단계에서 오탐이 사용자 신뢰를 깎는다.
6. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```