# Code Memory 결정 히스토리

각 항목: **후보 → 선택 → 이유**. 상위 방향 결정은 D0-*, plan의 결정 포인트는 D1~D7.

---

## D0-A. RAG 접근 재정의 — "풀 RAG"인가 "도구로서의 semantic"인가

**맥락**: plan은 원래 chunk+embedding+하이브리드를 "주 retrieval의 한 축"으로 두고 ranker에 항상
블렌딩하는 그림이었다. 여기에 "코드에선 RAG보다 agentic search가 낫다"는 Anthropic 주장이 제기됐다.

**후보**
- (a) 원안대로 풀 embedding RAG 파이프라인 구축(항상 벡터후보 블렌딩).
- (b) semantic을 "하나의 도구"로 축소 — 키워드·구조 검색이 주, semantic은 필요할 때 부르는 fallback/툴.
- (c) 방향 전환 — embedding 보류, 구조·키워드 primitive 강화 + MCP/LLM 툴링 우선(로드맵 재조정).

**선택: (b) 하이브리드, 재정의된 형태**
- 주 경로 = **키워드 + 구조 검색**(에이전트가 오케스트레이션, MCP 준비).
- semantic search = **필요할 때 부르는 도구/fallback**(명사가 코드와 다를 때). 항상 섞지 않음.
- 재료(chunk 저장 + embedding + `semanticSearch`)는 그대로 만든다.

**이유**
- "리뷰 삭제 → deleteReview/removeReview" 같은 동사 모호성은 명사(review)가 안정적으로 greppable해서
  agentic search(번역→명사 grep→열거→읽기)로 풀린다. 이 경우 embedding은 불필요 → 풀 RAG(a)는 약하다.
- 어휘 격차의 *진짜* 케이스(명사 자체가 다름: "리뷰" vs `Comment`)만 semantic이 값을 한다.
- **에이전트는 Clio 루프에 항상 있다**(등록 CRUD 이미 존재, "필요할 때만 쓴다"). 이 "필요할 때만" 철학을
  semantic search 자체에 적용 → 항상 블렌딩(a) 대신 필요 시 호출(b).
- Anthropic 지적과 충돌하지 않으면서(주 경로는 agentic/구조·키워드), 어휘격차 진짜 케이스도 커버.
- (c) 전환은 로드맵을 크게 흔들어 지금은 과함. (b)가 재료를 살리면서 방향을 맞춘다.

**영향**: D5(하이브리드 결합)·D6(연동)의 성격이 "항상 벡터후보 추가"에서 "필요할 때 호출하는 fallback/툴"로 바뀐다.

---

## D0-B. substrate 범용화 + 학습 루프(#8) 유예

**맥락**: "프로젝트를 진행할수록 성능이 좋아지는 장기기억"을 지금 넣고 싶다는 요구. 이는 로드맵
#8 Issue Memory(+#9)의 영역.

**후보**
- (a) #7 스코프 유지하되 semantic substrate를 **범용·재사용 가능**하게 설계(코드 chunk가 첫 사용자). 학습 루프는 #8.
- (b) #7에 **최소 outcome 캡처 훅**(분석 후보가 실제 맞았는지 표시)까지 추가. 학습 루프는 여전히 #8.
- (c) #8 Issue Memory를 **지금 #7과 함께** 크게 진행.

**선택: (a) 범용 substrate + 학습 루프 #8 유예**

**이유**
- "저장한다 ≠ 좋아진다". 진짜 성능 향상은 **피드백 신호(실제 수정 위치=ground truth)**가 있어야 하고,
  그게 없으면 과거의 추측을 쌓아 **실수를 증폭**한다. 그 신호 설계는 #8의 몫.
- 학습 루프는 retrieval 기반 위에서 돈다. 그 기반을 지금 #7이 만든다 → 순서상 #7 먼저.
- 지금 섞으면 #7이 비대해지고 "작게 쪼개 순차 진행" 규율을 깬다.
- semantic substrate를 "코드 전용"이 아니라 "임의 텍스트 embed+저장+유사도검색"으로 범용화하면,
  #8이 리포트를 같은 레일에 얹어 **싸게** 붙는다.

