# 03. Decisions — Agent Graph 연동 API 구현

## 목적

Agent 연동 API 구현에 영향을 준 D1~D8의 선택, 핵심 근거와 결과를 한 문서에서 확인한다.
모든 결정은 확정·반영 완료 상태다.

## D1. Agent 소유 엔티티 제거

- 결정일: 2026-08-09
- 선택: Agent가 소유하는 AI/RAG JPA 엔티티를 Server에서 제거한다.
- 근거: 사용하지 않는 엔티티도 Hibernate 관리 대상이며, Agent Alembic schema와 충돌할 수 있다.
- 제외: 엔티티를 남기고 사용하지 않는 안은 테이블 소유권 충돌을 해결하지 못한다.
- 결과: `BugEmbedding`, Code Memory, Decision Memory, `ProjectContextChunk` 매핑을 제거했다.
  Server는 업무 데이터만, Agent는 retrieval·embedding·PCM 데이터를 소유한다.

## D2. Agent 연동 JSON과 ID

- 결정일: 2026-08-09
- 선택: Java DTO는 camelCase, Agent 연동 JSON만 snake_case를 사용한다.
- 근거: Java 명명 규칙과 Agent Pydantic 계약을 모두 지키며 전역 API 호환성도 유지한다.
- 제외: Server 전체 JSON 변경은 기존 API에 영향을 주고, Agent adapter 변환은 불필요한 계층을 만든다.
- 결과: Agent DTO에만 `SnakeCaseStrategy`를 적용하고 ID는 양수 JSON number로 정했다.

## D3. 정규화·분석 결과 저장

- 결정일: 2026-08-09
- 선택: `NormalizedReport`는 Agent만 저장하고, Server는 분석별 전체 `IssueAnalysis` snapshot을 저장한다.
- 근거: 정규화 결과를 양쪽에 저장하면 active version이 갈리고, 분석 JSON 구조를 컬럼으로 나누면
  Evidence→Finding→Hypothesis 관계가 손실될 수 있다.
- 제외: 최신 결과 덮어쓰기는 과거 분석 재현과 재분석 비교가 불가능하다.
- 결과: 재분석마다 새 Job과 immutable JSONB Result를 생성한다.

## D4. Write API 멱등성

- 결정일: 2026-08-10
- 선택: body의 `request_id`를 `(project, operation, request_id)` 범위 멱등 키로 사용한다.
- 근거: Agent 실행 ID를 그대로 추적하고 timeout 재시도에도 최초 응답을 재현할 수 있다.
- 제외: unique constraint만으로는 같은 키의 payload 충돌과 응답 재생을 처리할 수 없다.
- 결과: 동일 키·동일 payload는 기존 응답을 반환하고, 다른 payload는 `409`를 반환한다.
  사용자용 BugReport 수집 API에는 적용하지 않는다.

## D5. Issue 매칭 결정 적용

- 결정일: 2026-08-10
- 선택: `AUTO_LINK`는 기존 Issue 연결, `CREATE_NEW`는 생성 후 연결, `REVIEW`는 기록만 한다.
- 근거: 검토 전에 관계를 바꾸거나 기존 연결을 자동 이동하면 통계와 과거 분석의 의미가 달라진다.
- 제외: REVIEW 임시 연결과 기존 연결 자동 이동은 안전하지 않다.
- 결과: 다른 Issue에 연결된 Bug의 이동은 `409`, 같은 연결 재적용은 기존 관계를 반환한다.

## D6. AnalysisJob 생명주기

- 결정일: 2026-08-10
- 선택: Server가 Job을 먼저 만들고 Agent에 ID를 전달하며, 결과 저장과 완료를 한 트랜잭션으로 묶는다.
- 근거: Agent 계약은 실행 전 `analysis_job_id`가 필요하고 결과 없는 완료 상태를 허용하면 안 된다.
- 제외: PATCH의 임의 상태 전이는 결과와 상태 사이의 불일치를 만든다.
- 결과: `PENDING → RUNNING`, `PENDING/RUNNING → FAILED`만 PATCH로 허용한다.
  RUNNING Job의 결과 PUT만 `COMPLETED`로 전이하며, 재분석은 완료된 이전 snapshot을 참조한다.

## D7. PCM 소유권

- 결정일: 2026-08-10
- 선택: Server는 PCM을 모르고 Client와 Agent를 연결하는 역할만 한다.
- 근거: PCM revision, Knowledge commit, Repository snapshot은 Agent가 실행 시 직접 고정한다.
- 제외: Server 복제는 두 저장소의 최신값 불일치와 Agent schema 결합을 만든다.
- 결과: PCM sync·snapshot API 3개와 관련 operation type을 범위에서 제거했다.

## D8. 동일 Bug 판단

- 결정일: 2026-08-10
- 선택: Agent가 BugReport를 기존 Bug에 묶을지 판단하고 Server는 그 결정을 반영한다.
- 근거: 문자열 fingerprint는 표현이 다른 동일 현상을 나누거나 다른 현상을 합칠 수 있다.
- 제외: Server fingerprint 자동 연결과 모든 Report의 개별 Bug 생성은 의미 기반 grouping을 지원하지 못한다.
- 결과: Agent가 `MATCH_EXISTING`, `CREATE_NEW`, `REVIEW`를 반환한다. Server는 발생 관계만
  트랜잭션으로 변경하고 fingerprint를 제거했다. 기존 Bug에 Issue가 있으면 Issue matching은 생략한다.

## 최종 경계

```text
Client → Server: 요청과 업무 데이터
Server → Agent: 실행에 필요한 식별자와 원본
Agent → Server: Bug grouping, Issue matching, 분석 결과
Agent 내부: 정규화, 검색, PCM, Repository snapshot
```

Agent Graph가 Server를 호출하는 7개 연동 API는 `/internal/api/v1` 아래에만 노출한다.
사용자용 `/api/v1`에는 Agent 원본 조회·결정 반영·분석 작업 API를 노출하지 않는다.
