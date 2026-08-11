# Agent 연동 API 계약

## 목적과 경계

- 독자: Clio Server·Agent Graph 연동 구현자
- 모든 계약은 외부 사용자 API와 분리된 `/internal/api/v1` namespace를 사용한다.
- Java 내부 필드는 camelCase, Agent와 주고받는 JSON은 snake_case다.
- ID는 양수 JSON number, `request_id`는 프로젝트·operation별 멱등 키다.
- Server는 업무 데이터와 Agent 결정을 저장한다.
- NormalizedReport·retrieval index·PCM·Repository snapshot은 Agent가 소유한다.

## 전체 흐름

```text
Client → Server: BugReport 수집
Server → Agent: 원본 BugReport 조회 결과 전달
Agent → Server: BugGroupingDecision 반영
Agent → Server: MatchDecision 반영
Server: AnalysisJob 생성
Agent → Server: Job RUNNING
Agent ← Server: 분석 context 조회
Agent → Server: IssueAnalysis 결과 저장
```

기존 Bug grouping 결과에 `resulting_issue_id`가 있으면 Issue matcher는 다시 실행하지 않는다.

## 1. 원본 BugReport 조회

`GET /internal/api/v1/projects/{projectId}/bug-reports/{reportId}`

```json
{
  "bug_report_id": 351,
  "title": "결제 실패",
  "description": "결제 버튼을 누르면 500 오류가 발생합니다.",
  "source": "API",
  "error_type": "PaymentException",
  "message": "payment failed",
  "stack_trace": ["PaymentService.approve"],
  "occurred_at": "2026-08-10T00:00:00Z",
  "raw_payload": {"status": 500}
}
```

## 2. Bug grouping 결정 반영

`POST /internal/api/v1/projects/{projectId}/bug-reports/{reportId}/grouping-decisions`

```json
{
  "request_id": "REQ-GROUP-1",
  "grouping_decision": {
    "bug_report_id": 351,
    "action": "MATCH_EXISTING",
    "matched_bug_id": 72,
    "confidence": 0.98,
    "supporting_reasons": ["현상과 오류 흐름이 같습니다."],
    "contradictions": [],
    "review_reasons": [],
    "candidate_comparisons": []
  }
}
```

- `MATCH_EXISTING`: 기존 Bug에 발생 건을 추가한다.
- `CREATE_NEW`: 새 Bug를 만들고 발생 건을 연결한다.
- `REVIEW`: 결정만 기록하고 관계를 변경하지 않는다.
- 응답의 `ready_for_issue_matching`이 `true`일 때만 다음 Issue 매칭을 실행한다.

## 3. Issue 매칭 결정 반영

`POST /internal/api/v1/projects/{projectId}/bugs/{bugId}/match-decisions`

```json
{
  "request_id": "REQ-MATCH-1",
  "bug_report_id": 351,
  "match_decision": {
    "bug_id": 72,
    "action": "AUTO_LINK",
    "matched_issue_id": 19,
    "confidence": 0.97,
    "supporting_reasons": ["동일한 오류 흐름입니다."],
    "contradictions": [],
    "review_reasons": [],
    "candidate_comparisons": []
  }
}
```

- `AUTO_LINK`: 기존 Issue 연결
- `CREATE_NEW`: 새 Issue 생성 후 연결
- `REVIEW`: 결정만 기록

## 4. 분석 작업 생성

`POST /internal/api/v1/projects/{projectId}/issues/{issueId}/analysis-jobs`

```json
{
  "request_id": "REQ-JOB-1",
  "trigger_bug_id": 72,
  "previous_analysis_job_id": null
}
```

재분석은 같은 Issue의 완료된 이전 작업 ID를 포함해야 한다.

## 5. 분석 작업 상태 변경

`PATCH /internal/api/v1/projects/{projectId}/analysis-jobs/{jobId}`

```json
{"request_id": "REQ-START-1", "status": "RUNNING"}
```

```json
{"request_id": "REQ-FAIL-1", "status": "FAILED", "failure_reason": "timeout"}
```

- 허용: `PENDING → RUNNING`
- 허용: `PENDING/RUNNING → FAILED`
- `COMPLETED`는 결과 저장 API만 설정한다.

## 6. 분석 context 조회

`GET /internal/api/v1/projects/{projectId}/analysis-jobs/{jobId}/context`

- Issue와 trigger Bug를 반환한다.
- 대표 Bug는 최대 5개이며 Agent가 자기 active NormalizedReport와 결합한다.
- 재분석이면 `previous_analysis`에 완전한 이전 IssueAnalysis snapshot을 반환한다.

## 7. 분석 결과 저장

`PUT /internal/api/v1/projects/{projectId}/analysis-jobs/{jobId}/result`

```json
{
  "request_id": "REQ-RESULT-1",
  "issue_analysis": {
    "analysis_job_id": 501,
    "project_id": 3,
    "issue_id": 19,
    "status": "INSUFFICIENT_EVIDENCE",
    "evidence": [],
    "relations": [],
    "findings": [],
    "hypotheses": [],
    "warnings": ["근거가 부족합니다."]
  }
}
```

결과 snapshot 저장과 Job의 `COMPLETED` 전이는 한 트랜잭션에서 처리한다.

## 멱등성과 오류

- 동일 `request_id`와 동일 요청: 최초 HTTP 상태와 응답을 재생한다.
- 동일 `request_id`와 다른 요청: `409 Conflict`
- 다른 프로젝트의 리소스 또는 없는 리소스: `404 Not Found`
- 상태 전이·경로 ID·본문 ID 충돌: `409 Conflict`
- JSON·enum·필수값 validation 실패: `400 Bad Request`
- 재생 여부는 `Idempotent-Replayed: true|false` 응답 헤더로 확인한다.
