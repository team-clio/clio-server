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
