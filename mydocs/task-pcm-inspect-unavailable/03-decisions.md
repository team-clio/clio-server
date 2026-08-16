# PCM inspect 미가용 오류 처리 — 결정 기록

## D1. 서버 오류 응답 형태

- 결정: 503 `PCM_INSPECT_UNAVAILABLE` + 명확한 메시지.
- 근거: inspect 서버가 꺼져 있거나 재시작 중인 일시적 상태를 5xx가 아닌
  의도된 unavailable로 표현해야 admin이 재시도 가능성을 구분할 수 있다.

## D2. admin 오류 UI 형태

- 결정: 오류 배너 + 재시도 버튼.
- 근거: inspect 서버를 시작한 뒤 화면을 새로고침하지 않고 바로 재시도하는 흐름이
  개발 편의상 가장 자연스럽다.

## D3. clio-agent-graph 실행 편의 방식

- 결정: Makefile + `scripts/dev.sh`.
- 근거: inspect 서버와 `langgraph dev`를 별도 터미널에서 관리하는 번거로움을 줄이고,
  종료 시 inspect 프로세스를 정리해 포트 점유 문제를 막는다.

## D4. 검증 범위

- 결정: 각 저장소 전체 검증.
- 근거: 세 저장소를 함께 변경하므로 각 저장소의 표준 검증 명령을 실행한다.
