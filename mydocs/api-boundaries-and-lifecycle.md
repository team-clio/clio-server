# API 경계와 Bug·Issue 생명주기

## 기준

Agent Graph의 계약을 상위 기준으로 사용한다. 수집된 오류 한 건을 `Bug`,
같은 원인으로 판단된 여러 Bug의 작업 단위를 `Issue`로 정의한다. Agent는
정규화·검색·판단·분석 결과를 소유하고, Spring Server는 Bug·Issue와 모든 상태
전이를 소유한다.

```text
Bug (one collected error)
  → Agent MatchDecision
  → Issue 생성 또는 기존 Issue 연결 요청
  → Issue (same root cause work item)
  → WorkflowRun / immutable AnalysisResult snapshot
```

Agent가 `CREATE_NEW`를 판단해도 DB row를 직접 만들지 않는다. Spring에 Issue 생성을
요청하면 Spring의 한 트랜잭션이 Issue 생성, Bug 연결과 집계 갱신을 수행한다. 기존
Issue로 판단한 경우에는 별도의 Bug 연결 API를 호출한다.

## API namespace

| 호출자 | prefix | 책임 |
|---|---|---|
| Client → Spring | `/external-api/v1` | Bug 수집·목록, Bug 상태/심각도, Issue 조회·상태/담당자/분류 |
| Agent → Spring | `/internal-api/v1` | Bug·Issue 원문 조회, workflow 실행 상태, Issue 생성·Bug 연결, 분석 결과 저장 |

Controller도 `controller.external`과 `controller.internal` package로 분리한다. 한 Controller가
두 namespace를 함께 처리하지 않는다.

## Agent 단일 진입점

Spring이 호출하는 Agent 계약은 최상위 `clio_agent` 하나다. Spring은
`report_matcher`, `issue_analyzer`, `issue_reanalyzer` 같은 하위 그래프를 직접
호출하지 않는다.

```text
Spring
  → clio_agent
      ├─ Report Processing
      ├─ Issue Analysis
      ├─ Document Sync
      └─ Repository Sync
```

Agent 저장소의 독립 그래프 entrypoint는 기능 개발·테스트에 사용할 수 있지만,
Spring 통합 API가 아니다. 최상위 `clio_agent`가 요청을 라우팅하고 해당
하위 그래프를 내부에서 조합·재사용한다. 운영 Report Processing은 실제
Normalizer·Hybrid Retrieval·Matcher를, Issue Analysis는 실제
`issue_analyzer`/`issue_reanalyzer`를 호출한다.

### 판단 책임

Spring은 Bug·Issue 생명주기와 데이터 무결성만 관리한다.

- Bug·Issue 원본 조회
- Bug·Issue 생성·연결·상태 전이
- Agent 요청에 따른 Issue 생성·Bug 연결과 분석 결과 저장
- 트랜잭션, FK·unique 제약, 멱등성 보호

다음은 전적으로 Agent의 책임이다.

- 대표 Bug 선택과 분석 문맥 구성
- 유사 Bug 검색과 Issue 후보 순위 결정
- 검색 query 생성과 코드·문서 탐색
- 매칭, 원인, 품질, 사람 검토 필요 여부 판단

Spring은 `대표 Bug 조회`, `분석에 적합한 Bug 조회`, `Issue 후보 조회`
같은 의미적 판단 API를 제공하지 않는다. 그 대신 Issue와 연결된 Bug를 일반적인
정렬·paging 계약으로 반환하고 Agent가 사용할 문맥을 선택한다.

### Issue Analysis 호출 경계

```text
Spring → clio_agent에 Issue 분석 요청
clio_agent → Spring에 Issue 원문 조회
clio_agent → Spring에 Issue에 연결된 Bug 조회
clio_agent → 내부 Issue Analysis 서브그래프 실행
clio_agent → Spring에 분석 결과 저장 요청
```

분석 중 PCM·코드·과거 분석 검색과 원인 판단은 Agent 내부에서 실행한다.
Spring은 Agent가 보낸 결과를 재판단하지 않고 대상 project·Issue 일치, 상태 전이,
중복 저장 여부만 검증한다.

## DB 명명과 소유권

