# clio ERD (JPA 엔티티 기준)

`@Entity` 22개를 **어느 서버가 그 테이블을 신경 쓰는가** 기준으로 두 장으로 나눠 그렸다.
이해용이므로 **양쪽에 중복 등장하는 테이블이 있다** (`bugs` · `issues` · `project_contexts` 등).
한쪽 소유라는 뜻이 아니라, 양쪽 다 봐야 하는 테이블이라는 뜻이다.
각 장에는 그 서버가 실제로 쓰는 컬럼만 적었으므로, 같은 테이블도 장마다 보이는 면이 다르다.

컬럼 타입은 JPA 필드 기준 표기다 (`float[]` → `vector`, enum은 `EnumType.STRING`이라 실제 DDL은 varchar).

> 현재 리포는 **엔티티 스켈레톤 단계**다. `JpaRepository` 구현이 0개이고,
> `@RestController`는 아래 3개뿐이다. 그 밖의 분리선은 아직 코드에 없는 설계상의 구분이다.
>
> | 컨트롤러 | 엔드포인트 | 닿는 테이블 |
> |---|---|---|
> | `BugReportController` | `POST·GET /api/v1/projects/{id}/bug-reports` | `bugs`, `bug_occurrences` |
> | `IssueController` | `GET /api/v1/projects/{id}/issues`, `/{issueId}`, `/stats` | `issues`, `issue_bugs` |
> | `LlmSettingsController` | `GET·PATCH·POST /api/v1/system/llm/*` | `llm_providers`, `llm_models` |

---

## 1. Spring Boot API 서버가 신경 쓸 테이블

프로젝트/저장소 설정, 버그 수집·집계, 이슈 관리, 인증, 분석 요청 접수와 결과 조회.

