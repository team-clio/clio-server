# Repository Sync Dispatch — 구현 계획

## 구현 단계

### S1. `ClioAgentClient`에 repository 이벤트 전송 추가

- `dispatchRepositorySync(Long projectId, Long sourceId, RepositorySyncRequestType type)` 추가.
- `request_type`별 payload: `repository_id`=`sourceId` 문자열, `branch`=`target_branch`,
  `source_uri`=`repo_url`(추가/변경 시), `request_id`=결정 D4 형식.
- 엔드포인트: `/runs` 비동기(결정 D6). 기존 `processBug`와 동일한 body 구조.
- 테스트: `ClioAgentClientTest`에 body 스냅샷 케이스 2건(`repository_added`, `repository_removed`).

### S2. `RepositorySyncEvent` + `RepositorySyncDispatcher`

- `ax.clio.agent.event` 패키지. `BugCollectedAgentDispatcher`와 동일 패턴:
  `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@ConditionalOnProperty("clio.agent.enabled")`.
- 동작: 디스패치 성공 시 `sync_status=SYNCING`(결정 D3), 실패 시 `FAILED`로 갱신하고 로그.
- 테스트: Mockito 단위 테스트(성공/실패 경로).

### S3. `ProjectService` 이벤트 발행 + `ProjectSource` 상태 전이 메서드

- `createRepository` → `repository_added` 발행.
- `deleteRepository` → `repository_removed` 발행 (트랜잭션 커밋 후).
- `updateRepository` → Agent 발행 없음, `sync_status=PENDING` 복귀(결정 D2).
- `ProjectSource`에 `markSyncing()`, `markFailed()` 추가(또는 서비스 메서드로 처리).
- 테스트: `ProjectControllerTest` 계열 + 서비스 단위 테스트로 발행 검증.

### S4. 통합 테스트

- `BugCollectedDispatchIntegrationTest` 패턴: repository 생성/삭제 API 호출 후
  커밋 뒤 디스패치 검증(`@MockitoBean ClioAgentClient`).

### S5. 문서 마무리

- `04-result.md` 작성, 검증(`./gradlew test`) 후 커밋, PR(base=main).

## 결정 포인트

### D1. 디스패치 트리거 방식

- **A. `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`** — 기존
  `BugCollectedAgentDispatcher`와 동일. (추천)
- B. `ProjectService`에서 직접 `ClioAgentClient` 호출 — 단순하지만 실패 시 트랜잭션 회수/재시도 경계 약함.
- C. Outbox 패턴 — 가장 견고하지만 이번 규모에 과함.

### D2. `updateRepository` 시 Agent 호출 여부 (`repository_changed`)

- **A. 발행하지 않고 `sync_status=PENDING`으로 복귀** — `repository_changed`는
  `before_commit`(active commit)이 필수인데 Spring에 해당 컬럼이 없음(설계 문서 "추후 설계"). (추천)
- B. update를 `repository_removed`+`repository_added`로 대체 — 의미 왜곡·비용 큼.
- C. 이번 범위에 active commit 컬럼까지 추가 — 범위 확장, 후속으로 미룸.

### D3. `sync_status` 전이 범위

- **A. 디스패치 성공→`SYNCING`, 실패→`FAILED`** — Agent 완료 통지 수단이 아직 없으므로
  `SYNCED`는 후속 작업(단, UI에서 `SYNCING`에 머무는 동안의 표시는 "동기화 중"으로 자연스러움). (추천)
- B. 디스패치 직후 낙관적 `SYNCED` — 실제 완료와 다를 수 있어 부정확.
- C. 실패 시에만 `FAILED`, 성공 시 `PENDING` 유지 — 상태 변화가 없어 UI에 반영 안 됨.

### D4. `request_id` 형식

- **A. `repository-{projectId}-{sourceId}`** — 디버깅에 프로젝트 맥락 포함. (추천)
- B. `repository-{sourceId}` — 더 짧지만 projectId 없음.
- C. UUID — 추적성 낮음.

### D5. payload 식별자 매핑

- **A. `repository_id` = `project_sources.id`, `branch` = `target_branch`, `source_uri` = `repo_url`** —
  Agent `RepositorySyncPayload` 계약(`repository_id`/`branch`/`source_uri` 문자열) 그대로. (추천)
- B. provider/owner/name 조합 식별자 — Agent 계약과 불일치.

### D6. Agent 전송 방식

- **A. `/runs` 비동기(fire-and-forget)** — mirror clone이 오래 걸리므로 동기 대기 부적합.
  `processBug`와 동일. (추천)
- B. `/runs/wait` — 완료를 기다려 정확한 상태 기록 가능하지만 타임아웃 위험.

## 검증

- 신규 테스트: `ClioAgentClientTest` body 스냅샷, `RepositorySyncDispatcherTest`,
  repository 통합 테스트 1건.
- 전체: `./gradlew test` (H2, 기존 스위트와 함께).