- `bugs`: 수집된 오류 원문 한 건과 Agent 처리 상태.
- `issues`: 같은 root cause로 연결된 여러 Bug의 작업 단위와 상태.
- `issue_bugs`: Bug는 최대 한 Issue에만 연결된다.
- `agent_workflow_runs`: 모든 `clio_agent` 요청의 공통 실행 상태.
- `analysis_results`: 분석이 실행된 workflow의 immutable IssueAnalysis snapshot.
- NormalizedBug, retrieval index, PCM, repository snapshot은 Spring JPA schema에 만들지 않는다.

`bug_occurrences`, `bug_reports`, `bug_grouping_decisions`는 목표 schema에서 제거한다.
기존 테이블의 원문 칼럼과 데이터는 Spring 반영 단계에서 `bugs`로 통합한다.

### `bugs` 목표 칼럼

| 칼럼 | 용도 |
|---|---|
| `id`, `project_id` | Bug 식별자와 프로젝트 소속 |
| `source` | 수집 경로 |
| `title`, `description` | 사람이 제공한 제목과 설명 |
| `error_type`, `message` | 오류 유형과 메시지 |
| `stack_trace` | 원문 stack frame 목록 |
| `raw_payload` | 추가 원문 정보 |
| `occurred_at` | 오류 발생 시각 |
| `status`, `severity` | Spring이 관리하는 처리 상태와 심각도 |
| `created_at`, `updated_at` | 생성·변경 시각 |

`fingerprint`, `occurrence_count`, `first_seen_at`, `last_seen_at`은 동일 오류를 Bug 하나로
합치는 모델에서 필요했던 칼럼이므로 목표 `bugs`에서 제거한다. Issue의
`bug_count`가 해당 Issue로 분류된 오류 건수를 나타낸다.

## Root 요청 검증

`validate_request`는 DB를 조회하지 않고 Spring이 보낸 JSON 계약만 검증한다.

공통 필드는 다음과 같다.

| 필드 | 계약 |
|---|---|
| `request_id` | 빈 문자열 금지. 같은 요청을 재시도할 때 유지한다. |
| `request_type` | Agent가 정의한 요청 유형만 허용한다. |
| `project_id` | 빈 문자열 금지. Spring 숫자 ID는 JSON에서 문자열로 직렬화한다. |
| `payload` | `request_type`별로 정의된 필드만 허용한다. |

`validate_request` 자체를 위한 추가 도메인 테이블은 필요하지 않다. Spring은 요청
발행 전에 대상 row의 존재와 `project_id` 소속만 검증한다.

| 요청 | Spring 원본 테이블·칼럼 |
|---|---|
| `process_report` (추후 `process_bug` 명칭 가능) | `bugs.id`, `bugs.project_id` |
| `analyze_issue` | `issues.id`, `issues.project_id` |
| `repository_added`, `repository_removed` | `project_sources.id`, `project_id`, `repo_url`, `target_branch` |
| `repository_changed` | 위 칼럼 + 현재 active commit. 현재 Spring에 해당 칼럼이 없으므로 추후 설계한다. |
| 문서 추가·삭제 | 요청이 title·markdown·revision을 직접 실어 보내므로 Spring 도메인 테이블은 필수가 아니다. |

Agent root 요청은 기존 `process_report` request type을 호환성 때문에 유지하되,
payload 식별자는 실제 구조에 맞게 `bug_id` 하나로 통일한다. 내부 정규화 모델의
식별자도 `bug_id`를 사용한다.

| 현재 이름 | 선택적 정리 이름 |
|---|---|
| `process_report` | `process_bug` |
| `report_id` | `bug_id` (반영 완료) |
| `load_and_normalize_report` | `load_and_normalize_bug` |
| `NormalizedReport` | `NormalizedBug` |

다만 `bug_id`와 `bug_report_id`를 동시에 저장·검증하는 구조는 이름의 문제가
아니므로 제거한다. 수집 원문과 매칭 대상은 하나의 `bugs.id`로 식별한다.

## Bug Processing

목표 Report Processing은 다음 Bug Processing으로 단순화한다.

```text
Bug 조회·정규화
  → Agent 소유 index에서 유사 Bug 검색
  → 검색된 Bug들의 Issue 연결 정보 조회
  → Agent가 Issue 후보를 그룹화·순위화
  → Agent가 AUTO_LINK / REVIEW / CREATE_NEW 판단
      ├─ AUTO_LINK → Spring에 기존 Issue-Bug 연결 요청
      ├─ REVIEW → workflow 결과만 기록
      └─ CREATE_NEW → Spring에 Issue 생성 요청
```

