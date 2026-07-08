# Clio 진행 리포트

## 현재 기준

현재 개발 방향은 단순 RAG 서비스가 아니라 다음 구조를 기준으로 한다.

```text
Agentic Code Analysis
+ RAG Memory
+ Hybrid Retrieval
+ Evidence-based Scoring
+ Grounded LLM Report
```

핵심 전제:

- 버그 리포트는 개발자만 작성한다고 가정하지 않는다.
- 일반 사용자 언어, 운영자 문의, 개발자 추정 리포트가 모두 들어올 수 있다.
- 리포트 원문 검색도 비교 대상에 포함한다.
- 초기 전처리는 무거운 구조화가 아니라 하이브리드 검색 입력 정규화다.
- 코드 구조 탐색은 agentic search가 중심이다.
- 의미 연결과 과거 기억은 RAG가 담당한다.
- 리포트 구조화는 LLM으로만 수행한다.
- LLM 설정이 없으면 원문 검색만 수행한다.
- 사용자가 LLM 설정을 선택했는데 호출에 실패하면 분석 Job을 실패 처리한다.
- 점수 계산은 검색된 코드 근거 기반 휴리스틱으로 유지한다.
- MVP 단계에서는 Flyway를 끄고 `ddl-auto: update`로 빠르게 개발한다.
- DB 스키마가 안정화되면 Flyway migration을 다시 도입한다.

## 현재 구현 완료

- 프로젝트 등록
- `/workspace` 경로 제한
- Java 파일 스캔
- JavaParser 기반 심볼 추출
- 버그 리포트 등록
- 기본 코드 검색
- 비동기 분석 Job
- 중요도/난이도/위험도 점수화
- 기본 분석 결과 생성
- LLM Provider Config CRUD
- API key 로컬 저장 및 응답 마스킹
- OpenAI-compatible LLM client
- LLM 기반 검색 입력 생성
- 코드베이스 심볼 기반 도메인 후보를 LLM 선택지로 제공
- RAW_ONLY / PREPARED_ONLY / HYBRID 비교가 가능하도록 검색 입력 생성 모드 분리
- LLM 응답 schema 검증 강화
  - 필수 필드 누락 검증
  - 리스트 필드 타입 검증
  - enum 필드 허용값 검증
  - 빈 값/중복 검색어 정리
  - 잘못된 응답의 실패 사유 명확화
- MVP용 JPA `ddl-auto: update` 전환
- Copilot PR 리뷰 대응
  - LLM 설정 수정 시 null 필드가 기존 값을 덮어쓰지 않도록 수정
  - RAW_ONLY 분석 결과에서 LLM 전용 표현이 보이지 않도록 문구 수정
  - 코드베이스 도메인 후보 조회를 정렬하고 최대 80개로 제한
  - `ddl-auto` 값을 환경 변수로 덮어쓸 수 있게 변경
  - OpenAI-compatible HTTP client를 `RestClient.Builder` 주입 방식으로 변경
- input 구조화 단위 테스트 추가
  - LLM 응답 파싱
  - 코드베이스 후보 밖 `candidateDomains` 제거
  - RAW_ONLY / PREPARED_ONLY / HYBRID 검색 입력 생성
  - API key 마스킹
  - LLM 설정 수정 정책
  - 코드베이스 도메인 후보 정렬/제한
