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
- E2E: 독립 Project 3을 만들고 sandbox-order-service를 commit `d0221f4fe44bde7d5b390cccc50809ec0e11f21a`로 동기화했다. External Bug API로 pagination 결함을 수집해 `process_report`가 Issue 11과 `COMPLETED` 분석을 생성하는 것을 확인했다.
- E2E: `GET /external-api/v1/projects/3/issues/11/analysis-results/latest/code-evidence`가 `pagination_service.py`와 `test_order_service.py`의 고정 commit 발췌, 줄 범위, AI 관찰을 반환했다.
- UI E2E: Orca 내장 브라우저에서 Project 3의 Issue 11을 열고 `코드 근거` 탭을 확인했다. 파일 트리의 두 파일을 각각 선택해 commit, 줄 번호, 코드 발췌와 AI 설명이 전환되어 표시되는 것을 확인했다.
- UI 보완: 코드 근거 탭을 `max-w-6xl` 안의 2열 작업 영역으로 조정했다. 최소 높이는 34rem으로 제한하고, 기존 화면과 같은 흰 배경·slate 경계·clio 강조색을 사용한다.
- UI 재검증: Orca 내장 브라우저에서 작업 영역이 1,088px × 544px로 렌더링되고, 두 파일을 전환할 때 코드와 AI 주석이 갱신되는 것을 확인했다.

## E2E 해석과 남은 과제

기존 Project 1의 분석은 구조화 위치 필드가 없어 D4 정책대로 IDE 파일을 제공하지 않았다. 새 E2E 분석은 데이터 포함 API와 Admin 화면 경로를 통과했다. Agent Graph 테스트 의존성(`pytest`, `ruff`)은 현재 실행 환경에 설치되어 있지 않아 실행하지 못했고 Python 구문 컴파일로 확인했다.
