# Issue Memory 결정 히스토리

각 항목: **후보 → 선택 → 이유**. plan의 결정 포인트 I1~I7.
전제: #7 substrate 재사용(`EmbeddingClient` 범용, pgvector `real[]`+캐스팅 패턴, in-memory 코사인 CI 대체).

---

## I1. 이슈 임베딩 텍스트 구성

**후보**
- (a) title + description만.
- (b) title + description + 분석결과(summary·domains·issueType).

**선택: (a) title + description만**

**이유**
- 유사도 검색은 **저장분 벡터 vs 쿼리(새 리포트) 벡터**를 비교하는데, 새 리포트는 **분석 전**이라 쿼리엔
  title+desc뿐이다. 저장분에 분석결과를 넣으면 저장분엔 분석 전문어휘(`PaymentService`·`트랜잭션`·난이도)가
  섞이고 쿼리엔 없어 **두 벡터가 서로 다른 분포**가 됨 → 유사도 왜곡(비대칭).
- 저장·쿼리 둘 다 title+desc로 임베딩해 **대칭(apples-to-apples)** 유지.
- 분석결과는 임베딩에 넣지 않고 **표시용 메타**로만 붙여 "이 유사 이슈는 이렇게 분석됐다"를 보여준다
  (신호 가치는 유지, 대칭성은 안 깨짐).

---

## I2. 임베딩·저장 시점

**후보**
- (a) 리포트 등록 시.
- (b) 분석 완료 시.
- (c) 별도 트리거.

**선택: (b) 분석 완료 시**

**이유**
- 유사 이슈의 가치는 "그때 **어떻게 분석/해결**됐나"에 있음 → 메모리 = "분석된 이슈"만.
- 유사이슈 표시 시 그 이슈의 분석결과(요약·추천)를 함께 보여줄 수 있음.
- 로컬 embedding은 싸서 완료 시 임베딩 부담 없음(API 켜면 비용/지연 재검토).
- 절충: 미분석 과거 리포트는 메모리에 없음 — 의도된 선택.

---

## I3. 저장소 형태

**후보**
- (a) 별도 `IssueEmbedding` 엔티티(reportId 참조 + embedding + 표시 메타).
- (b) 기존 `BugReport`/`AnalysisJob`에 embedding 컬럼 추가.

**선택: (a) 별도 `IssueEmbedding` 엔티티**

**이유**
- 리포트/잡 엔티티를 memory 관심사로 오염시키지 않음(도메인 분리).
- #7 `CodeChunk`와 같은 결(memory 패키지). 재임베딩·삭제가 독립적.

---

## I4. 이슈 벡터검색 구현

**후보**
- (a) `CodeChunkVectorSearch`를 제네릭 `VectorSearch<T>`로 승격해 공유.
- (b) 리포트 전용 `IssueVectorSearch` 병렬 구현(같은 pgvector 패턴 복제).
- (c) 공통 저수준 primitive만 추출 + 엔티티별 얇은 로더.

**선택: (b) 리포트 전용 병렬 구현** (세 번째 사용자 생기면 (c)로 추출)

**이유**
- 반환 타입이 엔티티별로 달라(ScoredCodeChunk vs ScoredIssue) 제네릭화는 지금 과설계.
- 두 번째 사용자일 뿐이라 복제가 싸다(rule of three). 세 번째 사용자 시 (c) primitive로 추출.
- D0-B의 "범용"은 `EmbeddingClient`까지가 진짜 범용이고 store/search는 엔티티별인 걸 인정.

---

## I5. 분석 파이프라인 연동 지점

**후보**
- (a) `AnalysisWorker` 통합만.
- (b) 별도 조회 API만.
- (c) 둘 다(워커 통합 + 조회 서비스 노출).

**선택: (c) 워커 통합 + 조회 서비스 노출**

**이유**
- #8 목표가 "분석 결과에 유사이슈 포함"이라 워커 통합 필요.
- 조회를 서비스로 두면 향후 UI·MCP·#9 Decision Memory가 재사용. #7 D6와 같은 판단.

**연동 형태**: `AnalysisWorker#run` 완료 지점에서 (1) 이 리포트 임베딩 저장(I2), (2) 새 리포트로 유사이슈
top-k 조회해 draft/결과에 참고로 포함(자기/같은 리포트 제외).

---

## I6. 학습 루프 범위 — "쓸수록 좋아지는"을 이번에 어디까지 (중대)

**후보**
- (a) 유사이슈 검색만(retrieval only).
- (b) + 최소 outcome 캡처(분석 후보가 실제 맞았는지 표시 훅).
- (c) 학습 루프까지(피드백으로 retrieval 개선).

**선택: (a) 유사이슈 검색만**

**이유**
- roadmap 3.2 범위는 "유사 이슈 찾아 참고"까지.
- 학습 루프는 outcome ground-truth(실제 수정 위치)가 선행돼야 하고 그 신호 설계가 큰 일. 지금 섞으면
  #8이 비대해져 "작게 쪼개기" 규율을 깬다. outcome/학습은 이후 별도 스텝.
- (오프라인 eval은 이미 `analysis/eval` 하네스가 있음 — 온라인 학습 루프와는 별개.)

---

## I7. 테스트 전략

**선택: #7과 동일** (대안 없음 — pgvector는 H2에서 못 돎)

- pgvector 실경로는 CI 제외 → 별도 벤치마크. 로컬 embedding + in-memory 코사인으로 배선·기능 검증
  (등록/분석완료→임베딩→유사이슈 조회, 자기 제외). 전체 스위트 H2 그린 유지.

---

## 결정 완료 — 다음 단계

I1~I7 전부 확정. 구현(S1~S6, `02-plan.md`) 착수 가능. `04-result.md`는 구현 진행에 따라 작성.
