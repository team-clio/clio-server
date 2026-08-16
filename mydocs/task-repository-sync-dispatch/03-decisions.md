# Repository Sync Dispatch — 결정 기록

## D1. 디스패치 트리거 방식

- **결정**: A. `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`
- **이유**: `BugCollectedAgentDispatcher`와 동일 패턴을 재사용한다. 트랜잭션 커밋 전에는
  이벤트가 발행되지 않아 DB와 디스패치가 어긋나지 않고, 디스패치 실패가 원 트랜잭션을
  깨지 않는다. `@ConditionalOnProperty("clio.agent.enabled")`로 로컬 개발 시 무해하다.
- **배제한 대안**: 서비스 직접 호출(실패 시 트랜잭션 회수·재시도 경계 약함), Outbox(규모에 비해 과함).
