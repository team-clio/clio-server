# Agent 연동 API 계약

Agent와 Spring 사이 JSON은 `snake_case`를 사용한다. Spring은 `clio_agent` 루트
그래프만 호출하고, Agent는 아래 internal API로 원문을 읽고 lifecycle 결과 적용을
요청한다. 경로의 구체적인 버전보다 책임과 payload 계약을 우선 기준으로 삼는다.

## 실행 생명주기

### validate 성공 후 실행 등록

```json
{
  "request_id": "REQ-20260813-1",
  "request_type": "process_report",
  "request_payload": {"bug_id": 72}
}
```

Spring은 같은 프로젝트의 `(request_id, request_hash)`를 멱등하게 처리하고
`PENDING` 실행을 반환한다. 같은 `request_id`에 다른 payload면 `409`다.

### 실행 상태 변경

```json
{"status": "RUNNING", "latest_checkpoint": {"node": "normalize_report"}}
```

```json
{"status": "COMPLETED", "result_snapshot": {"issue_id": 19}}
```

```json
{
  "status": "FAILED",
  "failure_code": "RETRIEVAL_TIMEOUT",
  "failure_message": "retrieval failed after retry",
  "latest_checkpoint": {"node": "search_issue_candidates"}
}
```

허용 전이는 `PENDING → RUNNING → COMPLETED`, `PENDING/RUNNING → FAILED`다.
`NEEDS_REVIEW`, `INSUFFICIENT_EVIDENCE`는 업무 결과이므로 저장이 끝나면 실행은
`COMPLETED`다.

## Bug 원문 조회

```json
{
  "bug_id": 72,
  "project_id": 3,
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

Backfill은 `after_bug_id`, `limit` cursor로 같은 projection의 목록을 읽는다.

## 검색된 Bug의 Issue 연결 조회

요청:

```json
{"bug_ids": [101, 102, 103]}
```

응답:

```json
[
  {
    "bug_id": 101,
    "issue_id": 19,
    "issue_title": "결제 승인 실패",
    "issue_status": "OPEN",
    "issue_summary": "결제 승인 과정의 공통 실패"
  }
]
```

Spring은 후보를 판단하거나 순위를 계산하지 않는다. Agent가 자기 Retrieval DB에서
유사 Bug ID를 만든 뒤 이 projection만 요청한다.

## 새 Issue 생성

```json
{
  "workflow_run_id": 501,
  "bug_id": 72,
  "confidence": 0.81
}
```

Spring은 Issue를 생성하고 요청의 Bug를 같은 트랜잭션에서 연결한다.

```json
{
  "issue_id": 19,
  "bug_id": 72,
  "issue_created": true,
  "bug_linked": true
}
```

## 기존 Issue에 Bug 연결

```json
{
  "workflow_run_id": 502,
  "bug_id": 73,
  "confidence": 0.97
}
```

대상 `issue_id`는 경로로 전달한다. Bug가 이미 같은 Issue에 연결된 재요청이면
기존 관계를 반환하고, 다른 Issue에 연결되어 있으면 `409`다.

두 API 모두 `RUNNING`인 `process_report` workflow만 허용하고, workflow payload의
`bug_id`와 요청의 `bug_id`가 같아야 한다. `REVIEW` 판단은 Spring 도메인 쓰기를
발생시키지 않고 workflow 결과로만 저장한다. Spring은 Agent의 match action이나
판단 근거를 해석하거나 저장하지 않는다.

## Issue 분석 원문과 결과

Agent는 Issue 상세를 읽고 응답의 Bug ID별 원문을 추가 조회한다. Spring은 대표
Bug를 고르지 않는다.

분석 결과 저장 예시:

```json
{
  "issue_id": 19,
  "previous_analysis_result_id": null,
  "issue_analysis": {
    "workflow_run_id": 501,
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

결과 저장은 workflow를 자동 완료시키지 않는다. 선택된 분기의 마지막 Spring
반영까지 성공한 뒤 Agent root가 실행을 `COMPLETED`로 전이한다.
