# 실패한 처리 재시도 — Plan

> 승인: 2026-10-11

## 확인한 사실

- `bugs.status`에는 Hibernate가 만든 `bugs_status_check` 제약이 있다(`NEW, ANALYZING, TRIAGED,
  RESOLVED, IGNORED`). 상태 값을 추가하면 기존 DB에서는 제약 교체가 필요하다.
  `V6__align_project_source_sync_status.sql`이 `sync_status`에 같은 작업을 한 선례다.
- Agent는 버그 ID를 `request_id`가 아니라 payload의 `bug_id`에서 읽는다. request_id 형식을 바꿔도
  Agent 수정이 필요 없다.
- Agent `start_workflow`는 같은 request_id의 run이 `COMPLETED`면 재생하고, `PENDING` 외 다른 상태면 거부한다.
- 서버 `AgentWorkflowRunService.update`는 `PENDING`으로 되돌리는 전이를 거부한다.
- `BugLifecycleService.claimForAnalysis`는 행 잠금으로 `NEW` 버그만 `ANALYZING`으로 점유한다.
  재시도 중복 요청은 이 점유로 한 번만 디스패치된다.

## 구현 단계

1. 실패 반영: Agent run이 `FAILED`가 되면 버그에 실패를 반영한다(D1, D6).
2. 재시도 request_id: 재시도마다 기존 실패 기록을 보존하는 식별자를 만든다(D2).
3. 버그 재시도 API: 재시도 가능 조건을 검사하고 버그를 다시 디스패치한다(D3, D4).
4. 저장소 재동기화 API: 전용 엔드포인트로 동기화를 다시 시작한다(D5).
5. 문서: `mydocs/api-boundaries-and-lifecycle.md`의 버그·저장소 lifecycle을 갱신한다.
6. 검증: 단위·통합 테스트, 그리고 체험 스택에서 실제 실패 버그를 재시도해 `TRIAGED`까지 확인한다.

각 단계는 해당 결정이 내려진 뒤 구현하고, 결정 단위로 커밋한다.

## 결정 포인트

### D1. Agent 실패를 버그에 어떻게 드러낼지

- A. `BugStatus.FAILED`를 추가한다. run이 `FAILED`가 되면 버그를 `ANALYZING → FAILED`로 바꾼다.
  재시도 대상이 상태만으로 드러난다. 상태 값이 늘어나므로 admin과 외부 소비자가 새 값을 처리해야 한다.
- B. 상태는 `ANALYZING`으로 두고, 버그 응답에 최근 처리 결과(run 상태, failureCode, failureMessage)를
  추가한다. 상태 계약은 그대로지만 `ANALYZING`이 실제와 다르게 남는다.
- C. run이 `FAILED`가 되면 버그를 `NEW`로 되돌린다. 기존 전이만 쓴다. 하지만 `NEW` 버그는 저장소
  동기화가 끝날 때 자동으로 다시 디스패치되어 의도치 않은 자동 재시도가 생기고, 실패 사실도 사라진다.

추천: A. 기각 조건: 버그 상태를 닫힌 값 집합으로 다루는 외부 소비자가 있고 함께 고칠 수 없으면 B가 안전하다.

### D2. 재시도 request_id

- A. 새 request_id `process-bug-{bugId}-retry-{n}`을 쓴다. `n`은 이 버그의 기존 run 수로 계산한다.
  스키마 변경 없이 실패 기록을 보존한다.
- B. A와 같지만 `n`을 버그의 시도 횟수 컬럼으로 관리한다. 조회가 단순하지만 스키마가 바뀐다.
- C. 기존 `FAILED` run을 `PENDING`으로 되돌려 같은 request_id를 재사용한다. 실패 기록이 덮어써지고
  run 상태 전이 규칙을 완화해야 한다.
- D. Agent가 `FAILED` run을 재시작하도록 바꾼다. 두 저장소 계약이 함께 바뀐다.

추천: A. 기각 조건: request_id 접두사로 run을 세는 쿼리가 다른 request 유형과 충돌하거나, 시도 횟수를
API에 자주 노출해야 하면 B가 낫다.

### D3. 버그 재시도 API 위치와 허용 상태

- A. `POST /external-api/v1/projects/{p}/bugs/{bugId}/retry`. 기존 버그 상태 변경(`PATCH`)과 같은 경계다.
- B. `POST /api/v1/projects/{p}/bugs/{bugId}/retry`. 관리 화면 전용 경계다.

허용 상태는 D1 결과를 따른다(A면 `FAILED`만, B면 `ANALYZING`이면서 최근 run이 `FAILED`).
그 외 상태는 409를 반환한다.

추천: A. 기각 조건: 외부 연동(Sentry 등)이 재시도를 호출하면 안 되는 정책이면 B로 제한한다.

### D4. 저장소가 동기화되지 않은 상태에서 재시도를 요청하면

- A. 409로 거부한다. 사용자가 저장소를 먼저 고쳐야 한다는 사실이 바로 드러난다.
- B. 버그를 `NEW`로 두고 202를 반환한다. 동기화가 끝나면 기존 디스패처가 처리한다.
- C. 저장소가 `FAILED`면 A, `PENDING`·`SYNCING`이면 B로 처리한다.

추천: C. 기각 조건: 응답 상태를 하나로 단순화해야 하면 A가 낫다. B는 저장소가 `FAILED`일 때 버그가
조용히 계속 기다린다.

### D5. 저장소 재동기화 API

- A. `POST /api/v1/projects/{p}/repositories/{r}/sync`. `FAILED`·`SYNCED`에서 허용하고, `PENDING`·`SYNCING`은 409.
- B. A와 같지만 `FAILED`에서만 허용한다.
- C. 새 API 없이 기존 `PATCH` 부수 효과를 문서화한다.

추천: A. 기각 조건: `SYNCED`에서의 재동기화가 PCM 지식 생성(LLM 호출) 비용을 반복 발생시키는 것이
우려되면 B로 제한한다.

### D6. 기존 DB의 상태 제약 (D1이 A일 때만)

- A. Flyway `V8`에서 `bugs_status_check`를 새 값 집합으로 교체한다. V6와 같은 방식이다.
- B. 새 DB만 지원하고, 기존 DB는 재생성하도록 안내한다.

추천: A. 기각 조건: pre-MVP 원칙대로 호환 migration을 늘리지 않기로 하면 B다. 다만 기존 체험 스택과
개발 DB가 바로 깨진다.

## 미결정으로 남기는 것

- 기존에 `ANALYZING`으로 고정된 버그를 `FAILED`로 일괄 전환할지는 다루지 않는다. 체험 스택의 고정 버그는
  검증 단계에서 수동으로 처리한다.
