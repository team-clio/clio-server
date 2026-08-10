# 03. Decisions — Agent Graph 연동 API 구현

## D1. Agent 소유 엔티티 제거 변경의 선행 반영

- 결정일: 2026-08-09
- 상태: 확정·반영 완료
- 선택: **A — `refactor/drop-ai-only-entities` 변경을 이번 브랜치에 반영**

### 검토한 대안

- A. Agent가 소유하는 AI/RAG 데이터의 JPA 엔티티를 Server에서 제거한다.
- B. 엔티티를 남기되 이번 API 구현에서는 사용하지 않는다.

### 결정 근거

- 사용하지 않는 `@Entity`도 Hibernate entity scan과 `ddl-auto=update`의 관리 대상이다.
- Server의 기존 `BugEmbedding`은 `bug_id`와 Java `float[]`를 사용하지만, Agent의 최신
  `bug_embeddings`는 `retrieval_document_id`와 PostgreSQL `vector`를 사용한다.
- Agent Alembic은 기존 JPA형 `bug_embeddings`를 `legacy_bug_embeddings`로 보존한 뒤 새 스키마를
  생성하므로, Server의 오래된 매핑을 남기면 같은 테이블의 소유권과 스키마가 충돌할 수 있다.
- Server는 업무 데이터와 Agent 결과 참조를 소유하고, retrieval·embedding·PCM 상세 데이터는
  Agent가 소유하는 경계가 현재 Agent 구현과 일치한다.

### 반영 범위

- 제거: `BugEmbedding`
- 제거: `CodeChunk`, `CodeChunkType`, `CodeFile`, `CodeSymbol`, `CodeSymbolType`
- 제거: `DecisionMemory`
- 제거: `ProjectContextChunk`

### 영향

- Java 매핑만 제거하며 이 변경 자체가 기존 DB 테이블이나 데이터를 삭제하지는 않는다.
- Server가 해당 데이터를 필요로 하면 Agent 연동 API 또는 명시적인 읽기 계약을 사용해야 한다.
- 다음 결정: D2 Agent 연동 JSON과 ID 계약

## D2. Agent 연동 JSON과 ID 계약

- 결정일: 2026-08-09
- 상태: 확정
- 선택: **Java DTO는 camelCase, Agent 연동 JSON은 DTO 경계에서 snake_case**

### 적용 방식

- Java record와 필드 이름은 Spring 코드 전반에서 `camelCase`로 통일한다.
- Agent 연동 요청·응답 DTO에만 Jackson `SnakeCaseStrategy`를 적용한다.
- Server가 Agent에 보내는 JSON은 `snake_case`로 직렬화한다.
- Server는 Agent가 보낸 `snake_case` JSON을 같은 DTO로 역직렬화한다.
- 기존 사용자용 버그·이슈 API DTO는 현재 `camelCase` 형식을 유지한다.
- DB 리소스 ID는 Java `Long`과 양수 JSON number를 사용한다. Git commit과 revision은 문자열이다.

### 결정 근거

- Java 내부 명명 규칙을 바꾸지 않으면서 최신 Agent Pydantic 계약과 동일한 JSON을 제공할 수 있다.
- Agent adapter에서 전체 key를 다시 변환하지 않아도 된다.
- 전역 ObjectMapper 설정을 변경하지 않으므로 기존 API의 호환성을 해치지 않는다.

### 검토 후 제외한 방식

- 모든 Server JSON을 `snake_case`로 변경: 기존 사용자 API까지 영향을 받으므로 제외했다.
- Agent가 `camelCase`를 별도 파싱: Agent의 `extra=\"forbid\"` 모델 앞에 변환 계층이 필요해져 제외했다.

### 영향

- 이후 생성하는 Agent 연동 DTO마다 snake_case 직렬화 계약 테스트를 작성한다.
- 다음 결정: D3 정규화 리포트와 분석 결과 저장 방식

## D3. 정규화 리포트와 분석 결과 저장 방식

- 결정일: 2026-08-09
- 상태: 확정·반영 완료
- 선택: **기존 Agent의 snapshot·소유권 결정을 계승**

### 기존 결정

- `NormalizedReport`는 Agent가 append-only JSON snapshot으로 보존한다.
- `bug_retrieval_documents`와 `bug_embeddings`는 Python/Alembic이 소유한다.
- 재분석은 기존 결과를 수정하지 않고 새 `analysis_job_id`의 전체 `IssueAnalysis` snapshot을 만든다.
- PCM 상세 이력과 검색 인덱스도 Agent가 소유한다.

### 적용 방식

