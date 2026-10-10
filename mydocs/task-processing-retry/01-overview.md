# 실패한 처리 재시도 — Overview

> 승인: 2026-10-11

## 배경

Clio는 버그 처리(`process_report`)와 저장소 동기화(`repository_added`)를 Agent에 비동기로 맡긴다.
실제 외부 저장소(Finvibe 백엔드)와 배포 구성(`compose.release.yaml`)으로 체험하는 동안 Agent 쪽
실패가 세 번 반복해서 발생했다.

| 실패 | 원인 | 결과 |
|---|---|---|
| 저장소 동기화 | LLM 출력이 토큰 상한에서 잘림 | `syncStatus=FAILED` |
| 버그 처리 | provider 400 (짝 없는 tool_call) | 버그 `ANALYZING`에 고정 |
| 버그 처리 | embedding 서버 OOM | 버그 `ANALYZING`에 고정 (4건) |

원인 결함은 Agent에서 고쳤다(clio-agent-graph #8, #9, #10). 하지만 일시적 장애는 앞으로도 생기고,
그때마다 **사용자가 회복할 수단이 없다.**

## 현재 상태

### 버그 처리

- 버그 상태: `NEW → ANALYZING → TRIAGED`. 전이 규칙에서 `ANALYZING → NEW`는 허용된다.
- 디스패치: `BugCollectedAgentDispatcher`가 버그 수집 커밋 후, 또는 저장소 동기화 완료 시 `NEW` 버그를
  `ANALYZING`으로 점유하고 Agent에 `request_id = process-bug-{bugId}`로 보낸다.
- 실패 기록: Agent가 실패하면 `PATCH workflow-runs/{runId}`로 run을 `FAILED`로 바꾼다.
  서버의 `updateFailed`는 run만 갱신하고 **버그는 건드리지 않는다.**
- `markNew`는 HTTP 디스패치 자체가 실패했을 때만 호출된다.
- 같은 `process-bug-{bugId}`로 다시 보내면 Agent의 `start_workflow`가 기존 `FAILED` run을 보고 거부한다.
  `COMPLETED` run은 재생(replay)한다.
- 수동 `PATCH /bugs/{bugId}`로 상태를 `NEW`로 바꿔도 다시 디스패치되지 않는다.

### 저장소 동기화

- 상태: `PENDING → SYNCING → SYNCED | FAILED`.
- 디스패치마다 `request_id = repository-{p}-{r}-{UUID}`로 새로 만든다.
- 전용 재동기화 API는 없다. `PATCH /projects/{p}/repositories/{r}`(설정 수정)가 `PENDING`으로 되돌리고
  `REPOSITORY_ADDED`를 다시 발행해 **부수 효과로** 재동기화된다.
- 체험 중에는 이를 몰라 저장소를 삭제하고 재등록했다. 재등록은 PCM 지식과 ID를 새로 만든다.

### 관리 화면

- clio-admin은 `syncStatus`만 표시하고 재시도 버튼이 없다.

## 문제

1. Agent 처리 실패 후 버그가 `ANALYZING`에 영구히 남는다. 실패 여부도 버그 API로는 보이지 않는다.
2. 실패한 버그를 다시 처리할 API가 없다. 같은 request_id 재사용은 Agent가 거부한다.
3. 저장소 재동기화가 설정 수정 API의 부수 효과로만 가능하다. 의도와 계약이 드러나지 않는다.

## 개선 방향

- 실패한 버그 처리와 저장소 동기화를 사용자가 명시적으로 다시 실행하는 API를 제공한다.
- Agent 실패가 버그 상태에 반영되어, 재시도 대상인지 API로 판단할 수 있게 한다.
- 재시도해도 기존 실패 기록(`agent_workflow_runs`)이 사라지지 않게 한다.

## 범위

### 포함

- clio-server: 버그 처리 재시도 API, 저장소 재동기화 API, 실패 상태 반영
- clio-agent-graph: 재시도 계약에 필요한 경우에만 보완
- 관련 테스트와 API 문서

### 미포함 (후속 작업)

- clio-admin 재시도·재동기화 버튼
- 자동 재시도(backoff, 스케줄러)
- 이슈 재분석(`reanalysis`) 경로 변경
- Agent `start_workflow` 자체가 실패해 run이 생성되지 않은 경우의 감지

## 참고

- `src/main/java/ax/clio/bug/service/BugLifecycleService.java`
- `src/main/java/ax/clio/agent/BugCollectedAgentDispatcher.java`
- `src/main/java/ax/clio/workflow/service/AgentWorkflowRunService.java`
- `src/main/java/ax/clio/project/service/ProjectService.java`
- clio-agent-graph `workflows/orchestration/clio_server.py` (`start_workflow`)
