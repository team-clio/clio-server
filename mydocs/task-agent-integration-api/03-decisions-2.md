# 03. Decisions (계속) — Agent Graph 연동 API 구현

이 문서는 [`03-decisions.md`](03-decisions.md)에서 이어진다.

## D6. 분석 작업 생성·상태 전이와 결과 완료 처리

- 결정일: 2026-08-10
- 상태: 확정·반영 완료
- 선택: **Server가 명시적인 API로 AnalysisJob을 먼저 생성하고 Agent에 ID를 전달**

### 적용 방식

- `POST .../issues/{issueId}/analysis-jobs`가 최초 분석과 재분석 작업을 생성한다.
- 재분석은 같은 Issue의 완료된 이전 작업과 결과 snapshot을 필수로 참조한다.
- 기존 Issue context 주소는 `GET .../analysis-jobs/{jobId}/context`로 변경한다.
- Context에는 Issue·Trigger Bug·대표 Bug 식별자와 이전 `IssueAnalysis`를 포함한다.
- Agent가 소유하는 active `NormalizedReport`는 Agent adapter가 Bug ID로 결합한다.
- `PENDING → RUNNING`, `PENDING/RUNNING → FAILED`만 상태 API에서 허용한다.
- 결과 PUT은 `RUNNING` 작업에만 허용하며 snapshot 저장과 `COMPLETED` 전이를 한 트랜잭션에서 수행한다.
- `INSUFFICIENT_EVIDENCE`도 정상 결과이므로 Job은 `COMPLETED`가 된다.

### 결정 근거

- 최신 IA 공개 계약은 실제 `analysis_job_id`를 실행 전에 요구한다.
- 매칭과 Job 생성을 분리해야 수동 분석·재분석·실행 시점 제어가 가능하다.
- 결과 없는 완료 상태를 금지하고 결과 저장과 완료를 원자적으로 묶어야 한다.
- 재분석마다 새 Job과 전체 snapshot을 만들어 과거 결과를 덮어쓰지 않는다.

### API 변경

- 추가: `POST /api/v1/projects/{projectId}/issues/{issueId}/analysis-jobs`
- 변경: `GET .../issues/{issueId}/analysis-context` → `GET .../analysis-jobs/{jobId}/context`
- 필요한 Agent 연동 API는 총 9개가 된다.
- 다음 결정: D7 동기화 revision 모델
