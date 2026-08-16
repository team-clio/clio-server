# Repository Sync Dispatch — 결정 기록

## D1. 디스패치 트리거 방식

- **결정**: A. `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`
- **이유**: `BugCollectedAgentDispatcher`와 동일 패턴을 재사용한다. 트랜잭션 커밋 전에는
  이벤트가 발행되지 않아 DB와 디스패치가 어긋나지 않고, 디스패치 실패가 원 트랜잭션을
  깨지 않는다. `@ConditionalOnProperty("clio.agent.enabled")`로 로컬 개발 시 무해하다.
- **배제한 대안**: 서비스 직접 호출(실패 시 트랜잭션 회수·재시도 경계 약함), Outbox(규모에 비해 과함).

## D2. `updateRepository` 시 Agent 호출 여부

- **결정**: A. `repository_changed`를 발행하지 않고 `sync_status`를 `PENDING`으로 복귀시킨다.
- **이유**: `repository_changed`는 Agent 계약상 `before_commit`(현재 active commit)이 필수인데
  `project_sources`에 해당 컬럼이 없고 설계 문서도 "추후 설계"로 명시했다. update로 branch/URL이
  바뀌면 기존 mirror는 stale하므로 재동기화 필요를 `PENDING`으로 표현하고, 정확한 동기화는
  후속 작업(active commit 컬럼)에서 `repository_changed`로 처리한다.
- **배제한 대안**: remove+add 재등록(이벤트 두 번·재클론 비용), 이번 범위에서 컬럼 추가(범위 확장).
