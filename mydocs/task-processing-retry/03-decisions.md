# 실패한 처리 재시도 — Decisions

사용자가 plan 승인 시 "추천대로 진행"으로 D1~D6 결정을 위임했다. 각 항목은 plan의 추천안을 따른다.

## D1. Agent 실패를 버그에 어떻게 드러낼지

### 결정: A — `BugStatus.FAILED` 추가

- 대안 A: `FAILED` 상태를 추가하고 `process_report` run이 `FAILED`가 되면 버그를 `ANALYZING → FAILED`로 바꾼다. ← 선택
- 대안 B: 상태는 `ANALYZING`으로 두고 버그 응답에 최근 처리 결과를 추가한다. `ANALYZING`이 실제와 다르게 남는다.
- 대안 C: 실패 시 버그를 `NEW`로 되돌린다. 저장소 동기화 완료 때 자동 재디스패치되어 의도치 않은 재시도가 생기고 실패 사실이 사라진다.

선택 이유: 재시도 대상이 상태만으로 드러나고 기존 전이 규칙 안에서 표현된다. 현재 버그 상태를 닫힌 값 집합으로
다루는 외부 소비자는 clio-admin뿐이다.

구현 규칙:
- 전이: `ANALYZING → FAILED`, `FAILED → NEW | IGNORED`.
- run 실패 시 버그가 `ANALYZING`일 때만 바꾼다. 사용자가 이미 다른 상태로 바꾼 버그는 건드리지 않는다.
- 버그 ID는 run의 `request_payload.bug_id`에서 읽는다.

## D6. 기존 DB의 상태 제약

### 결정: A — Flyway `V8`에서 `bugs_status_check` 교체

- 대안 A: `V8__add_failed_bug_status.sql`에서 제약을 새 값 집합으로 교체한다. ← 선택
- 대안 B: 새 DB만 지원하고 기존 DB는 재생성한다. 체험 스택과 개발 DB가 `FAILED` 저장 시 바로 깨진다.

선택 이유: Hibernate `ddl-auto: update`는 기존 CHECK 제약을 갱신하지 않는다. `V6`가 `sync_status`에 같은 방식을
쓴 선례가 있다. 테이블이 없는 새 DB에서는 아무것도 하지 않는다.

## D2. 재시도 request_id

### 결정: A — `process-bug-{bugId}-retry-{n}`, `n`은 기존 run 수

- 대안 A: 재시도마다 새 request_id를 쓰고 `n`을 기존 run 수로 계산한다. ← 선택
- 대안 B: A와 같지만 `n`을 버그의 시도 횟수 컬럼으로 관리한다. 스키마가 바뀐다.
- 대안 C: 기존 `FAILED` run을 `PENDING`으로 되돌려 재사용한다. 실패 기록이 덮어써진다.
- 대안 D: Agent가 `FAILED` run을 재시작하도록 바꾼다. 두 저장소 계약이 함께 바뀐다.

선택 이유: 스키마와 Agent를 바꾸지 않고 실패 기록을 보존한다. Agent는 버그 ID를 payload에서 읽으므로
request_id 형식이 바뀌어도 영향이 없다.

구현 규칙:
- 첫 시도는 기존과 같은 `process-bug-{bugId}`다.
- 시도 수는 `requestId = process-bug-{bugId}` 또는 `process-bug-{bugId}-retry-%`인 run만 센다.
  `process-bug-70`처럼 접두사만 같은 다른 버그의 run은 세지 않는다.
- 결과적 변화: `IGNORED → NEW`로 되돌린 버그가 다시 디스패치되면, 이전에는 `COMPLETED` run이 재생되어 아무 일도
  일어나지 않았지만 이제 새로 처리된다.

## D3. 버그 재시도 API 위치와 허용 상태

### 결정: A — `POST /external-api/v1/projects/{p}/bugs/{bugId}/retry`

- 대안 A: 외부 API 경계. 기존 버그 상태 변경(`PATCH /bugs/{bugId}`)과 같은 곳이다. ← 선택
- 대안 B: 관리 API(`/api/v1/...`) 경계. 외부 연동이 재시도를 호출하지 못하게 제한할 때 쓴다.

선택 이유: 버그 lifecycle 변경이 이미 외부 API에 있고, 외부 연동의 재시도를 막을 정책 요구가 없다.

구현 규칙:
- `FAILED` 버그만 허용하고 그 외 상태는 409를 반환한다.
- 버그를 `FAILED → NEW`로 바꾸고 `BugCollectedEvent`를 발행한다. 커밋 후 기존 디스패처가 `ANALYZING`으로
  점유하고 D2의 새 request_id로 Agent에 보낸다. 중복 요청은 상태 검사와 점유 잠금으로 한 번만 디스패치된다.
- 응답은 202와 갱신된 버그 상태(`NEW`)다.

## D4. 저장소가 동기화되지 않은 상태에서 재시도를 요청하면

### 결정: C — 저장소가 `FAILED`면 409, `PENDING`·`SYNCING`이면 `NEW`로 대기

- 대안 A: 항상 409로 거부한다. 동기화 중인 정상 상황도 거부된다.
- 대안 B: 항상 `NEW`로 두고 202를 반환한다. 저장소가 `FAILED`면 버그가 조용히 계속 기다린다.
- 대안 C: 활성 저장소 중 `FAILED`가 있으면 409, 그 외에는 `NEW`로 두고 동기화 완료 시 디스패치한다. ← 선택

선택 이유: 일시적인 동기화 중에는 기존 수집 흐름과 같이 기다리고, 사용자가 먼저 고쳐야 하는 저장소 실패는 즉시 드러낸다.
409 메시지에 실패한 저장소 ID를 담아 D5의 재동기화 API로 이어지게 한다.

## D5. 저장소 재동기화 API

### 결정: A — `POST /api/v1/projects/{p}/repositories/{r}/sync`, `FAILED`·`SYNCED`에서 허용

- 대안 A: 전용 엔드포인트. `FAILED`·`SYNCED`에서 허용하고 `PENDING`·`SYNCING`은 409. ← 선택
- 대안 B: A와 같지만 `FAILED`에서만 허용한다. 수동 갱신이 막힌다.
- 대안 C: 새 API 없이 기존 `PATCH` 부수 효과를 문서화한다. 의도가 드러나지 않고, 체험 중 실제로 이를 몰라 삭제·재등록했다.

선택 이유: 재동기화를 명시적인 계약으로 드러낸다. `SYNCED`에서의 재동기화 비용(PCM 지식 생성 LLM 호출)은 사용자가
직접 요청할 때만 발생하므로 허용한다.

구현 규칙:
- 기존 `updateRepository`와 같은 경로로 `PENDING`으로 바꾸고 `REPOSITORY_ADDED`를 발행한다. 새 request_id가 만들어진다.
- 비활성 저장소는 409를 반환한다.
- 동기화가 끝나면 기존 `RepositorySyncCompletedEvent`가 `NEW`로 기다리는 버그를 디스패치한다(D4와 연결).
- 응답은 202와 저장소 정보(`syncStatus=PENDING`)다.
