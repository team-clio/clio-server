# PCM inspect 미가용 오류 처리 — 구현 계획

## 1. 단계

1. overview 작성·확정·커밋
2. plan 작성·확정(이 문서)
3. 결정 기록 작성·커밋
4. clio-server 503 매핑 구현·테스트·커밋
5. clio-admin 오류 UI 구현·검증·커밋
6. clio-agent-graph 실행 편의 구현·검증·커밋
7. result 작성·커밋

## 2. 결정 포인트

### D1. 서버 오류 응답 형태

- 선택지: 500 그대로 / 503 `PCM_INSPECT_UNAVAILABLE` / 502 Bad Gateway
- 추천: 503. inspect 서비스가 일시적으로 미가용인 상태를 정확히 표현한다.

### D2. admin 오류 UI 형태

- 선택지: 오류 텍스트만 / 오류 배너 + 재시도 버튼
- 추천: 오류 배너 + 재시도. inspect 서버를 켠 뒤 바로 재시도할 수 있다.

### D3. clio-agent-graph 실행 편의 방식

- 선택지: README 명령만 / Makefile / Makefile + dev script
- 추천: Makefile + dev script. inspect 서버 백그라운드 실행, 종료 정리,
  `langgraph dev` 실행을 한 명령으로 묶는다.

### D4. 검증 범위

- 선택지: 변경 파일만 / 각 저장소 전체 검증
- 추천: clio-server `./gradlew test`, clio-admin `npm run typecheck && npm run lint &&
  npm run build`, clio-agent-graph ruff + pytest

## 3. 구현 계획(결정 후)

### clio-server

- `ax.clio.common.PcmInspectUnavailableException` 추가
- `GlobalExceptionHandler`에 503 매핑 추가
- `PcmInspectClient`에서 5xx·연결 실패를 변환하고 404 매핑 유지
- `PcmInspectClientTest`에 503 변환 테스트 추가

### clio-admin

- `PcmInspectPage`에서 snapshot·knowledge query error를 수집
- 오류 배너(AlertCircle + 메시지 + 재시도 버튼) 렌더링
- 빈 상태는 오류가 아닐 때만 표시

### clio-agent-graph

- `Makefile`에 `dev`·`inspect`·`infra` 타깃 추가
- `scripts/dev.sh`: `.env` 로드 → inspect 서버 백그라운드 시작 → `langgraph dev`
- README 로컬 실행 섹션에 `make dev` 안내 추가

## 4. 커밋 계획

- `docs: PCM inspect 미가용 오류 처리 overview` / `docs: ... 구현 계획` /
  `docs: ... 결정 기록`
- clio-server: `feat(pcm): map inspect unavailability to 503`
- clio-admin: `fix(pcm): show inspect errors with retry`
- clio-agent-graph: `chore: add agent dev convenience make targets`
- `docs: PCM inspect 미가용 오류 처리 result`