`code_files` · `code_symbols` · `code_chunks` 는 **여기 없다.** API 응답 어디에도 코드 인덱스가 나오지
않고(`IssueDetailResponse` 확인), `code` 패키지에는 엔티티 외에 컨트롤러·서비스·리포지토리가 없다.
전부 2번 소관이다.

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_SOURCES : has
    PROJECTS ||--o{ PROJECT_CONTEXTS : has
    PROJECTS ||--o{ BUGS : collects
    PROJECTS ||--o{ ISSUES : groups
    PROJECTS ||--o{ API_KEYS : issues
    PROJECTS ||--o{ ANALYSIS_JOBS : requests

    PROJECT_SOURCES ||--o| REPOSITORY_CREDENTIALS : "1:1 token"
    PROJECT_SOURCES ||--o{ ISSUE_BRANCHES : "branch on"

    BUGS ||--o{ BUG_OCCURRENCES : "raw events"
    BUGS ||--o{ BUG_PRIORITY_FEEDBACKS : "priority log"

    ISSUES ||--o{ ISSUE_BUGS : contains
    BUGS   ||--o{ ISSUE_BUGS : "grouped into"
    ISSUES ||--o{ ISSUE_BRANCHES : "fix branch"

    BUGS   ||--o{ ANALYSIS_JOBS : "target(nullable)"
    ISSUES ||--o{ ANALYSIS_JOBS : "target(nullable)"
    ANALYSIS_JOBS ||--o| ANALYSIS_RESULTS : "결과 조회"

    PROJECTS {
        bigint id PK
        varchar name
        varchar description
        enum status "ProjectStatus"
        instant created_at
        instant updated_at
    }
    PROJECT_SOURCES {
        bigint id PK
        bigint project_id FK
        varchar repo_url
        varchar target_branch
        varchar root_path
        enum sync_status "ProjectSourceSyncStatus"
        instant last_synced_at
        instant created_at
        instant updated_at
    }
    REPOSITORY_CREDENTIALS {
        bigint id PK
        bigint project_source_id FK "UNIQUE 1:1"
        varchar token_secret_ref
        instant created_at
        instant updated_at
    }
    PROJECT_CONTEXTS {
        bigint id PK
        bigint project_id FK
        varchar title
        enum type "ProjectContextType"
        text content
        varchar source_path
        varchar source_url
        instant created_at
        instant updated_at
    }
    BUGS {
        bigint id PK
        bigint project_id FK
        varchar fingerprint "UK(project_id,fingerprint)"
        varchar fingerprint_source
        varchar title
        text description
        enum source "BugSource"
        varchar reporter_name
        varchar error_type
        text normalized_message
        varchar top_application_frame
        int occurrence_count
        enum status "BugStatus"
        enum severity "Severity"
        instant first_seen_at
        instant last_seen_at
        instant created_at
        instant updated_at
    }
    BUG_OCCURRENCES {
        bigint id PK
        bigint bug_id FK
        enum source "BugSource"
        jsonb raw_payload
        instant occurred_at
        instant created_at
    }
    BUG_PRIORITY_FEEDBACKS {
        bigint id PK
        bigint bug_id FK
        enum previous_priority "Priority"
        enum new_priority "Priority"
        text reason
        instant created_at
    }
    ISSUES {
        bigint id PK
        bigint project_id FK
        varchar title
        text summary
        enum status "IssueStatus"
        enum priority "Priority"
        enum severity "Severity"
        int risk_score
        varchar assignee_name
        decimal ai_confidence "5,4"
        int bug_count
        int occurrence_count
        instant first_seen_at
        instant last_seen_at
        instant created_at
        instant updated_at
    }
    ISSUE_BUGS {
        bigint id PK
        bigint issue_id FK "UK(issue_id,bug_id)"
        bigint bug_id FK
        decimal confidence
        enum grouped_by "IssueGroupingMethod"
        instant created_at
    }
    ISSUE_BRANCHES {
        bigint id PK
        bigint issue_id FK
        bigint project_source_id FK
        varchar branch_name
        varchar base_branch
        enum merge_status "MergeStatus"
        varchar pull_request_url
        instant last_checked_at
        instant created_at
        instant updated_at
    }
    API_KEYS {
        bigint id PK
        bigint project_id FK
        varchar name
        varchar key_prefix
        varchar key_hash "UK"
        boolean revoked
        instant last_used_at
        instant created_at
    }
    ADMIN_ACCOUNTS {
        bigint id PK
        varchar username "UK"
        varchar password_hash
        boolean must_change_password
        instant created_at
        instant updated_at
    }
    ANALYSIS_JOBS {
        bigint id PK
        bigint project_id FK
        bigint bug_id FK "nullable"
        bigint issue_id FK "nullable"
        bigint llm_model_id FK "nullable"
        enum status "AnalysisJobStatus"
        enum search_mode "SearchMode"
        instant created_at
        instant started_at
        instant completed_at
        varchar failure_reason
    }
    ANALYSIS_RESULTS {
        bigint id PK
        bigint job_id FK "UNIQUE 1:1"
        int difficulty_score
        int risk_score
        varchar issue_type
        text summary
        text rationale
        text recommended_fix
        jsonb related_code
        jsonb flows
        jsonb similar_issues
    }
```

`llm_providers` · `llm_models` 는 `LlmSettingsController`(설정 CRUD)가 다루지만, 모델을 실제로 호출하는
쪽이 파이썬이라 2번에 그렸다. `analysis_results` 는 화면 조회에 쓰는 필드만 적었다 — 전체 컬럼은 2번에 있다.

---

## 2. Python AI 서버가 신경 쓸 테이블

저장소 clone, 코드 인덱싱·청킹, 임베딩, 벡터 검색, LLM 분석 실행.

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_SOURCES : "clone 대상"
    PROJECTS ||--o{ CODE_FILES : indexes
    PROJECTS ||--o{ PROJECT_CONTEXTS : has
    PROJECTS ||--o{ BUGS : collects
    PROJECTS ||--o{ ISSUES : groups
    PROJECTS ||--o{ DECISION_MEMORIES : remembers
    PROJECTS ||--o{ ANALYSIS_JOBS : runs

    PROJECT_SOURCES ||--o| REPOSITORY_CREDENTIALS : "1:1 token"

    CODE_FILES ||--o{ CODE_CHUNKS : "chunk+embed"
    CODE_FILES ||--o{ CODE_SYMBOLS : declares
    PROJECT_CONTEXTS ||--o{ PROJECT_CONTEXT_CHUNKS : "chunk+embed"

    BUGS ||--o{ BUG_EMBEDDINGS : embeds
    BUGS ||--o{ BUG_OCCURRENCES : "분석 입력"
    BUGS   ||--o{ ISSUE_BUGS : "grouped into"
    ISSUES ||--o{ ISSUE_BUGS : contains

    LLM_PROVIDERS ||--o{ LLM_MODELS : offers
    LLM_MODELS ||--o{ ANALYSIS_JOBS : "used by"
    BUGS   ||--o{ ANALYSIS_JOBS : "target(nullable)"
    ISSUES ||--o{ ANALYSIS_JOBS : "target(nullable)"
    ANALYSIS_JOBS ||--o| ANALYSIS_RESULTS : produces

    PROJECTS {
        bigint id PK
        varchar name
        varchar description
        enum status "ProjectStatus"
    }
    PROJECT_SOURCES {
        bigint id PK
        bigint project_id FK
        varchar repo_url
        varchar target_branch
        varchar root_path
        enum sync_status "ProjectSourceSyncStatus"
        instant last_synced_at
    }
    REPOSITORY_CREDENTIALS {
        bigint id PK
        bigint project_source_id FK "UNIQUE 1:1"
        varchar token_secret_ref
    }
    CODE_FILES {
        bigint id PK
        bigint project_id FK
        varchar path "UK(project_id,path)"
        varchar file_name
        varchar language
        boolean test
        bigint size_bytes
        varchar content_hash
        instant last_modified_at
        instant indexed_at
    }
    CODE_CHUNKS {
        bigint id PK
        bigint file_id FK
        varchar path
        varchar symbol_name
        varchar role
        enum chunk_type "CodeChunkType"
        int start_line
        int end_line
        text content
        varchar content_hash
        vector embedding
        varchar embedding_model
        int embedding_dimension
        instant embedded_at
    }
    CODE_SYMBOLS {
        bigint id PK
        bigint file_id FK
        varchar name
        varchar qualified_name
        varchar signature
        enum type "CodeSymbolType"
        varchar role
        varchar package_name
        int start_line
        int end_line
        text annotations
    }
    PROJECT_CONTEXTS {
        bigint id PK
        bigint project_id FK
        varchar title
        enum type "ProjectContextType"
        text content
    }
    PROJECT_CONTEXT_CHUNKS {
        bigint id PK
        bigint context_id FK
        int chunk_index
        text content
        vector embedding
        varchar embedding_model
        int embedding_dimension
        instant embedded_at
    }
    BUGS {
        bigint id PK
        bigint project_id FK
        varchar fingerprint
        varchar title
        text description
        varchar error_type
        text normalized_message
        varchar top_application_frame
        int occurrence_count
        enum status "BugStatus"
        enum severity "Severity"
    }
    BUG_OCCURRENCES {
        bigint id PK
        bigint bug_id FK
        jsonb raw_payload
        instant occurred_at
    }
    BUG_EMBEDDINGS {
        bigint id PK
        bigint bug_id FK
        vector embedding
        varchar embedding_model
        int embedding_dimension
        instant embedded_at
    }
    ISSUES {
        bigint id PK
        bigint project_id FK
        varchar title
        text summary
        int risk_score
        decimal ai_confidence "5,4"
        int bug_count
        int occurrence_count
    }
    ISSUE_BUGS {
        bigint id PK
        bigint issue_id FK "UK(issue_id,bug_id)"
        bigint bug_id FK
        decimal confidence
        enum grouped_by "IssueGroupingMethod"
    }
    DECISION_MEMORIES {
        bigint id PK
        bigint project_id FK
        varchar title
        text body
        vector embedding
        varchar embedding_model
        int embedding_dimension
        instant embedded_at
    }
    LLM_PROVIDERS {
        bigint id PK
        varchar name
        enum provider_type "LlmProviderType"
        varchar base_url
        varchar api_key_secret_ref
        boolean enabled
    }
    LLM_MODELS {
        bigint id PK
        bigint provider_id FK
        varchar name
        enum purpose "LlmModelPurpose"
        int context_window
        int embedding_dimension
        boolean default_model
        boolean enabled
    }
    ANALYSIS_JOBS {
        bigint id PK
        bigint project_id FK
        bigint bug_id FK "nullable"
        bigint issue_id FK "nullable"
        bigint llm_model_id FK "nullable"
        enum status "AnalysisJobStatus"
        enum search_mode "SearchMode"
        instant started_at
        instant completed_at
        varchar failure_reason
    }
    ANALYSIS_RESULTS {
        bigint id PK
        bigint job_id FK "UNIQUE 1:1"
        int difficulty_score
        int risk_score
        varchar issue_type
        varchar keywords
        varchar domains
        text summary
        text rationale
        text recommended_fix
        text recommended_tests
        jsonb related_code
        jsonb flows
        jsonb similar_issues
        jsonb related_decisions
        jsonb evidence_warnings
        instant created_at
    }
```

`admin_accounts` · `api_keys` · `issue_branches` · `bug_priority_feedbacks` 는 여기 없다. 분석 파이프라인이
읽을 일이 없다.

---

## 읽는 포인트

- **`projects`가 양쪽 모두의 루트다.** 8개 테이블이 직접 `project_id`를 들고 있다.
- **임베딩 저장 위치가 두 갈래다** — `bug_embeddings`만 별도 테이블(1:N)이고,
  `code_chunks` · `project_context_chunks` · `decision_memories`는 엔티티 내부 컬럼(1:1)이다.
  Bug만 모델 교체 시 복수 벡터를 들 수 있는 구조인데, 의도한 비대칭인지 확인이 필요하다.
- **`analysis_jobs.bug_id` · `issue_id`는 둘 다 nullable** — 버그 단위/이슈 단위 분석을 한 테이블로 받는다.
  DB 제약으로 "둘 중 정확히 하나"가 강제되지 않아, 둘 다 null이거나 둘 다 채워진 행이 들어갈 수 있다.
- **`admin_accounts`는 어디와도 연결되지 않는다.** 이슈 담당자는 FK가 아니라 `issues.assignee_name` 문자열이다.
