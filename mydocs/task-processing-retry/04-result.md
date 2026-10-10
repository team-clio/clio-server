# 실패한 처리 재시도 — Result

## 완료 범위

| 결정 | 구현 |
|---|---|
| D1 | `BugStatus.FAILED` 추가. `process_report` run이 `FAILED`가 되면 `ANALYZING` 버그를 `FAILED`로 전환 |
| D2 | 재시도 request_id `process-bug-{bugId}-retry-{n}`. 첫 시도는 기존 형식 유지 |
| D3 | `POST /external-api/v1/projects/{p}/bugs/{bugId}/retry` (202, `FAILED`만 허용) |
| D4 | 활성 저장소 중 `FAILED`가 있으면 재시도 409, 동기화 중이면 `NEW`로 대기 |
| D5 | `POST /api/v1/projects/{p}/repositories/{r}/sync` (202, `FAILED`·`SYNCED`만 허용) |
| D6 | Flyway `V8__add_failed_bug_status.sql`로 `bugs_status_check` 교체 |

`mydocs/api-boundaries-and-lifecycle.md`의 Bug 처리 호출과 상태 전이를 갱신했다. Agent는 변경하지 않았다.

## 검증

### 자동 테스트

- `./gradlew test`: 54 tests, 0 failures
- 추가한 테스트
  - `AgentIntegrationLifecycleTest`: 실패 시 `FAILED` 전환, 사용자가 바꾼 상태 보존, 재시도 request_id 계산
    (`process-bug-70` 같은 다른 버그 ID는 세지 않음)
  - `BugRetryLifecycleTest`: 재시도 성공, `FAILED`가 아닌 버그 거부, 저장소 실패 시 거부
  - `ProjectControllerTest`: `FAILED` 저장소 재동기화, 진행 중 동기화 거부
- D3 단독 커밋(`7002347`)에서도 전체 테스트가 통과하는 것을 확인했다.

### 체험 스택 (배포 구성 + 이 브랜치 서버 이미지 + Agent main `58201b0`)

1. 기존 DB에 V8이 적용되어 제약에 `FAILED`가 추가되었다.
2. Ollama를 멈추고 버그를 등록하자 Agent run이 실패했고 버그가 `FAILED`가 되었다.
   실패 메시지: `Retrieval operation failed after one retry: Ollama embed is unavailable at http://ollama:11434.`
3. Ollama를 다시 켜고 재시도하자 202, 연속 두 번째 요청은 409(`ANALYZING`)였다. 버그는 `TRIAGED`가 되었다.
   run 기록: `process-bug-12`(FAILED), `process-bug-12-retry-1`(COMPLETED)
4. `SYNCED` 저장소 재동기화 요청은 202, 진행 중 두 번째 요청은 409(`SYNCING`)였다. 56초 뒤 `SYNCED`로 끝났다.

## 남은 과제

- clio-admin에 `FAILED` 상태 표시와 재시도·재동기화 버튼 추가
- 변경 전에 실패해 `ANALYZING`에 남은 버그(체험 스택 #6, #7, #8, #10)는 자동 전환하지 않는다.
  `PATCH /bugs/{id}`로 `FAILED`로 바꾼 뒤 재시도할 수 있다.
- Agent `start_workflow` 자체가 실패해 run이 생성되지 않은 경우는 여전히 `ANALYZING`에 남는다.
- 자동 재시도(backoff)는 다루지 않았다.
