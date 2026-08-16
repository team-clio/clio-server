# PCM 메모리 inspect 화면 — 구현 계획

## 1. 구현 단계

### 1단계. 에이전트 PCM 읽기 API (clio-agent-graph)

- `context/pcm/inspect_api.py` 추가: PostgresPCM을 재사용하는 읽기 전용 FastAPI 앱
  - `GET /pcm/projects/{project_id}/snapshot` — active revision + index revision
  - `GET /pcm/projects/{project_id}/knowledge` — Knowledge 목록(현재 유효 + tombstone 포함 여부 옵션)
  - `GET /pcm/projects/{project_id}/knowledge/{knowledge_id}` — 상세(body_markdown, sources, revision, 유효 범위)
  - `GET /pcm/projects/{project_id}/search?q=...` — `search_knowledge` 재사용(검색 포함 시)
- Pydantic 응답 모델: `models.py`의 기존 도메인 모델을 재사용하되 inspect용 projection을 추가
- 실행 진입점: `uvicorn` 명령(스크립트 또는 README에 기록), 개발 시 `langgraph dev`와 병행
- 테스트: `tests/pcm/test_inspect_api.py` — Fake/InMemory PCM으로 동작 검증

### 2단계. clio-admin PCM inspect 페이지

- `src/api/pcm.ts` 추가: `request<T>` 기반 PCM API 클라이언트
- `src/api/hooks.ts`에 `usePcmSnapshot`, `usePcmKnowledge`, `usePcmKnowledgeDetail`, `usePcmSearch` 추가
- `src/pages/PcmInspectPage.tsx` 추가
  - 프로젝트 선택(Sidebar 연동) → snapshot 요약
  - Knowledge 목록(테이블): logical_key, type, title, revision, 유효 범위
  - Knowledge 상세(패널 또는 별도 뷰): 본문 markdown, sources, related ids
  - 검색 UI(포함 시)
- `src/components/layout/Sidebar.tsx` + `App.tsx`에 페이지 등록(개발자 도구 섹션)
- vite proxy에 PCM API 경로 추가

### 3단계. 검증 및 문서

- `npm run typecheck`, `npm run lint`, `npm run build`
- 에이전트: `ruff check .`, `ruff format --check .`, `pytest`
- `API_UI_CHANGES.md` 갱신(새 API 클라이언트 기록)
- `04-result.md` 작성

## 2. 결정 포인트

### D1. PCM 조회 API 노출 위치

| 대안 | 설명 | 장단점 |
|---|---|---|
| A. 에이전트 서버 직접 (추천) | PCM read API를 clio-agent-graph에 두고 admin이 직접 호출 | 데이터 소유권 원칙에 부합. Spring 불필요. admin의 vite proxy가 에이전트(:2024 또는 별도 포트)를 바라봄 |
| B. Spring 경유 프록시 | Spring에 PCM 조회 컨트롤러를 두고 에이전트로 중계 | admin이 기존처럼 :8080만 바라봄. 하지만 Spring은 PCM을 모르는 구조라 중간 계층만 추가되고 소유권 경계가 흐려짐 |

**추천: A.** PCM 데이터를 읽는 책임은 데이터를 소유한 에이전트에 두는 게 `api-boundaries-and-lifecycle.md`의
"PCM은 Spring JPA 스키마에 만들지 않는다" 원칙과 일관된다.

### D2. 에이전트 서버 구현 방식

| 대안 | 설명 | 장단점 |
|---|---|---|
| A. 별도 FastAPI 앱 (추천) | `inspect_api.py`를 standalone FastAPI 앱으로 추가, uvicorn으로 실행 | LangGraph dev 서버 구조를 건드리지 않음. PostgresPCM을 직접 주입해 기존 읽기 로직 재사용 |
| B. LangGraph 서버 라우터 추가 | `langgraph dev`의 FastAPI 앱에 라우터를 붙임 | LangGraph 커스텀 라우터 확장은 공식 지원이 제한적. `langgraph build` 이미지에도 별도 구성 필요 |

**추천: A.** 배포(`langgraph build`)와 로컬 개발(`langgraph dev`) 양쪽에서 독립적으로 실행 가능하다.

### D3. API 계약 범위

| 항목 | 포함 여부 후보 |
|---|---|
| snapshot 조회 (revision, index revision) | 필수 |
| Knowledge 목록 (현재 유효) | 필수 |
| Knowledge 상세 (본문·sources·revision) | 필수 |
| tombstone 포함 조회 옵션 | 선택 (검토 대상) |
| Knowledge 검색 (`search_knowledge` 재사용) | 선택 (검토 대상) |
| revision 히스토리 (과거 revision 보기) | 선택 (추후 확장) |

**추천:** snapshot + 목록 + 상세를 1차 범위로 하고, tombstone·검색은 2차로 미룬다.
검색은 `search_knowledge`가 vector/embedding(Ollama) 의존이라 inspect 화면과 결이 다르다.

### D4. UI 구조

| 대안 | 설명 |
|---|---|
| A. 목록 + 상세 패널 (추천) | 페이지에서 왼쪽 Knowledge 목록, 오른쪽 상세. 단일 페이지 |
| B. 목록 → 상세 이동 | 상세를 별도 하위 뷰/모달로 분리 |
| C. 탭 구조 | snapshot 요약 / 목록 / 검색을 탭으로 분리 |

**추천: A.** 데이터 규모가 작아 단일 페이지의 목록+상세 패널이 충분하다.

### D5. project_id 매핑

Spring `project.id`는 숫자(Long), PCM `project_id`는 문자열. `ClioAgentClient`가
`projectId.toString()`으로 직렬화하므로 PCM project_id는 **Spring project.id의 문자열 표현**이다.

| 대안 | 설명 |
|---|---|
| A. `String(projectId)` 그대로 사용 (추천) | 기존 디스패치와 동일한 매핑. 변환 로직 불필요 |
| B. 별도 프로젝트 키 관리 | Spring에 PCM project_id 칼럼 추가 — Spring이 PCM을 알게 되어 소유권 경계 위반 |

**추천: A.**

### D6. 구현 순서

| 대안 | 설명 |
|---|---|
| A. 에이전트 API 먼저 (추천) | API 계약 확정 후 화면. 계약 위반을 줄임 |
| B. 화면 먼저 | 목업 기반으로 화면을 만들고 API는 이후 |
| C. 함께 | 병렬 진행 |

**추천: A.** API 응답 모델이 화면 타입의 기준이 되므로 API를 먼저 확정한다.

## 3. 범위 밖 (이번 작업에서 하지 않음)

- PCM 쓰기(Knowledge 생성·수정·tombstone)
- Spring이 PCM DB 직접 조회
- 인증·권한 (현재 admin 수준과 동일)
- revision 히스토리 UI, 검색 UI (D3/D4에서 포함 시 제외 없음)
