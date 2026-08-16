# 코드 근거 IDE 화면 개편 Result

## 완료 범위

- Admin 코드 근거 탭을 파일 트리와 읽기 전용 코드 뷰로 변경했다.
- Spring 외부 API가 최신 분석의 구조화된 citation만 확인하고 Agent Graph에 코드 발췌를 요청한다.
- Agent Graph는 고정 repository commit에서 citation 주변 최대 400줄을 읽고, 파일별 citation·설명을 반환한다.
- 코드 citation은 repository ID, commit, 파일 경로, 시작·끝 줄이 없으면 quality gate에서 코드 근거로 제외한다.
- 기존 immutable 분석 결과는 구조화 위치가 없으면 IDE 보기 불가 안내와 기존 카드 표시로 처리한다.

## 검증

- `clio-server`: `./gradlew test` 통과.
- `clio-admin`: `npm run typecheck`, `npm run lint`, `npm run build` 통과.
- E2E: 변경본 Spring을 `SERVER_PORT=8081 ./gradlew bootRun`으로 실행하고 `GET /external-api/v1/projects/1/issues/1/analysis-results/latest/code-evidence`를 호출했다. 응답은 `{"available":false,"files":[]}`였다.

## E2E 해석과 남은 과제

로컬의 기존 분석 결과에는 새 구조화 위치 필드가 없어 D4 정책대로 IDE 파일을 제공하지 않았다. 새 계약으로 분석을 다시 실행한 이슈가 있어야 파일 트리·발췌·하이라이트의 데이터 포함 E2E를 완료할 수 있다. Agent Graph 테스트 의존성(`pytest`, `ruff`)은 현재 실행 환경에 설치되어 있지 않아 실행하지 못했고 Python 구문 컴파일로 확인했다.