**영향**: S1(모델·저장소)·S3(embedding)·S4(벡터검색)을 코드 chunk에 하드코딩하지 않고 범용 인터페이스로
설계한다. outcome 캡처/학습 루프는 이번 범위에서 제외(#8).

---

## D1. Chunk granularity — 코드를 어느 단위로 자를까

**후보**
- (a) 메서드 단위(+클래스 헤더): 메서드/생성자마다 chunk, 클래스는 선언+필드 헤더 chunk.
- (b) 클래스 단위: 타입 하나 = chunk 하나.
- (c) 둘 다.

**선택: (a) 메서드 단위(+클래스 헤더)**

**이유**
- 벡터 변별력·결과 정밀도 근거에 동의. **클래스는 너무 크다** — 여러 메서드의 평균이 돼 벡터가 흐릿해지고
  결과 범위도 넓어짐.
- `CodeSymbol`에 메서드 startLine/endLine이 이미 있어 경계를 공짜로 얻음.
- (c)는 뼈대 단계에서 chunk·임베딩 비용 2배와 중복 매칭 부담이 커서 보류.

---

## D2. Chunk 본문(코드 텍스트) 저장 여부

**후보**
- (a) DB에 본문 저장(길이 상한, 예: 8KB): 임베딩 입력·스니펫 표시에 바로 사용, 파일 변경/이동에도 검색 유지.
- (b) 라인 범위만 저장: 표시할 때 `rootPath`에서 재읽기(현 `CodeSearchService` 방식).

**선택: (a) DB에 본문 저장(길이 상한)**

**이유**
- 임베딩 입력·스니펫 표시에 바로 쓸 수 있음.
- **파일 변경/이동에도 검색이 안 깨지는 게 크다** — 스캔 시점 스냅샷을 chunk가 자체 보유.

---

## D3. Embedding 소스

**후보**
- (a) 실제 embedding API만 (`/embeddings`, `LlmConfig` 재사용).
- (b) 결정적 로컬 대체만 (토큰 해시/bag-of-tokens, 외부 의존 0).
- (c) 인터페이스 + 두 구현 (로컬 기본, API 옵션).

**선택: (c) `EmbeddingClient` 인터페이스 + 로컬 결정적 구현(기본) + OpenAI호환 API 구현(설정 시)**

**이유**
- 외부 키·비용 없이 **전체 파이프라인과 테스트가 H2에서 결정적으로** 돈다(#7 완료기준).
- API 구현은 신규 배선이 거의 없음 — 기존 `OpenAiCompatibleLlmClient`(RestClient+`LlmConfig`, Bearer)를
  그대로 미러링하고 endpoint만 `/embeddings`(`{"model","input"}` → `{"data":[{"embedding":[...]}]}`)로 바꾸면 됨.
- (a)만: 테스트가 외부 API/모킹에 묶임. (b)만: 실제 semantic 품질로 가는 길이 막힘.

**정직한 한계(반드시 result에 명시)**
- 로컬 결정적 구현(토큰 해시/bag-of-tokens)은 **의미 검색이 아니다** — "같은 토큰 겹침"이라 키워드 매칭의
  벡터 버전일 뿐. "명사 자체가 다른 코드 찾기"(#7의 진짜 목표)는 로컬로는 **원리적으로 불가**.
- 로컬 구현이 검증하는 것은 딱 하나: chunk→embed→저장→코사인 top-k→fallback **배선이 돈다**는 것.
  실제 semantic 값은 `LlmConfig`에 embedding 설정을 줘서 API를 켤 때만 나온다.

**영향**: S3에서 `EmbeddingClient`(범용, D0-B)를 두고 로컬 구현을 default 빈으로. API 구현은 config 존재 시 사용.
D7(재생성)은 로컬이 싸서 스캔 시 전체 재생성이 무난하나, API를 켜면 비용/지연으로 재검토 트리거.

---

## D4. 벡터 저장·유사도 검색 방식

**후보**
- (a) 벡터를 직렬화 컬럼에 저장 + 앱단 코사인 계산 (Postgres·H2 동일, 의존성 0).
- (b) pgvector 확장 (런타임 성능↑, Postgres 전용).
- (c) 외부 벡터 DB (범위 밖).

**선택: (b) pgvector** — 벡터 저장·유사도검색은 프로덕션 Postgres의 pgvector(`vector` 컬럼 + `<=>`)로.

**이유**
- 규모보다 **프로덕션급 벡터검색을 제대로 세우는 학습·완성도**를 우선. (수천 chunk엔 앱단 코사인도 충분하지만,
  사용자가 실제 벡터 인프라를 목표로 함.)
- 벡터검색을 인터페이스(D0-B 범용 substrate) 뒤에 두어, 규모/이식 이슈 시 구현 교체 여지는 유지.

**끌려오는 하위 결정 — 테스트 전략 (사용자 확정)**
- pgvector는 H2에 `vector` 타입·`<=>`가 없어 H2 테스트에서 실행 불가. 그래서:
  - **CI/H2 테스트는 pgvector 쿼리를 실행하지 않는다.** 하이브리드/fallback **오케스트레이션 배선과 "기능 장애 없음"**만
    검증(벡터검색 인터페이스를 mock/in-memory stub로 대체). "CI는 기능 장애 정도만 체크".
  - **실제 pgvector 유사도 품질은 별도 벤치마크로 검증** — CI 아님, 수동/게이팅 러너(기존 `analysis/eval` 게이팅
    패턴과 동일한 결). 진짜 Postgres+pgvector 대상.

**완료기준 갱신(overview/plan의 "H2에서 전부 통과" 조항 대체)**
- ~~"외부 키 없이 전체 파이프라인·테스트가 H2에서 통과"~~ →
  **"CI(H2)는 배선·기능 장애 없음을 검증(벡터검색은 stub). pgvector 실경로는 별도 벤치마크(비-CI)로 검증."**
- (D3의 로컬 embedding은 여전히 외부 키 없이 CI에서 결정적으로 도는 배선 검증용으로 유효.)

**리스크/후속(S4)**
- pgvector 도입: Postgres에 `CREATE EXTENSION vector`, `vector` 컬럼 매핑(Hibernate 6.4+ `org.hibernate.vector`
  또는 `com.pgvector` 클라이언트 + 네이티브 쿼리), build.gradle 의존성. 스키마 마이그레이션 방식은 S4에서 확정.
- CI 스텁 경로 ≠ 프로덕션 pgvector 경로 → pgvector 쿼리 회귀는 벤치마크가 잡는다(의도된 트레이드오프).

**D4-1. pgvector 컬럼 × H2 DDL 충돌 해소 (구현 상세, S1을 가름)**
- 문제: `ddl-auto: update`라 `@Entity`의 테이블을 Hibernate가 자동 생성. `CodeChunk`에 `vector(N)` 컬럼을 두면
  **memory 테스트가 없어도** 다른 모든 H2 테스트 부팅 시 `create table ... vector(N)`이 H2에서 실패 → 전체 CI 붕괴.
  즉 "테스트를 안 짜는 것"으로는 못 피함(DDL 자동생성이 원인).
- **선택: 이식적 `real[]` 배열 컬럼 + 쿼리 시 vector 캐스팅**
  - embedding은 `float[]` → SQL ARRAY 컬럼으로 저장(H2·Postgres 둘 다 생성 성공, DDL에 pgvector 타입 미등장).
  - 유사도는 **벤치마크(비-CI) 네이티브 쿼리에서만** `embedding::vector <=> :q::vector`로 캐스팅해 진짜 pgvector
    코사인 연산(exact scan) 수행. H2 경로엔 pgvector가 문자 그대로 안 닿음 = "pgvector는 CI 미테스트" 실현.
  - 신규 Gradle 의존성 0, 신규 테스트 인프라 0.
  - **미루는 것**: HNSW/ivfflat 인덱스(성능 최적화)만 backlog. 정밀도 검증엔 exact scan이 오히려 정확 → 손해 없음.
    프로덕션 대규모 성능 필요 시 실제 `vector` 컬럼+인덱스로 승격(인터페이스로 격리).

---

## D5. semantic을 언제/어떻게 붙일까 (D0-A로 "항상 블렌딩"은 이미 배제)

**후보**
- (a) 키워드 fallback: 키워드+구조 결과가 빈약할 때(히트 수/최고점 임계 미만)만 `semanticSearch` 호출해 보강.
- (b) 항상 조회하되 별도 표시: 매번 semantic top-k를 뽑되 안 섞고 "의미상 유사" 섹션으로만.
- (c) 둘 다.

**선택: (a) 키워드 fallback (임계 미만 시에만 호출)**

**이유**
- D0-A 철학("필요할 때만 호출")과 정확히 일치.
- 핵심 근거는 **정밀도** — semantic을 항상 섞으면 키워드가 잘 맞는 케이스까지 흐릿한/노이즈 후보가 상위를
  오염시킴. "키워드가 헛발질할 때만 보강"이 정밀도를 지킴.
- 참고: pgvector(D4)로 오면서 "평소 임베딩 비용 절약"이라는 원래 부차 논거는 약해짐(쿼리 임베딩 1회+`<=>`는 쌈).
  그래도 정밀도 논거만으로 (a)가 유지됨.
- 임계값(무엇이 "빈약"인가)은 상수로 두고 튜닝은 backlog — 여기서 과설계 금지.

**영향**: S5 하이브리드에서 키워드 결과를 먼저 평가 → 임계 미만이면 semanticSearch로 후보 보강, 점수는
정규화 가중합으로 최소 결합. 임계 상수는 backlog 튜닝 대상.

---

## D6. 분석 파이프라인 연동 지점

**후보**
- (a) `CodeCandidateRanker`에 fallback으로만 연동.
- (b) `semanticSearch`를 범용 서비스로만 두고 분석 파이프라인 미변경(추후 MCP 툴 노출용).
- (c) 둘 다.

**선택: (c) 범용 서비스 구축 + `CodeCandidateRanker` fallback 연동**

**이유**
- (a)/(b)는 배타 선택이 아니라 **계층 관계** — 범용 `CodeMemorySearchService`(D0-B substrate)는 무조건 필요하고
  (#8이 재사용), fallback 연동(a)은 그 서비스 **위에 얹힌다.** "둘 다"가 스코프 중복이 아님.
- 실제 결정은 "이번에 ranker 연동까지 하느냐"인데, #7 완료기준이 *"분석 결과 후보에 벡터 기반 후보가 실제로
  반영된다"*라 **연동을 이번에 해야 목표 달성.** (b)만 하면 substrate는 서지만 #7 성과가 분석에 안 드러남.
- REST 신규 노출은 필요해질 때 후속(범위 밖).

**연동 지점(코드 확인 완료)**: `CodeCandidateRanker.rank()` 1단계(키워드 누적) 뒤, 키워드 후보가 임계 미만(D5)이면
`semanticSearch` 결과를 `SymbolAccumulator`에 보강 주입 → 기존 2·3단계(파일 그룹화/정렬)·흐름추적·draft 그대로 재사용.

---

## D7. Chunk·embedding 재생성 시점

**후보**
- (a) 스캔 시 전체 재생성: `scan()`이 파일/심볼 지우고 다시 만들 때 chunk/embedding도 함께.
- (b) 별도 트리거(`POST .../embeddings`): 스캔과 분리, 임베딩 비용 명시적 통제.

**선택: (a) 스캔 시 전체 재생성** (단, API 모드용 (b)는 backlog 옵션으로 열어둠)

**이유**
- 로컬 embedding(D3 기본)은 결정적·무비용이라 스캔 시 전체 재생성이 전혀 부담 없음 → (a)가 단순·일관.
- D3에서 **실제 API를 켜면** 스캔마다 전체 재임베딩 = 비용·지연 → 그땐 아픔. 그래서 **(b) 별도 트리거는
  "API를 실제로 켜는 시점"에 붙이는 backlog 옵션**으로만 둠(지금 미리 만들면 로컬 기본 모드엔 YAGNI).
- 증분(변경분만) 재임베딩은 이번 범위 밖(overview 제외 항목). 초기엔 전체 재생성 허용. 규모/비용이 실제
  문제가 되면 그때 증분으로.

**영향**: S2/S7에서 `CodeAnalysisService#scan`의 "지우고 다시 저장" 흐름에 chunk/embedding 재생성을 훅으로 얹음.
API 모드 비용 통제(b)는 D3 API 활성화 시 재검토 트리거.

---

## 결정 완료 — 다음 단계

D0-A/B, D1~D7 전부 확정. 이제 구현(S1~S7, `02-plan.md`) 착수 가능.
`04-result.md`는 구현 진행에 따라 작성한다.
