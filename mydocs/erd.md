# clio ERD

clio는 **여러 경로로 들어온 버그 리포트를 모아 중복을 합치고, 같은 원인끼리 이슈로 묶은 뒤,
코드·문서·과거 이력을 근거로 LLM이 분석해주는 서비스**다.

DB 테이블 22개를 두 장으로 나눠 그렸다.

| | 하는 일 |
|---|---|
| **1. Spring Boot API 서버** | 버그 수집, 이슈 조회, 프로젝트·저장소 설정, 인증 |
| **2. Python AI 서버** | 코드 인덱싱, 임베딩, 벡터 검색, LLM 분석 |

`bugs` · `issues` 처럼 **양쪽에 모두 나오는 테이블이 있다.** 한쪽 것이 아니라 양쪽 다 다루는 테이블이라는
뜻이고, 각 장에는 그 서버가 쓰는 컬럼만 적어서 같은 테이블도 장마다 보이는 면이 다르다.

컬럼 타입은 JPA 엔티티 기준 표기다 (`vector`는 pgvector, enum은 실제 DDL에서 varchar).

---

## 1. Spring Boot API 서버

### 테이블이 하는 일

- **`projects`** — 모든 것의 루트. 나머지 테이블 대부분이 `project_id`를 들고 있다.
- **`project_sources`** — 프로젝트에 연결된 Git 저장소(URL·브랜치). `repository_credentials`에 접근 토큰이 1:1로 붙는다.
- **`project_contexts`** — 프로젝트 배경 지식 문서(설계 문서, 규칙 등). 분석할 때 참고 자료로 쓰인다.
- **`bugs`** — 지문(`fingerprint`)으로 중복을 합친 버그 하나. 같은 에러가 100번 나도 행은 하나고 `occurrence_count`가 오른다.
- **`bug_occurrences`** — 실제 발생 이벤트 원본. 들어온 payload를 그대로 보관한다.
- **`bug_priority_feedbacks`** — 우선순위를 바꾼 이력.
- **`issues`** — 같은 원인으로 판단된 버그들의 묶음. 담당자·상태·위험도를 관리하는 실제 작업 단위다.
- **`issue_bugs`** — 어떤 버그가 어떤 이슈에 묶였는지. `grouped_by`에 묶은 방식이 남는다.
- **`issue_branches`** — 이슈를 고치는 브랜치·PR 추적.
- **`api_keys`** — MCP 클라이언트용 프로젝트별 API 키.
- **`admin_accounts`** — 관리자 로그인 계정.
- **`analysis_jobs` / `analysis_results`** — 분석 요청 접수와 결과 조회. 실행은 2번이 한다.

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

`analysis_results`는 화면 조회에 쓰는 필드만 적었다. 전체 컬럼은 2번에 있다.

---

## 2. Python AI 서버

### 테이블이 하는 일

- **`code_files` / `code_symbols`** — 저장소를 훑어 만든 파일 목록과, 파일이 선언한 클래스·메서드 목록.
- **`code_chunks`** — 코드를 검색 단위로 쪼갠 조각 + 그 벡터. "이 버그와 관련된 코드" 검색이 여기서 일어난다.
- **`project_context_chunks`** — 배경 문서(`project_contexts`)를 같은 방식으로 쪼갠 조각 + 벡터.
- **`bug_embeddings`** — 버그 텍스트의 벡터. 비슷한 버그를 찾아 이슈로 묶을 때 쓴다.
- **`decision_memories`** — 과거에 내린 판단·결정 기록 + 벡터. 분석할 때 "예전에 이렇게 정했다"를 근거로 끌어온다.
- **`llm_providers` / `llm_models`** — 어떤 LLM 업체의 어떤 모델을 쓸지. `purpose`로 분석용·임베딩용을 나눈다.
- **`analysis_jobs`** — 분석 잡. 상태를 진행시키며 실행한다.
- **`analysis_results`** — 분석 결과. 점수·요약·수정 제안과, 근거로 쓴 코드·유사 이슈를 jsonb로 남긴다.

벡터를 만들 때 원본 테이블(`bugs`, `code_files`, `project_contexts`)을 읽는다.

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

---

## 테이블 목록

**API 서버에만 (4)** — `bug_priority_feedbacks`, `issue_branches`, `api_keys`, `admin_accounts`

**AI 서버에만 (8)** — `code_files`, `code_chunks`, `code_symbols`, `project_context_chunks`,
`bug_embeddings`, `decision_memories`, `llm_providers`, `llm_models`

**양쪽 (10)** — `projects`, `project_sources`, `repository_credentials`, `project_contexts`,
`bugs`, `bug_occurrences`, `issues`, `issue_bugs`, `analysis_jobs`, `analysis_results`

## 데이터가 흐르는 순서

1. 버그 리포트가 들어온다 → `bug_occurrences`에 원본 적재, 지문으로 `bugs` 행을 찾거나 만든다
2. 버그 텍스트를 임베딩한다 → `bug_embeddings`
3. 비슷한 버그끼리 묶는다 → `issues` + `issue_bugs`
4. 저장소를 인덱싱해둔다 → `code_files` → `code_symbols` · `code_chunks`
5. 분석을 건다 → `analysis_jobs` 생성
6. 벡터 검색으로 근거를 모은다 → `code_chunks` · `project_context_chunks` · `decision_memories`
7. LLM이 분석한다 → `analysis_results`에 점수·요약·수정 제안 기록
