# Issue Memory 결과 (RAG 3.2 / roadmap #8)

결정(I1~I7) 확정 후 S1~S5 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.

## 무엇을 만들었나 (S1~S5)

전 구간: **분석 완료 → 리포트 임베딩·저장(remember)**, **새 리포트 분석 시 유사 과거 이슈 top-k 조회
(findSimilar, 자기 제외) → 분석 결과에 포함**.

| 산출물 | 위치 |
|--------|------|
| `IssueEmbedding` 엔티티 + `IssueEmbeddingRepository` | `memory/` |
| `IssueVectorSearch` + `PgVectorIssueVectorSearch`(프로덕션) + `InMemoryIssueVectorSearch`(CI/dev) | `memory/` |
| `IssueMemoryService` (remember / findSimilar) | `memory/IssueMemoryService.java` |
| 분석 결과 유사이슈 필드 (`SimilarIssueEntry`) | `analysis/` (Draft·Job·Response) |
| `AnalysisWorker` 연동 (완료 시 remember + 분석 시 findSimilar) | `analysis/AnalysisWorker.java` |

**적용된 결정**: I1(title+description만·대칭) · I2(분석 완료 시) · I3(별도 엔티티) · I4(리포트 전용 병렬 구현) ·
I5(워커 통합 + 서비스 노출) · I6(검색만) · I7(#7과 동일 테스트).

## 동작 방식 요약

- **remember**: `AnalysisWorker#run` 완료 지점에서 `IssueMemoryService.remember(report)` → title+desc 임베딩 →
  `issue_embeddings` 저장(재분석 시 갱신).
- **findSimilar**: 분석 시 새 리포트의 title+desc 임베딩으로 벡터 top-k, **자기/같은 리포트 제외** →
  `SimilarIssueEntry`(reportId·title·score)로 분석 결과(`AnalysisJobResponse.similarIssues`)에 포함.
- 런타임 벡터검색 빈은 `PgVectorIssueVectorSearch`(유일 `@Component`).

## 부수 개선 (중요)

- **로컬 embedding 토크나이저를 유니코드 대응으로 수정**(`LocalEmbeddingClient`). 기존엔 `[^a-z0-9]`로 잘라
  **한글을 전부 버려 zero 벡터**가 됐다 — 코드(영문)엔 문제없었지만 **버그 리포트는 한글**이라 Issue Memory엔 치명적.
  `[^\p{L}\p{N}]`로 바꿔 한글 토큰을 살림. (#7 code-memory에도 이로운 개선.)

## 테스트 / 검증 상태

- CI(H2, 외부 의존 0): `issue_embeddings` 배열 컬럼 DDL·저장/조회·리포트별 삭제, 로컬 embedding 한글 처리,
  **전 구간 통합**(remember→findSimilar로 토큰 겹치는 과거 이슈 상위 반환·자기 제외), 유사이슈 JSON 직렬화 왕복.
  전체 스위트 그린. `@SpringBootTest contextLoads()`가 새 빈 포함 전체 컨텍스트 부팅.

## 정직한 한계 / 미완 (backlog)

1. **의미검색은 아직 가짜(#7 D3 상속).** 로컬 embedding = 토큰 겹침이라 "표현이 다른 유사 이슈"는 못 찾음.
   실제 semantic은 #7 D3-1(API embedding 활성화)을 켜야 유효. Issue Memory도 그때 진짜가 됨.
2. **pgvector 실경로는 CI 미검증(I7).** `PgVectorIssueVectorSearch`는 H2에서 못 돌아 in-memory로 대체.
   실경로는 별도 벤치마크(미작성) 몫.
3. **학습 루프·outcome 캡처 없음(I6).** "쓸수록 좋아지는" 루프는 이번 범위 밖 — ground-truth 신호 선행 필요.
4. **분석된 리포트만 기억(I2).** 미분석 과거 리포트는 유사이슈 후보에 안 들어감(의도).
5. 벡터검색이 #7과 **중복 코드**(I4 병렬 구현). 세 번째 사용자 생기면 공통 primitive로 추출.
6. 유사이슈 표시는 title·score만. 과거 분석결과(요약·추천) 함께 보여주기는 backlog(응답에 reportId 있어 조회 가능).

## 런타임 셋업

- pgvector 확장은 #7에서 이미 준비됨(`db/init/01-create-vector-extension.sql`, compose `pgvector/pgvector:pg16`).
  `issue_embeddings`도 같은 `real[]`+캐스팅 패턴이라 추가 셋업 불필요.

## 다음(범위 밖)

- #7 D3-1(API embedding) 켜면 #7·#8 의미검색이 동시에 진짜가 됨.
- 유사이슈에 과거 분석결과 요약 붙이기.
- #9 Decision Memory가 이 substrate 재사용.
