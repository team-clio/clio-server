# 프로젝트 삭제 기능 결과

## 완료 범위

- `DELETE /api/v1/projects/{projectId}`를 추가했다.
- Spring 소유 프로젝트 데이터와 Agent 소유 PCM·Git 저장소 데이터를 삭제한다.
- Agent 정리가 실패하면 Spring 데이터는 삭제하지 않는다.
- 프로젝트 설정에서 이름 입력 재확인 후 삭제할 수 있다.
- 성공 시 선택 프로젝트를 비우고 리포트 화면으로 이동한다.

## 검증

- Server: `./gradlew test --tests 'ax.clio.project.controller.ProjectControllerTest'` 통과.
- Admin: `npm run typecheck`, `npm run lint`, `npm run build` 통과.
- Agent: root graph import 통과. 선택 실행한 `tests/test_graph.py`에는 기존 분석 결과가 `needs_review`가 되는 테스트 1건이 있어 실패했다. 프로젝트 삭제 경로의 직접 회귀 테스트는 후속 보강이 필요하다.

## 남은 과제

- Agent 프로젝트 삭제 node의 InMemory/PostgreSQL PCM 및 Git 저장소 회귀 테스트를 추가한다.
- 실제 Agent 연결 환경에서 삭제 실패 시 UI 오류와 Spring 데이터 보존을 E2E로 확인한다.
