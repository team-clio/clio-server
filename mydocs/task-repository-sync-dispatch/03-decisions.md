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

## D3. `sync_status` 전이 범위

- **결정**: A. 디스패치 성공 시 `SYNCING`, 실패 시 `FAILED`. `SYNCED`는 후속 작업으로 미룬다.
- **이유**: `SYNCED`는 Agent가 mirror/PCM 작업을 끝냈을 때만 정확한데, 현재 repository sync
  그래프는 workflow-run 완료 통지를 Spring에 보내지 않는다(검토 시 확인된 갭). 전송 성공까지가
  이번 범위에서 Spring이 보장할 수 있는 경계다. `last_synced_at`은 `SYNCED` 전이 시 채우므로
  이번 범위에서는 미기입으로 둔다.
- **배제한 대안**: 낙관적 `SYNCED`(실제 완료와 다를 수 있음), 실패만 기록(발행 여부를 UI에서
  알 수 없음).

## D4. `request_id` 형식

- **결정**: A. `repository-{projectId}-{sourceId}` (예: `repository-7-42`)
- **이유**: 프로젝트 경계에서도 유일하고 로그·에러 메시지에서 식별이 쉽다. 기존 `process-bug-{id}`
  패턴과 일관된다.
- **배제한 대안**: `repository-{sourceId}`(projectId 없음 → 프로젝트 경계 충돌 가능), UUID(추적성 낮음).

## D5. payload 식별자 매핑

- **결정**: A. `repository_id`=`project_sources.id`, `branch`=`target_branch`,
  `source_uri`=`repo_url` (모두 문자열 직렬화)
- **이유**: Agent `RepositorySyncPayload` 계약이 `repository_id`/`branch`/`source_uri`를 요구하므로
  그대로 매핑하면 에이전트 수정 없이 동작한다. project_id는 request_id·요청 공통 필드로 전달된다.
- **배제한 대안**: provider/owner/name 조합 식별자(Agent 계약과 불일치 → 에이전트 수정 필요).
