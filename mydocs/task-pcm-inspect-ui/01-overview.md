# PCM 메모리 inspect 화면

## 1. 작업 배경

PCM(Project Context Memory)은 clio-agent-graph가 문서·저장소·해결 이력에서 추출한
Knowledge를 프로젝트 단위로 관리하는 메모리다. 이 Knowledge는 Issue 분석과 버그 매칭에
재사용되는데, 현재는 그 내용을 볼 수 있는 화면이 없다.

- **소유권**: PCM은 Python 에이전트의 자체 PostgreSQL(`pcm_*` 테이블)과 Markdown store에
  저장된다. Spring JPA 스키마에 없으며, Spring은 PCM의 내용을 알지 못한다.
- **읽기 경로 없음**: PCM을 읽는 HTTP API가 없다. 에이전트 서버(:2024)는 LangGraph의
  `/runs`, `/runs/wait`만 노출하고, `ProjectContextReader` Protocol은 그래프 내부
  주입용이다.
- **UI 접근 경로**: clio-admin은 vite proxy로 Spring(:8080)만 바라본다. 에이전트
  서버로의 proxy 경로가 없어 PCM 데이터에 접근할 수 없다.

## 2. 현재 상태와 문제

| 계층 | 현재 상태 |
|---|---|
| PCM 데이터 | 에이전트 자체 PostgreSQL(`pcm_knowledge`, `pcm_knowledge_revisions`, `pcm_commits`, `pcm_source_events`, `pcm_knowledge_chunks` 등) + Markdown store |
| PCM 읽기 계약 | `ProjectContextReader` Protocol(에이전트 내부) — `resolve_snapshot` / `search_knowledge` / `read_knowledge` / `trace_knowledge_sources` |
| 에이전트 HTTP | `/runs`, `/runs/wait` (LangGraph). PCM 전용 엔드포인트 없음 |
| clio-admin | Spring 프록시만 존재(`/api`, `/external-api`, `/internal-api` → :8080) |
| Spring | PCM을 모름. `ClioAgentClient`는 `clio_agent` 루트 그래프에만 요청 |

**문제**: PCM Knowledge가 어디에 쌓이고 어떤 내용인지 개발자가 확인할 수 있는 통로가 없다.
LLM이 잘못된 Knowledge를 기록해도 눈으로 검증할 수 없고, PCM revision이 왜 증가하는지도
추적할 수 없다.

## 3. 개선 방향

에이전트 서버에 PCM 읽기 전용 inspect API를 추가하고, clio-admin에 PCM inspect 화면을
만든다. 데이터 소유권 원칙(Spring은 PCM을 모름)을 유지하기 위해 조회 API는 에이전트
서버에 두는 것을 기본으로 한다.

```text
clio-admin (PCM inspect 화면)
  → 에이전트 서버 read API (:2024)
  → PostgresPCM / MarkdownStore
```

경로에 대한 구체적인 선택지는 plan의 결정 포인트(D1)에 정리한다.

## 4. 범위

### 포함 후보

- 에이전트 서버에 PCM 읽기 전용 HTTP 엔드포인트
  - 프로젝트별 PCM snapshot 조회(`pcm_revision`, `knowledge_index_revision`)
  - Knowledge 목록 조회(knowledge_id, logical_key, type, title, revision, 유효 범위)
  - Knowledge 상세 조회(body_markdown, sources, related_knowledge_ids)
  - 검색(`search_knowledge` 재사용 가능성)
- clio-admin PCM inspect 페이지
  - 사이드바 메뉴 추가(개발자 도구 섹션)
  - 프로젝트 선택 연동
  - Knowledge 목록·상세·revision 표시

### 제외 후보

- PCM 쓰기(Knowledge 생성·수정·tombstone)
- Spring이 PCM DB를 직접 조회
- 인증·권한(현재 admin 화면과 동일 수준)
- 저장소·문서 원본 보기(Markdown store 원문, Git commit diff 등)

## 5. 확인된 사실(조사 결과)

- `pcm_knowledge`(logical_key), `pcm_knowledge_revisions`(type·title·sources·유효 범위·
  is_tombstone), `pcm_projects`(active_pcm_revision·knowledge_index_revision),
  `pcm_commits`(base/target revision·change_summary), `pcm_source_events`(멱등성)가
  존재한다.
- `PostgresPCM.read_knowledge`는 snapshot 시점에 유효한 Knowledge를 반환하고, tombstone을
  KnowledgeNotFound로 처리한다.
- 에이전트 서버는 `langgraph dev`로 실행되며 커스텀 라우터를 추가하는 방법은 별도 확인이
  필요하다. (FastAPI 서버에 라우터를 붙이는 방식을 검토)
- clio-admin은 `src/api/`에 도메인별 모듈 + `hooks.ts`(React Query) 패턴을 쓴다.
- 사이드바는 `src/components/layout/Sidebar.tsx`의 navSections으로 구성되며 페이지 전환은
  `App.tsx`의 Page union으로 관리한다.

## 6. 완료 기준 초안

- 에이전트 서버에서 PCM snapshot·Knowledge 목록·상세를 읽을 수 있는 read API가 동작한다.
- clio-admin에서 프로젝트를 선택하면 해당 프로젝트의 PCM Knowledge를 볼 수 있다.
- Knowledge 상세에서 본문, source, revision, tombstone 상태를 확인할 수 있다.
- API 계약·Pydantic 모델·UI 타입이 일치하고, 관련 테스트가 통과한다.

## 7. Plan에서 결정할 핵심 항목

1. **PCM 조회 API 위치**: 에이전트 서버 직접 / Spring 경유
2. **API 계약 범위**: snapshot + 목록 + 상세 + 검색 포함 여부
3. **에이전트 서버 구현 방식**: FastAPI 라우터 추가 / 별도 서버 / LangGraph 확장
4. **UI 구조**: 페이지 배치, 목록·상세 분리, 검색 UI 포함 여부
5. **project_id 매핑**: Spring 프로젝트 ID ↔ PCM project_id 매핑 방식
6. **구현 순서**: API 먼저 / 화면 먼저 / 함께
