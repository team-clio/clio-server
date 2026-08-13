# Agent Graph 연동 리팩터링 계획

## 반영 순서

1. Bug 원문을 `bugs`로 통합하고 grouping 계층을 제거한다.
2. `agent_workflow_runs`를 추가해 모든 root 요청에 공통 lifecycle을 적용한다.
3. match decision과 analysis result를 workflow run에 연결한다.
4. Spring internal API를 원문 조회와 결과 적용으로만 제한한다.
5. Agent Retrieval에서 Spring 테이블 직접 조인과 FK를 제거한다.
6. Agent root가 PENDING, RUNNING, COMPLETED, FAILED 전이를 요청하게 한다.
7. 기존 데이터를 보존하는 Flyway/Alembic migration과 통합 테스트를 실행한다.

## 검증 기준

- 수집 오류 한 건이 Bug 한 행으로 생성된다.
- 여러 Bug를 Issue 하나에 연결할 수 있고 Bug는 최대 한 Issue에 속한다.
- workflow `request_id` 재시도는 멱등하며 다른 payload 재사용은 충돌한다.
- match decision과 analysis result는 workflow별 하나만 저장된다.
- Agent Retrieval DB는 Spring 도메인 테이블 없이 검색할 수 있다.
- `NEEDS_REVIEW`와 `INSUFFICIENT_EVIDENCE`는 결과 저장 후 workflow가 완료된다.
- 중간 기술 오류는 workflow가 실패한다.
