# Agent Graph 연동 결정 기록

## D1. Bug 모델

수집 오류 한 건을 `Bug` 한 행으로 저장한다. 동일 원인의 여러 Bug는 `issue_bugs`로
Issue 하나에 연결한다. `BugReport`/occurrence 계층과 grouping decision은 제거한다.

## D2. 판단 소유권

정규화, 유사 Bug 검색, 후보 Issue 구성, 매칭, 분석, 품질 판단은 Agent가 전담한다.
Spring은 주어진 식별자의 원문 projection과 결과 적용 transaction만 제공한다.

## D3. Agent 진입점

Spring은 `clio_agent` 루트 그래프만 호출한다. 기능별 독립 그래프는 Agent 내부
재사용·개발 entrypoint이며 Spring API가 아니다.

## D4. 공통 workflow lifecycle

`validate_request` 성공 후 `agent_workflow_runs`를 `PENDING`으로 생성하고 실제 분기
시작 시 `RUNNING`, 마지막 필수 작업과 Spring 반영 후 `COMPLETED`, 기술적 실패 시
`FAILED`로 전이한다. 업무 결과인 `NEEDS_REVIEW`와 `INSUFFICIENT_EVIDENCE`는
`COMPLETED`다.

## D5. 멱등성

프로젝트별 `request_id`와 canonical request hash로 root 요청을 보호한다. 별도
`agent_operations` 응답 저장 테이블은 사용하지 않는다. `issue_bugs.bug_id`,
workflow별 decision/result unique 제약도 함께 적용한다.

## D6. 분석 결과

`analysis_jobs`를 제거한다. 분석 결과는 `workflow_run_id`와 `issue_id`에 연결하고,
재분석은 `previous_analysis_result_id`로 immutable snapshot 이력을 만든다. 결과 저장과
전체 workflow 완료는 분리한다.

## D7. Retrieval 경계

Agent의 `bug_retrieval_documents`에는 `project_id`, `bug_id`만 식별자로 저장한다.
Spring DB FK와 `bug_report_id`는 제거한다. Agent는 exact·lexical·vector 검색과 RRF로
유사 Bug ID를 만든 뒤 Spring에서 그 Bug들의 Issue 연결 projection만 읽는다.

## D8. 동기화 저장소

문서 PCM, Git mirror, repository manifest와 LangGraph checkpoint는 Agent가 소유한다.
Spring은 공통 workflow 상태와 `project_sources`, 필요한 credential 참조만 관리한다.
