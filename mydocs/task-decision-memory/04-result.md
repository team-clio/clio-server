# Decision Memory 결과 (RAG 3.3 / roadmap #9)

결정(D1~D8) 확정 후 S1~S5 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.

## 무엇을 만들었나 (S1~S5)

전 구간: **사람이 설계 결정 메모를 등록(REST)** → 임베딩·저장, **새 리포트 분석 시 관련 결정 top-k 조회
(findRelevant, 프로젝트 스코프) → 분석 결과에 참고로 포함**.

| 산출물 | 위치 |
|--------|------|
| `DecisionMemory` 엔티티(title+body+embedding) + `DecisionMemoryRepository` | `memory/` |
| `DecisionVectorSearch` + `PgVectorDecisionVectorSearch`(프로덕션) + `InMemoryDecisionVectorSearch`(CI/dev) | `memory/` |
| `DecisionMemoryService` (register / findByProject / findRelevant) | `memory/DecisionMemoryService.java` |
| 등록 REST (`DecisionMemoryController` + Create/Response DTO) | `memory/` |
| 분석 결과 관련결정 필드 (`RelatedDecisionEntry`) | `analysis/` (Draft·Job·Response) |
| `AnalysisWorker` 연동 (분석 시 findRelevant) | `analysis/AnalysisWorker.java` |

**적용된 결정**: D1(title+body 최소) · D2(생성+목록) · D3(결정=title+body) · D4(쿼리=리포트 title+desc,
#8 재사용) · D5(병렬 복제, 추출 보류) · D6(워커 통합+서비스 노출) · D7(표시만, 자동판정 없음) ·
D8(#7·#8과 동일 테스트).

## 동작 방식 요약

- **register**: `POST /api/decisions`(projectId·title·body) → `DecisionMemoryService.register` → title+body 임베딩 →
  `decision_memories` 저장. `GET /api/decisions?projectId=`로 프로젝트별 목록(최신순).
- **findRelevant**: `AnalysisWorker#run`에서 새 리포트의 title+desc 임베딩으로 벡터 top-k(프로젝트 스코프) →
  `RelatedDecisionEntry`(decisionId·title·score)로 분석 결과(`AnalysisJobResponse.relatedDecisions`)에 포함.
- 런타임 벡터검색 빈은 `PgVectorDecisionVectorSearch`(유일 `@Component`).
- #8과 달리 자동 remember 없음 — 결정은 **사람이 등록**하므로 워커는 조회만 한다.

## 의도적 비대칭 (D3/D4)

- 결정 저장 임베딩 = **title+body**(결정의 의미는 본문에 있음).
- 쿼리 임베딩 = **리포트 title+desc**(`IssueMemoryService.embeddingText` 재사용).
- #8은 리포트↔리포트라 대칭이 맞았지만, 여기는 **리포트↔결정의 교차 검색**이라 애초에 다른 종류 텍스트다.
  비대칭이라 API embedding 전엔 교차 품질이 특히 낮음 — 뼈대 검증이 목적.

## 테스트 / 검증 상태

- CI(H2, 외부 의존 0): `decision_memories` 배열 컬럼 DDL·저장/조회, **전 구간 통합**
  (register→findRelevant로 토큰 겹치는 결정 상위 반환·프로젝트 스코프 유지), 결정 0건 시 빈 리스트,
  관련결정 JSON 직렬화 왕복(기존 `AnalysisJob` 경로 재사용). 전체 스위트 그린.
- `@SpringBootTest contextLoads()`가 새 빈(컨트롤러·서비스·pgvector 검색) 포함 전체 컨텍스트 부팅.

## 정직한 한계 / 미완 (backlog)

1. **충돌 "자동 판정"은 안 함(D7).** 3.3 셋째 목표("추천이 기존 결정과 충돌하지 않게")는 이번엔 **관련 결정을
   보여줘 사람이 알아채게** 하는 것으로 충족. 추천↔결정 자동 충돌 판정은 **LLM 근거검증(#11)의 몫**으로 미룸.
2. **의미검색은 아직 가짜(#7 D3 상속).** 로컬 embedding = 토큰 겹침이라 "표현이 다른 관련 결정"은 못 찾음.
   실제 semantic은 #7 D3-1(API embedding 활성화)을 켜야 유효.
3. **pgvector 실경로는 CI 미검증(D8).** `PgVectorDecisionVectorSearch`는 H2에서 못 돌아 in-memory로 대체.
   실경로는 별도 벤치마크(미작성) 몫.
4. **등록은 생성+목록만(D2).** 단건 조회·수정·삭제(재임베딩)는 backlog. 결정이 틀리면 지우고 재등록.
5. **결정 스키마 최소(D1).** category/status 등은 충돌 필터가 본격화되는 #10~11에 필요 시 추가.
6. 벡터검색이 #7·#8과 **중복 코드**(D5 병렬 구현, 세 번째 사용자). 지금 제네릭/primitive 추출은 강행하지
   않음 — pgvector 쿼리에 테이블명이 박혀 이득이 제한적. 중복이 실제로 아파질 때 별도 스텝에서 추출 판단.
7. **UI 미노출.** `relatedDecisions`는 응답에만 포함. 결과 화면 렌더링은 Web UI(#6 보류)와 함께.

## 런타임 셋업

- pgvector 확장은 #7에서 이미 준비됨(`db/init/01-create-vector-extension.sql`, compose `pgvector/pgvector:pg16`).
  `decision_memories`도 같은 `real[]`+캐스팅 패턴이라 추가 셋업 불필요(Hibernate `ddl-auto=update`가 테이블 생성).

## 다음(범위 밖)

- #7 D3-1(API embedding) 켜면 #7·#8·#9 의미검색이 동시에 진짜가 됨.
- #11 근거검증에서 추천↔결정 충돌 자동 판정(D7 미룬 부분).
- 결정 메모 CRUD 보강(수정·삭제·재임베딩)과 결과 화면 노출.
