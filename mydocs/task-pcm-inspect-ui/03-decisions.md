# PCM 메모리 inspect 화면 — 결정 기록

## D1. PCM 조회 API 노출 위치

- **결정**: Spring에 중계(relay) API를 구현하고, 에이전트 서버에 PCM read API를 추가한다.
- **흐름**: `clio-admin → Spring 중계 API (:8080) → 에이전트 PCM read API (:2024) → PostgresPCM`
- **이유**:
  - admin은 기존처럼 vite proxy를 통해 Spring(:8080)만 바라봐도 된다. 단일 origin 유지.
  - Spring은 PCM 데이터를 해석하지 않고 요청·응답을 그대로 중계하므로, "Spring이 PCM을 모른다"는
    소유권 원칙을 지킨다. PCM 데이터를 읽는 구현은 여전히 에이전트 소유다.
  - `ClioAgentClient`가 이미 에이전트 URL(:2024)을 알고 있으므로 중계에 필요한 인프라가 있다.
- **제외한 대안**: admin이 에이전트 서버를 직접 호출(프록시 경로 추가) — admin이 두 origin을
  바라보게 되어 기존 패턴에서 벗어남.

## D2. 에이전트 PCM read API 구현 방식

- **결정**: standalone FastAPI 앱(`inspect_api.py`)을 추가하고 uvicorn으로 실행한다.
- **이유**:
  - LangGraph dev 서버(:2024)의 `/runs`·`/runs/wait` 구조를 건드리지 않는다.
  - `PostgresPCM`을 직접 주입해 기존 읽기 로직(`read_knowledge`, `search_knowledge`)을
    재사용한다.
  - `langgraph build` 배포 이미지와 독립적으로 실행 가능하다.
- **제외한 대안**: LangGraph 서버에 커스텀 라우터 추가 — 공식 지원이 제한적이고 배포
  이미지에도 별도 구성이 필요해 리스크가 크다.

## D3. PCM API 1차 범위

- **결정**: snapshot 조회 + Knowledge 목록 + Knowledge 상세를 1차 범위로 한다.
  - `GET /pcm/projects/{project_id}/snapshot` — active revision + index revision
  - `GET /pcm/projects/{project_id}/knowledge` — Knowledge 목록(현재 유효분)
  - `GET /pcm/projects/{project_id}/knowledge/{knowledge_id}` — 상세(body, sources, revision)
- **이유**:
  - inspect 화면의 핵심(무엇이 저장되어 있는가)을 최소 범위로 충족한다.
  - 검색은 `search_knowledge`가 Ollama embedding 의존이라 inspect 화면과 결이 다르고,
    구현·테스트 범위가 커진다. tombstone·검색은 2차로 미룬다.