### Spring 읽기

`Bug 조회`는 `bugs`에서 다음 칼럼을 반환한다.

`id`, `project_id`, `source`, `title`, `description`, `error_type`, `message`,
`stack_trace`, `occurred_at`, `raw_payload`

`search_issue_candidates`는 Spring에 후보 검색을 요청하지 않는다. Agent가 유사 Bug ID를
찾은 뒤 Spring에 `검색된 Bug들의 Issue 연결 정보 조회`만 요청한다. Spring은
`bugs`, `issue_bugs`, `issues`에서 다음 projection을 반환한다.

- `bug_id`
- `issue_id`
- Issue `title`, `summary`, `status`

### Spring 쓰기

`match_report`는 Agent 내부 판단이므로 Spring 호출이 없다. 판단 이후에는 하나의
판정 적용 API를 호출하지 않고 생명주기 명령을 분리한다.

- `AUTO_LINK`: `link_bug_to_issue`가 기존 Issue에 Bug 연결을 요청한다.
- `REVIEW`: Spring 도메인을 변경하지 않고 workflow 결과에 검토 필요 상태를 기록한다.
- `CREATE_NEW`: `create_issue`가 Issue 생성을 요청한다. Spring은 새 Issue에 Bug를
  함께 연결하고 생성된 `issue_id`를 반환한다.

각 API에서 Bug 연결과 Issue 집계 갱신은 Spring의 한 트랜잭션으로 실행한다.

### Agent 구조 변경 범위

```text
정규화 → 후보 검색 → 매칭 판단
                    ├─ create_issue → Issue 분석
                    ├─ link_bug_to_issue → 종료
                    └─ mark_match_for_review → 종료
```

Agent의 실질적인 구조 변경은 다음과 같다.

1. **Retrieval 원본 조회 단순화**

   기존에는 `Bug → 최신 BugOccurrence`를 조회해 색인했다. 변경 후에는 `Bug`
   자체를 정규화·색인한다. backfill SQL의 latest occurrence lateral join과
   Bug·Occurrence 소속 검증을 제거한다.

2. **Retrieval 문서의 중복 식별자 제거**

   `bug_retrieval_documents.bug_report_id`와 `bug_occurrences` FK를 제거하고
   `bug_id` 하나만 `bugs.id`를 참조한다. 문서 `version`과 active snapshot은 Bug
   수정·재정규화를 위해 유지한다.

   ```text
   bugs 1 ─ N bug_retrieval_documents 1 ─ N bug_embeddings
   ```

3. **판단과 Spring 생명주기 명령 분리**

   Agent 내부 `MatchDecision`은 분기에만 사용한다. Spring에는 판단 snapshot이나
   action enum을 보내지 않고 `Issue 생성` 또는 `기존 Issue에 Bug 연결`을 각각 요청한다.

다음 기능은 구조적으로 그대로 유지한다.

- exact·lexical·vector 검색과 결과 fusion
- 유사 Bug를 Issue별로 그룹화하고 후보 순위 계산
- Issue별 대표 Bug 선정
- Agent 내부 `AUTO_LINK`, `REVIEW`, `CREATE_NEW` 판단
- `issue_bugs`를 통한 Bug·Issue 연결
- 신규 Issue의 Issue Analysis 실행

결론적으로 Agent 그래프 재설계가 아니라, Retrieval persistence·backfill·Spring
adapter의 데이터 관계를 단순화하는 변경이다.

## Agent workflow 실행 생명주기

`analysis_jobs`를 Issue Analysis에만 한정하지 않고 `agent_workflow_runs`로
일반화한다. 하나의 row는 `clio_agent`가 수신한 요청 하나의 전체 실행을
나타낸다. Report Processing, Issue Analysis, Document Sync, Repository Sync에 같은
상태 계약을 적용한다.

```text
validate_request 성공
  → PENDING
route_request 후 선택된 workflow 실행 시작
  → RUNNING
선택된 분기의 마지막 필수 작업과 Spring 결과 적용 완료
  → COMPLETED
재시도 후에도 필수 작업을 끝내지 못함
  → FAILED
```

`validate_request`가 실패한 요청은 workflow run을 생성하지 않는다. 추후 운영상
필요하면 별도 요청 거부 로그를 두지만 workflow 상태와는 분리한다.

### 상태의 의미