- Server는 `NormalizedReport`를 별도 테이블이나 `Bug` 컬럼에 복제하지 않는다.
- Server의 `AnalysisResult`는 분석 작업당 하나의 완전한 `IssueAnalysis` JSONB snapshot을 보관한다.
- 결과 상태와 이전 분석 작업 참조만 별도 컬럼으로 두며 project와 issue는 `AnalysisJob` 관계로 조회한다.
- `AnalysisResult`는 생성 후 내용을 덮어쓰지 않는다. 재분석은 새 Job과 새 Result를 생성한다.
- 기존 Server의 점수·요약·추천별 컬럼은 최신 `IssueAnalysis` 계약과 일치하지 않아 JPA 모델에서 제거한다.

### 결정 근거

- 정규화 결과를 양쪽에서 저장하면 active version과 소유권이 갈라진다.
- 완전한 JSON snapshot은 Evidence→Finding→Hypothesis 참조 구조를 손실 없이 보존한다.
- 분석별 immutable snapshot은 과거 분석 재현과 `revision_summary` 해석에 필요하다.
- 조회에 필요한 관계는 `AnalysisJob`이 이미 project, issue, trigger bug를 보유한다.

### API 영향

- 매칭 결과 API는 정규화 본문을 Server 정본으로 저장하지 않고 매칭 결정과 식별 참조를 기록한다.
- 분석 문맥 API는 Server의 Issue·Bug·Report 식별 문맥을 제공하고, Agent가 자기 active 정규화 snapshot을 결합한다.
- 다음 결정: D4 결과 반영 API의 멱등성

## D4. 결과 반영 API의 멱등성

- 결정일: 2026-08-10
- 상태: 확정·반영 완료
- 선택: **Agent 요청 body의 `requestId`를 공통 멱등 키로 사용**

### 적용 방식

- Agent 연동 write DTO는 `requestId`를 필수로 받으며 JSON에서는 `request_id`로 표현한다.
- `(project, operationType, requestId)`를 고유 키로 저장한다.
- 최초 처리 시 canonical JSON 요청 해시, HTTP 상태, 응답 snapshot을 함께 저장한다.
- 동일 키와 동일 payload의 재처리는 도메인 로직을 다시 실행하지 않고 기존 응답을 반환한다.
- 동일 키와 다른 payload가 들어오면 `409 Conflict` 대상 예외를 발생시킨다.
- 프로젝트 행에 비관적 쓰기 잠금을 걸어 동일 프로젝트의 최초 처리 경쟁을 직렬화한다.

### 결정 근거

- Agent Root Graph와 PCM은 이미 `request_id`를 source event 식별자로 사용한다.
- 별도 HTTP 헤더로 옮기는 adapter 없이 실행 ID를 end-to-end로 추적할 수 있다.
- 결과 응답까지 저장하면 timeout 뒤 재시도에도 최초 처리 결과를 동일하게 돌려줄 수 있다.
- 리소스 unique constraint만으로는 같은 요청의 payload 충돌과 응답 복원이 불가능하다.

### 범위

- 적용: 매칭 결정, 분석 작업 생성·결과·상태 API
- D7에서 PCM 관련 동기화 API가 Server 범위에서 제외되어 해당 operation type도 제거했다.
- 비적용: 사용자용 Bug Report 수집 API
- 다음 결정: D5 매칭 결정 적용 규칙

## D5. 매칭 결정 적용 규칙

- 결정일: 2026-08-10
- 상태: 확정·반영 완료
- 선택: **검토 전에는 연결하지 않고 기존 연결을 자동 이동하지 않는다**

### 적용 방식

- `AUTO_LINK`: 지정된 기존 Issue에 Bug를 연결한다.
- `REVIEW`: 후보와 결정 snapshot만 기록하고 `IssueBug`를 만들지 않는다.
- `CREATE_NEW`: Bug의 원문 정보를 사용해 새 Issue를 만들고 Bug를 연결한다.
- 같은 Issue에 이미 연결된 `AUTO_LINK`는 기존 연결을 반환하고 count를 다시 증가시키지 않는다.
- 다른 Issue에 이미 연결된 Bug를 자동 연결하거나 새 Issue로 옮기려 하면 `409 Conflict`로 거부한다.
- Agent의 전체 MatchDecision은 감사·재현을 위해 JSON snapshot으로 보존한다.

### 결정 근거

- `REVIEW`는 사람 판단이 끝나지 않은 상태이므로 업무 관계를 미리 변경하면 안 된다.
- 기존 연결을 자동 이동하면 과거 분석과 Issue 통계의 의미가 조용히 바뀐다.
- 동일 연결 재사용은 다른 `requestId`로 같은 결론이 다시 도착해도 중복 관계를 만들지 않는다.

### 상태와 집계

- 실제 연결이 생성되면 Bug를 `TRIAGED`로 변경한다.
- 새 연결일 때만 Issue의 Bug 수와 occurrence 수를 증가시킨다.
- 다음 결정: D6 분석 작업 상태 전이와 결과 완료 처리
