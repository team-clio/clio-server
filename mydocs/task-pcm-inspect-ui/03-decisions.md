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