- `PENDING`: 요청 계약 검증은 끝났지만 선택된 workflow의 실제 작업은 시작하지 않음.
- `RUNNING`: 원문 조회, Agent 판단, 하위 그래프, Spring 결과 적용을 포함한 전체 실행 중.
- `COMPLETED`: 선택된 분기의 마지막 필수 작업과 Spring의 결과 저장·적용이 모두 성공함.
- `FAILED`: 기술적 오류로 필수 작업을 마치지 못했고 허용된 재시도도 소진함.

`NEEDS_REVIEW`, `INSUFFICIENT_EVIDENCE`, `CREATE_NEW` 같은 값은 workflow 실패가 아니라
업무 결과다. Agent가 그 결론을 정상적으로 저장했다면 workflow는 `COMPLETED`다.

### Checkpoint 소유권

LangGraph checkpoint와 노드별 재개 정보는 Agent가 소유한다. Spring은 Agent의
노드 이름과 분기를 해석하지 않는다. 운영 표시가 필요하면 Agent가 보낸 최신
checkpoint JSON을 불투명한 값으로 저장한다.

### `agent_workflow_runs` 칼럼

| 칼럼 | 용도 |
|---|---|
| `id` | Spring 내부 workflow run ID |
| `request_id` | 멱등성 식별자. unique, 재시도 시 유지 |
| `request_type` | Agent root 요청 유형 |
| `project_id` | 대상 프로젝트 FK |
| `request_hash` | 같은 `request_id`의 다른 payload 재사용 방지 |
| `status` | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| `latest_checkpoint` | Agent가 보낸 불투명한 최신 진행 JSON, nullable |
| `result_snapshot` | 최종 업무 결과 JSON, nullable |
| `failure_code`, `failure_message` | 최종 실패 정보, nullable |
| `created_at` | `PENDING` 생성 시각 |
| `started_at` | `RUNNING` 전이 시각 |
| `completed_at` | `COMPLETED` 또는 `FAILED` 전이 시각, nullable |

Spring은 상태 전이와 unique 제약을 관리하고, Agent는 언제 checkpoint를 남길지와
선택된 분기의 마지막 필수 작업을 결정한다. `finalize_request`는 성공 경로의
마지막에서만 `COMPLETED`를 요청하고, 처리되지 않은 최종 예외는 `FAILED`를 요청한다.

### 분석 결과와의 관계

`analysis_jobs`는 제거하고, 분석 결과가 있는 workflow run만 `analysis_results`와
연결한다.

| 칼럼 | 용도 |
|---|---|
| `id` | 분석 결과 ID |
| `workflow_run_id` | 분석을 생성한 workflow run FK, unique |
| `issue_id` | 분석 대상 Issue FK |
| `previous_analysis_result_id` | 재분석 시 이전 결과 FK, nullable |
| `status` | `COMPLETED`, `INSUFFICIENT_EVIDENCE`, `NEEDS_REVIEW` |
| `result_snapshot` | Agent의 immutable IssueAnalysis JSON |
| `created_at` | 저장 시각 |

workflow run의 `COMPLETED`는 원인을 찾았다는 뜻이 아니라, 선택된 전체 절차와
결과 적용을 정상적으로 끝냈다는 뜻이다.

### workflow 멱등성과 도메인 무결성

`agent_workflow_runs.request_id` unique와 `request_hash`로 요청 중복을 보호한다. 또한
workflow 멱등성만 믿지 않고 `issue_bugs.bug_id` unique, 낙관적 락, 조건부
상태 전이 같은 도메인 불변식을 함께 적용한다.

## 상태 전이

Bug는 다음 전이를 허용한다.

```text
NEW → ANALYZING | TRIAGED | IGNORED
ANALYZING → NEW | TRIAGED | IGNORED
TRIAGED → ANALYZING | RESOLVED | IGNORED
RESOLVED → TRIAGED
IGNORED → NEW
```

각 Bug가 수집 오류 한 건이므로 새로운 발생은 기존 Bug의 상태를 되돌리지 않고
새 Bug row로 생성한다.

Issue는 다음 전이를 허용한다.

```text
OPEN → IN_PROGRESS | CLOSED
IN_PROGRESS → OPEN | RESOLVED | CLOSED
RESOLVED → IN_PROGRESS | CLOSED
CLOSED → OPEN
```

허용하지 않는 전이는 `409 Conflict`다. 상태 전이와 priority, severity, assignee 변경은
external API를 통해서만 수행한다.
