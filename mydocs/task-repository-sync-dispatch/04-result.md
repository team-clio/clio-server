# Repository Sync Dispatch — 결과

## 완료 범위

Spring이 repository 등록·삭제 이벤트를 Agent로 비동기 발행하고, `sync_status`를 추적한다.

| 항목 | 내용 |
|---|---|
| `ClioAgentClient.dispatchRepositorySync` | `repository_added`/`repository_removed`를 `/runs`로 비동기 전송. `request_id`=`repository-{projectId}-{sourceId}`(D4), payload는 `repository_id`/`branch`/`source_uri` 매핑(D5) |
| `RepositorySyncEvent` + `RepositorySyncAgentDispatcher` | `@TransactionalEventListener(AFTER_COMMIT)` + `clio.agent.enabled` 조건(D1). 성공 시 `SYNCING`, 실패 시 `FAILED`(D3). `repository_removed`는 상태 전이 생략(F1) |
| `ProjectService` | create→`repository_added` 발행, delete→`repository_removed` 발행, update→`PENDING` 복귀(D2, `repository_changed` 미발행) |
| `ProjectSource` | `markSyncing()`/`markFailed()`/`markPending()` 추가 |
| 트랜잭션 처리 | AFTER_COMMIT 내부 쓰기 유실 문제를 `REQUIRES_NEW`로 해결(F2) |

## 결정 요약

- D1 AFTER_COMMIT 이벤트 리스너 / D2 update는 발행 안 함 + PENDING 복귀
- D3 성공→SYNCING, 실패→FAILED (`SYNCED`는 후속)
- D4 `repository-{projectId}-{sourceId}` / D5 source id·target_branch·repo_url / D6 `/runs` 비동기
- F1 removed는 상태 전이 생략 / F2 REQUIRES_NEW

## 검증 결과

- `./gradlew test` 전체 통과 (`GRADLE_USER_HOME=/tmp/clio-gradle-home`, `--no-daemon`).
- 신규 테스트:
  - `ClioAgentClientTest` — `repository_added`/`repository_removed` body 스냅샷 2건
  - `RepositorySyncAgentDispatcherTest` — 성공·실패·removed 경로 3건
  - `RepositorySyncDispatchIntegrationTest` — 커밋 후 디스패치 + `SYNCING` 반영, removed 디스패치 2건
- F2 재현 과정: AFTER_COMMIT 내부 `REQUIRED` 쓰기가 유실되는 것을 통합 테스트로 확인 후
  `REQUIRES_NEW` 적용으로 해결.

## 남은 과제 (후속)

1. `repository_changed`(update) — `project_sources`에 active commit 컬럼 추가 후 발행
   (설계 문서가 "추후 설계"로 명시, 이번 D2에서 PENDING 복귀로 대체).
2. `SYNCED` 전이 — Agent가 repository sync workflow-run 완료를 Spring에 통지하는 수단이
   아직 없다(Agent `memory_sync` 그래프가 `complete_workflow` 미호출). 통지 수단 구현 시
   `last_synced_at` 기록과 함께 전이.
3. **`BugCollectedAgentDispatcher.markAnalyzing` 잠재 동일 이슈** — AFTER_COMMIT 내부 쓰기
   트랜잭션 유실(F2)이 Bug 경로에도 있을 수 있다. 기존 테스트가 DB 상태를 검증하지 않아
   미발견 상태. 확인 필요.
4. PCM Knowledge reconcile — `repository_removed`/`repository_changed`가 repository-derived
   지식을 정리하지 않음(Agent 측 기존 TODO).
5. Document Sync 디스패치, push 웹훅 수신 — 이번 범위 밖.
