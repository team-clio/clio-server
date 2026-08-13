# Agent Graph 연동 개요

## 기준

- `clio-agent-graph`의 개념과 계약을 상위 기준으로 사용한다.
- Spring은 Bug·Issue와 Agent workflow의 lifecycle, 트랜잭션, 무결성을 관리한다.
- Agent는 정규화, Retrieval, Issue 후보 구성, 매칭, 분석, 품질 판단을 관리한다.
- Spring은 Agent 내부 서브그래프를 호출하지 않고 `clio_agent` 루트만 호출한다.

## 핵심 흐름

```text
Client → Spring: Bug 한 건 수집
Spring → clio_agent: project_id, bug_id를 포함한 root 요청
Agent → Spring: workflow PENDING 등록 후 RUNNING 전이
Agent → Spring: Bug 원문 조회
Agent: 정규화, 유사 Bug 검색, 후보 Issue 그룹화, 매칭 판단
Agent → Spring: 매칭 결과 적용
Agent: 필요한 분기에서 Issue 분석
Agent → Spring: 분석 결과 저장
Agent → Spring: 전체 workflow COMPLETED 또는 FAILED 전이
```

## 완료된 구조 변경

- `bug_reports` 계층을 없애고 원문을 `bugs`에 통합
- `bug_grouping_decisions`, `analysis_jobs`, `agent_operations` 제거
- 공통 `agent_workflow_runs`와 workflow 기반 결정·분석 결과 연결
- Retrieval 문서에서 `bug_report_id`와 Spring DB FK 제거
- Retrieval 검색 후 Spring에는 주어진 Bug ID의 Issue 연결만 조회
- Client API는 `external-api`, Agent API는 `internal-api` package와 경로로 분리

세부 계약은 `api-contract.md`, 소유권과 상태 의미는
`../api-boundaries-and-lifecycle.md`를 기준으로 한다.
