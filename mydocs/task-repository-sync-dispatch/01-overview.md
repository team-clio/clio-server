# Repository Sync Dispatch — Overview

## 배경

Clio는 `project_sources`에 등록된 Repository를 Agent 측에서 bare Git mirror로 복제하고
고정 commit 기반 코드 탐색·PCM Knowledge ingest를 수행한다. 이 흐름은 Agent root 그래프의
`repository_added` / `repository_removed` / `repository_changed` 요청으로 시작된다.

그러나 현재 **Spring은 이 요청을 한 번도 보내지 않는다.**

## 현재 상태

| 영역 | 상태 |
|---|---|
| Agent side (`repository_sync`, `code_change_sync` 그래프) | 구현됨, 단독 테스트 통과 |
| Spring → Agent 디스패치 | **없음** |
| `project_sources.sync_status` 전이 | 생성 시 `PENDING`만, 이후 미갱신 |
| `last_synced_at` | 미기입 |
| 웹훅/스케줄러 | 없음 |

## 문제

1. `ClioAgentClient`에는 `processBug()`, `readCodeEvidence()`만 있다. repository 이벤트
   전송 수단이 없다.
2. `ProjectService.createRepository/updateRepository/deleteRepository`는 DB만 갱신하고
   이벤트 발행이나 Agent 호출을 하지 않는다.
3. 결과적으로 repository 등록·변경·삭제가 제품 표면에서 **아무 효과도 없다**.
   mirror·manifest·PCM Knowledge는 한 번도 갱신되지 않는다.
4. UI(`clio-admin`)는 `PENDING|SYNCING|SYNCED|FAILED`를 이미 정의하지만, 서버가
   `PENDING` 이후 상태를 만들지 않아 영원히 `PENDING`으로 보인다.

설계 문서(`mydocs/api-boundaries-and-lifecycle.md` 156–157행)는 Spring이 `project_sources`
기반으로 `repository_added/removed`를 발행하도록 명시한다. 구현이 뒤처져 있는 것이다.

## 개선 방향

- Repository 등록/변경/삭제 시 Spring이 Agent로 `repository_added` / `repository_removed`
  / `repository_changed` 요청을 비동기 발행한다.
- 발행 결과에 따라 `project_sources.sync_status`(`PENDING→SYNCING→SYNCED|FAILED`)와
  `last_synced_at`을 갱신한다.
- 기존 Bug 디스패치(`BugCollectedAgentDispatcher`, 트랜잭션 커밋 후 이벤트) 패턴을 재사용한다.

## 범위

### 포함

- `ClioAgentClient`에 repository 이벤트 전송 메서드 추가
- `ProjectService` 변경(생성/수정/삭제)에 대한 이벤트 발행 + 디스패처
- `sync_status` / `last_synced_at` 갱신
- 관련 테스트

### 미포함 (후속 작업)

- `repository_changed`가 요구하는 active commit 컬럼 (설계 문서가 "추후 설계"로 명시)
- Document Sync 디스패치
- PCM Knowledge reconcile (Agent 측 별도 TODO)
- 웹훅 수신 (push 이벤트 기반 자동 동기화)

## 참고

- `clio-server/mydocs/api-boundaries-and-lifecycle.md`
- `clio-server/src/main/java/ax/clio/agent/` (기존 디스패치 패턴)