- 버그 리포트 기반 코드 후보 탐색 개선 (랭킹 파이프라인, PR #3 머지)
- 관련 코드 분석 흐름 추적 (Controller-Service-Repository, import 기반 의존 그래프)

## 현재 문서 상태

- `mydocs/analysis-pipeline-assumptions.md`
  - 최신 분석 파이프라인 전제 문서
  - 일반 사용자 리포트, 하이브리드 검색, agentic/RAG 역할 분리를 정리함

- `mydocs/remaining-roadmap.md`
  - 남은 개발 로드맵 문서
  - 일부 우선순위는 최신 판단 기준으로 다시 정리할 필요가 있음

- `mydocs/remaining-report.md`
  - 현재 진행 상태 추적 문서
  - 앞으로 작업을 진행할 때마다 이 문서를 갱신한다

- `mydocs/report-search-plan-schema.md`
  - 버그 리포트를 하이브리드 검색 입력으로 바꾸기 위한 문서
  - `candidateDomains`를 코드베이스 도메인 후보 기반으로 재정의함
  - 과한 필드인 `entryPointHints`, `flowHints`, `stateHints`, `testSearchHints`는 초기 스키마에서 제외함
  - 초기 검색 입력을 6개 필드 기준으로 축소함
  - 원문 검색과 전처리 검색을 비교할 수 있도록 `RAW_ONLY`, `PREPARED_ONLY`, `HYBRID` 모드를 명시함

- `mydocs/analysis-pipeline-assumptions.md`
  - 리포트 구조화 단계를 검색 입력 정규화 단계로 축소함
  - 초기 스키마 제외 필드와 충돌하던 설명을 정리함

## 다음 우선순위

### 1. 하이브리드 검색 입력 생성

현재 1차 구현은 완료됐다.

이유:

- 일반 사용자 언어를 코드 검색 가능한 입력으로 바꿔야 한다.
- 원문 검색과 전처리 검색을 비교할 수 있어야 한다.
- 검색 품질은 query 품질에 크게 의존한다.
- RAG를 붙여도 query가 나쁘면 검색 결과가 나쁘다.
- 개발자 리포트와 일반 사용자 리포트를 구분해야 검색 전략을 다르게 잡을 수 있다.

해야 할 일:

- 샘플 리포트와 실제 검색 결과 비교
- provider별 응답 포맷 흔들림 확인
- LLM Config CRUD 통합 테스트 추가
- 스키마 안정화 후 Flyway migration 재도입

초기 방식:

- 필드명과 enum은 영어로 설계
- 설명과 문서는 한글로 작성
- LLM으로 검색 입력을 생성한다.
- rule-based 구조화 fallback은 사용하지 않는다.

나중 방식:

- LLM 기반 검색어 확장
- strict JSON schema
- provider별 native adapter

### 2. 버그 리포트 기반 코드 후보 탐색

리포트 원문과 LLM 검색 입력을 기반으로 실제 코드 후보를 찾는 단계다.

이 단계의 목표는 evidence 저장이 아니라, 먼저 어떤 코드가 관련 후보인지 더 정확히 고르는 것이다.

해야 할 일:

- RAW_REPORT / CANDIDATE_DOMAIN / KEYWORD 입력별 검색 결과 수집
- 검색 입력 타입별 가중치 적용
- 여러 입력에서 반복 등장한 파일/심볼 가산점 적용
- 중복 결과 제거 기준 개선
- 관련 코드 후보와 관련 테스트 후보 분리
- 최종 relatedCode 생성 기준 개선

아직 하지 않을 일:

- DB evidence 저장
- vector search
- LangGraph
- LLM 리포트 생성

### 3. Code Chunk / Vector Search

RAG를 붙이기 위한 기반이다.

해야 할 일:

- 코드 chunk 생성
- chunk 저장
- embedding 생성
- vector search 구현
- chunk 품질 확인

### 4. 관련 코드 분석 흐름 추적

검색된 후보 주변의 실제 코드 흐름을 확장한다.

해야 할 일:

- Controller-Service-Repository 흐름 추적
- Entity 연결
- 관련 테스트 탐색
- import/constructor/field 기반 의존 관계 추적

### 5. Hybrid Rerank

여러 검색 결과를 합쳐 최종 근거 후보를 고른다.

해야 할 일:

- keyword score
- symbol score
- vector score
- flow score
- domain score
- test score
- 중복 제거

### 6. LLM 리포트 생성

검색과 rerank 이후에 붙인다.

해야 할 일:

- 근거 코드만 context로 전달
- 분석 요약 생성
- 수정 방향 추천
- 테스트 전략 추천
- 근거 없는 파일/클래스 언급 방지

### 7. 벤치마크

RAG/LLM 적용 후 비교 실험을 수행한다.

비교 대상:

- keyword only
- raw report only
- prepared query + keyword
- symbol search
- vector search
- hybrid search
- hybrid + agentic expansion
- hybrid + memory
- hybrid + LLM report

측정 항목:

- 정답 파일 Top-K 포함 여부
- 정답 메서드 Top-K 포함 여부
- 관련 없는 코드 노이즈 비율
- 설명과 근거 코드 일치 여부
- hallucination 여부
- 점수 판단 납득 가능성
- 응답 시간

## 현재 진행 상태

```text
완료: 백엔드 MVP
완료: 분석 파이프라인 전제 문서화
완료: 검색 입력 정규화 방향 정리
완료: LLM Provider Config CRUD 구현
완료: LLM 기반 검색 입력 생성 구현
완료: RAW_ONLY / PREPARED_ONLY / HYBRID 분석 실행 옵션 추가
완료: Copilot PR 리뷰 대응 및 테스트 통과
완료: LLM 검색 입력 schema 검증 강화
완료: 버그 리포트 기반 코드 후보 탐색 개선
완료: 관련 코드 분석 흐름 추적
다음: Code Chunk / Vector Search
대기: Hybrid Rerank
대기: LLM 리포트 생성
대기: 벤치마크
```
