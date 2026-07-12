# Decision Memory 구현 계획

overview 컨펌 완료. 이 문서는 **구현 단계**와 **결정 포인트(D1~D8)**를 담는다.
결정은 3단계에서 하나씩 확정하고 `03-decisions.md`에 기록한다.

> **#7·#8 substrate 재사용 전제**: `EmbeddingClient`(임의 텍스트 embed) 그대로 사용. pgvector 패턴
> (`real[]` 컬럼 + 쿼리 시 `::vector` 캐스팅) + in-memory 코사인(CI/dev 대체) 구조 재사용.
> `IssueEmbedding`/`IssueVectorSearch`/`IssueMemoryService`/`SimilarIssueEntry` 연동을 **미러링**한다.
> embedding은 API 켜기 전까진 로컬=가짜 semantic(한계 상속).

## #8과 다른 핵심: 등록 경로가 새로 필요

Issue Memory는 "분석된 리포트"가 자동 소스여서 사람 입력 UI가 없었다. Decision Memory는 **사람이 결정 메모를
직접 등록**한다 → 엔티티뿐 아니라 **REST 등록/조회 경로**가 새로 생긴다(#8엔 없던 축).

## 전체 그림

```
[등록] 사람이 결정 메모 등록(REST)
   └─(A) 결정 텍스트 임베딩 ── DecisionMemory 저장
[분석] 새 리포트 분석 시(AnalysisWorker)
   (B) 리포트 임베딩 → (C) 관련 결정 top-k
          └─(D) 분석 결과에 "관련 설계 결정" 참고로 노출
          └─(E?) 추천 방향 ↔ 결정 충돌 신호(범위는 D7에서 결정)
```

## 구현 단계 (결정에 종속)

각 단계는 관련 결정 확정 뒤 착수. 단계마다 컴파일/테스트 통과 상태로 커밋.

- **S1. 저장소** — `memory.DecisionMemory`(엔티티) + `DecisionMemoryRepository`.
  - 필드(안): projectId, title, body, embedding(`float[]`), createdAt (+ D1에서 확정)
  - 관련 결정: **D1(스키마), D3(임베딩 텍스트)**
- **S2. 등록 경로** — `DecisionMemoryController` + `Service` + `CreateRequest`/`Response`.
  - 등록 시 임베딩 생성·저장. 조회(프로젝트별 목록). 수정·삭제는 D2에 따라.
  - 관련 결정: **D2(등록 범위), D3**
- **S3. 결정 벡터검색** — `DecisionVectorSearch` seam + pgvector(프로덕션) + in-memory(CI) top-k.
  - 관련 결정: **D5(병렬 구현), D8(테스트)**
- **S4. 관련 결정 조회 서비스** — 리포트 임베딩 → top-k(프로젝트 스코프).
  - 관련 결정: **D4(쿼리 소스), D5**
- **S5. 분석 파이프라인 연동** — `AnalysisWorker`: 분석 시 관련결정 조회 → draft/Response에 주입.
  - 관련 결정: **D6(연동 지점), D7(충돌 범위)**
- **S6. Result** — `04-result.md` + roadmap #9 상태 갱신.

---

## 결정 포인트 (하나씩 확정)

### D1. 결정 메모 스키마 — 어떤 필드를 둘까
- (a) **title + body**(최소).
- (b) title + body + category(예: 아키텍처/보안/성능) + status(active/superseded).
- (c) 위 + author/date 등 운영 메타.
- **AI 추천: (a) title + body(최소).** 뼈대 단계라 검색·연동이 목적. category/status는 충돌·필터가
  본격화될 때(#10~11 이후) 넣어도 안 늦다. YAGNI. (createdAt은 표시용으로 자동 포함.)

### D2. 등록 범위 — CRUD 어디까지
- (a) **생성 + 프로젝트별 목록 조회**.
- (b) + 단건 조회.
- (c) + 수정/삭제(수정 시 재임베딩).
- **AI 추천: (a) 생성 + 목록 조회.** 분석 연동을 증명할 최소치. 수정/삭제는 재임베딩 훅만 있으면 쉬우나
  지금 없어도 흐름 검증엔 불필요. (#8 IssueMemory도 remember/findSimilar 최소로 시작했음.)

### D3. 결정 임베딩 텍스트 구성 — 무엇을 임베딩할까
- (a) **title + body**.
- (b) title만.
- **AI 추천: (a) title + body.** 결정의 의미는 본문(이유·맥락)에 있음. #8 I1의 "쿼리와 대칭" 원칙은
  여기선 다르게 적용된다 — 결정 저장은 body 포함, 쿼리(리포트)는 title+desc. **비대칭이지만 OK**:
  #8은 리포트↔리포트라 대칭이 맞았고, 여기는 리포트↔결정이라 애초에 다른 종류의 텍스트다(교차 검색).

### D4. 쿼리(리포트) 임베딩 소스 — 무엇으로 관련 결정을 찾을까
- (a) **리포트 title + description**(#8 IssueMemory.embeddingText와 동일 규칙 재사용).
- (b) 위 + 분석 준비물(candidateDomains·codeSearchTerms).
- **AI 추천: (a) title + description.** #8과 같은 소스라 `IssueMemoryService.embeddingText`를 재사용/공유
  가능. 분석 준비물까지 넣으면 결정 검색이 분석 파이프라인 중간 산출물에 결합돼 순서 의존이 커진다. 뼈대는 단순히.

### D5. 결정 벡터검색 구현 — 제네릭화 vs 병렬 (rule-of-three 지점)
- (a) 이제 `EmbeddingClient` 위에 벡터검색이 3번째(코드·이슈·결정) → `VectorSearch<T>`로 제네릭 추출.
- (b) **결정 전용 `DecisionVectorSearch` 병렬 구현**(#8 패턴 복제).
- (c) 저수준 primitive만 추출(네이티브 코사인 쿼리 → (id, score)) + 엔티티별 얇은 로더.
- **AI 추천: (b) 병렬 구현, 단 (c)를 진지하게 검토.** #8 I4에서 "세 번째 사용자 생기면 (c)로 추출"이라
  명시했고, 지금이 세 번째다. 그러나 pgvector 쿼리는 테이블·컬럼명이 박혀 있어 순수 추출이 생각보다 크다.
  **1차는 (b)로 빠르게 미러링하고, 중복이 실제로 아픈지 보고 (c) 추출을 별도 판단**하는 게 정직하다.
  → 이 결정은 "지금 추출을 강행하지 않는다"가 핵심. (사용자와 상의.)

### D6. 분석 파이프라인 연동 지점
- (a) `AnalysisWorker`에 통합 + 조회 서비스로도 노출(#8 I5-c와 동일).
- (b) 별도 조회 API만(분석 결과 미변경).
- **AI 추천: (a) 둘 다.** 3.3 목표가 "분석 시 관련 결정 검색"이라 워커 통합 필요. 조회 서비스로 두면
  UI·MCP가 재사용. #8 I5와 같은 판단. Response에 `relatedDecisions` record 신설(SimilarIssueEntry 미러).

### D7. 충돌 감지 범위 — 3.3 셋째 목표를 이번에 어디까지 (중대)
- (a) **관련 결정 검색·표시만**(충돌 판정은 사람 몫).
- (b) + 최소 rule 신호(추천 문구 ↔ 결정 텍스트 키워드 대비 등 얕은 힌트).
- (c) LLM 기반 충돌 판정.
- **AI 추천: (a) 검색·표시만.** 이유: ①embedding이 가짜 semantic인 단계라 (b) rule 충돌은 오탐이 신뢰를
  깎는다(overview 주의 5). ②(c) LLM 충돌 판정은 3.3이 아니라 4.3/4.4(#10~11)의 "추천 생성·근거 검증"에
  자연히 속한다. ③3.3의 셋째 목표는 "관련 결정을 **보여줘서** 사람이 충돌을 알아채게"로 충분히 만족된다.
  → 충돌 자동판정은 LLM 단계로 미룸을 결정에 명시.

### D8. 테스트 전략
- **AI 추천: #7·#8과 동일.** pgvector 실경로 CI 제외(H2 불가)→벤치마크 몫. 로컬 embedding + in-memory
  코사인으로 배선·기능 검증(등록→임베딩→관련결정 조회, 프로젝트 스코프, 결정 0건 시 빈 참고). H2 그린 유지.

---

## 결정 포인트 요약표

| ID | 주제 | AI 추천 |
|----|------|---------|
| D1 | 결정 메모 스키마 | title + body(최소) |
| D2 | 등록 범위 | 생성 + 목록 조회 |
| D3 | 결정 임베딩 텍스트 | title + body |
| D4 | 쿼리 임베딩 소스 | 리포트 title+description(#8 재사용) |
| D5 | 벡터검색 구현 | 병렬 구현(지금 제네릭 추출 강행 안 함) |
| D6 | 분석 연동 | 워커 통합 + 서비스 노출(둘 다) |
| D7 | 충돌 감지 범위 | 검색·표시만(자동판정은 LLM 단계로) |
| D8 | 테스트 | #7·#8과 동일(pgvector CI 제외·벤치마크) |

## 완료 기준(이 작업)

- 사람이 결정 메모를 등록할 수 있고, 등록 시 임베딩·저장된다.
- 새 리포트 분석 시 관련 설계 결정 top-k가 결과에 참고로 붙는다(프로젝트 스코프).
- 결정이 0건이어도 분석이 정상 동작(빈 참고).
- CI(H2)는 배선·기능 검증(pgvector는 stub/벤치마크). 로컬 embedding으로 외부 키 없이 그린.

## 열린 질문 / 리스크

- embedding이 로컬=가짜라 관련도 품질 낮음(#7 D3-1 켜야 진짜). "구조 검증"이 목적.
- D3-D4 **비대칭 임베딩**(결정=title+body, 쿼리=리포트 title+desc)이라 교차검색 품질은 API embedding 전엔
  특히 신뢰 낮음 — 뼈대 검증 목적임을 명확히.
- D5 rule-of-three: 지금이 세 번째 사용자라 (c) 추출 유혹이 크지만, 강행 시 pgvector 결합 때문에 되레
  복잡해질 수 있음. "복제가 실제로 아픈지" 본 뒤 판단.
- D7: 충돌 자동판정을 이번에 안 하는 건 "3.3 셋째 목표 미달"로 오해될 수 있음 → result에 "표시로 만족,
  자동판정은 #10~11" 명시 필요.
```