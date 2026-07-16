# Code Memory 결과 (RAG 3.1 / roadmap #7)

결정(D0~D7, D3-1, D4-1) 확정 후 S1~S6 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.
(드리프트 방지: 코드가 바뀌면 여기도 갱신.)

## 무엇을 만들었나 (S1~S6)

전 구간: **스캔 → chunk 생성 → embedding → 저장**, **쿼리 → embedding → 벡터 top-k**,
**키워드 랭킹에 semantic fallback 보강**.

| 단계 | 산출물 | 위치 |
|------|--------|------|
| S1 | `CodeChunk` 엔티티 + `CodeChunkRepository`, `CodeChunkType{CLASS_HEADER,METHOD}` | `memory/` |
| S2 | `CodeChunker` — `CodeSymbol`→`CodeChunk`(메서드 본문 + 클래스 헤더, role 상속) | `memory/CodeChunker.java` |
| S3 | `EmbeddingClient`(범용) + `LocalEmbeddingClient`(기본) + `OpenAiCompatibleEmbeddingClient`(옵션) | `memory/` |
| S4 | `CodeChunkVectorSearch` + `PgVectorCodeChunkVectorSearch`(프로덕션) + `InMemoryCodeChunkVectorSearch`(CI/dev) | `memory/` |
| S5 | `CodeChunkIndexer`(스캔 훅) + `CodeMemorySearchService.semanticSearch` | `memory/` + `code/CodeAnalysisService` |
| S6 | `CodeCandidateRanker` semantic fallback 연동 | `analysis/CodeCandidateRanker.java` |

**적용된 결정**: D1(메서드+헤더 chunk) · D2(본문 DB 저장, 8KB 상한) · D3(로컬 기본/API 옵션) ·
D4+D4-1(pgvector, embedding은 `real[]` 컬럼 + 쿼리 시 vector 캐스팅) · D5(키워드 fallback, 임계<3) ·
D6(범용 서비스 + ranker fallback 둘 다) · D7(스캔 시 전체 재생성).

## 동작 방식 요약

- **인제스트**: `CodeAnalysisService.scan()` → 파일별로 심볼 저장 후 `CodeChunkIndexer.index()`가
  chunk 생성 → `EmbeddingClient.embed()` → `code_chunks` 저장. 스캔마다 chunk 전체 재생성(D7).
- **검색**: `CodeMemorySearchService.semanticSearch(projectId, query, topK)` = 쿼리 embedding → 벡터검색.
  런타임 벡터검색 빈은 `PgVectorCodeChunkVectorSearch`(유일 `@Component`).
- **분석 반영**: `CodeCandidateRanker.rank()`에서 키워드 후보가 3개 미만이면만 semanticSearch 호출해
  신규 후보 보강(키워드로 이미 잡힌 심볼은 덮어쓰지 않음). 이후 기존 그룹화/정렬/흐름추적/draft 재사용.

## 테스트 / 검증 상태

- CI(H2, 외부 의존 0): `code_chunks` 배열 컬럼 DDL 생성 + chunk 저장/조회, chunker 분리, 로컬 embedding
  결정성, in-memory 코사인 랭킹, **전 구간 통합**(chunk→embed→저장→semanticSearch로 관련 메서드 상위 반환),
  ranker semantic fallback 3종. 전체 스위트 그린.
- `@SpringBootTest contextLoads()`가 새 memory 빈 포함 전체 컨텍스트를 H2에서 부팅함(재확인).

## 정직한 한계 / 미완 (backlog)

1. **로컬 embedding은 진짜 semantic이 아니다(D3).** 토큰 해싱이라 "명사 자체가 다른 코드"(#7의 핵심 목표)는
   못 찾는다. CI가 검증하는 건 **배선**뿐. 실제 semantic 값은 API embedding을 켜야 나온다.
2. **pgvector 실경로는 CI 미검증(D4).** `PgVectorCodeChunkVectorSearch`의 네이티브 쿼리는 H2에서 못 도므로
   테스트에서 실행하지 않는다. **별도 벤치마크(비-CI, 실제 Postgres+pgvector) 미작성** → backlog.
3. **API embedding 자동선택·모델 정책 미배선(D3-1).** `OpenAiCompatibleEmbeddingClient`는 구현·테스트만
   되어 있고 활성 빈 선택은 후속. "어떤 config·embedding 모델·차원" 정책을 정하고 어댑터로 브리지해야 함.
4. **ANN 인덱스(HNSW/ivfflat) 미적용(D4-1).** 현재는 exact scan. 프로덕션 대규모 성능 필요 시 실제 `vector`
   컬럼+인덱스로 승격(인터페이스로 격리되어 교체 쉬움).
5. **semantic 출처 표시 없음.** semantic 보강 후보는 `CODE_TEXT` matchType으로 들어가 키워드 후보와
   구별되지 않는다(enum exhaustiveness 회피). 필요 시 출처 태그 추가 backlog.
6. **임계값 튜닝 미실시(D5).** fallback 임계(<3)·SEMANTIC_SCORE_SCALE(90) 상수는 미튜닝.
7. **학습 루프·outcome 캡처 제외(D0-B).** #8 Issue Memory의 몫. substrate만 범용으로 깔아둠.

## 런타임 셋업 (중요)

- 프로덕션 DB는 **pgvector 확장이 있어야 함.** compose 이미지를 `pgvector/pgvector:pg16`으로 올리고
  `db/init/01-create-vector-extension.sql`(`CREATE EXTENSION IF NOT EXISTS vector`)을
  `docker-entrypoint-initdb.d`로 마운트함. **최초 초기화 시 1회 실행**되므로, 기존 볼륨이 있으면 수동으로
  확장을 생성해야 한다(`docker exec ... psql -c 'CREATE EXTENSION IF NOT EXISTS vector;'`).
- embedding 컬럼 자체는 `real[]`이라 Hibernate DDL엔 확장이 불필요하다. 확장은 **쿼리 시 `::vector` 캐스팅**에만 필요.

## 다음(범위 밖)

- pgvector 벤치마크 러너(비-CI) 작성 → 실경로·유사도 품질 검증.
- API embedding 활성화(D3-1) 후 D7(재생성 시점) 비용 재검토.
- #8 Issue Memory가 이 substrate(embed+저장+유사도) 재사용.
