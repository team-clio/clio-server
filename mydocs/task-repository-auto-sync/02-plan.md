# Repository 등록 후 자동 동기화 구현 계획

## 목표

Repository 등록 트랜잭션이 완료되면 `clio_agent`에 `repository_added`를 제출하고, 비동기 실행
결과를 `project_sources`의 상태로 반영한다. 작업 배경과 범위는 [01-overview.md](01-overview.md)를
따른다.

## 구현 단계

### 1. 동기화 lifecycle 계약 정의

- `PENDING → SYNCING → SYNCED | FAILED`의 허용 전이를 엔티티 메서드로 제한한다.
- 현재 실행과 과거 callback을 구분할 실행 식별자를 정의한다.
- 성공 시 `last_synced_at`, 실패 시 추적 가능한 오류 정보를 저장할 위치를 정한다.
- Repository 등록 응답과 비동기 동기화 결과의 의미를 분리한다.

### 2. Repository 등록 이벤트 발행

- Repository 저장이 완료되면 식별자만 담은 domain event를 발행한다.
- `AFTER_COMMIT` listener가 커밋된 Repository를 다시 조회해 Agent 요청 payload를 만든다.
- 등록 트랜잭션 안에서는 네트워크 호출이나 상태 callback을 처리하지 않는다.

### 3. Agent client 확장

- `ClioAgentClient`에 `repository_added` 제출 메서드를 추가한다.
- 기존 `POST /runs`와 `assistant_id=clio_agent` 계약을 재사용한다.
- project ID, repository ID, HTTPS URL, 대상 branch를 Agent 계약에 맞춰 직렬화한다.
- 결정한 완료 확인 방식에 필요한 webhook 또는 run ID를 함께 처리한다.

### 4. 제출 결과와 상태 전이 연결

- 제출 직전에 Repository를 `SYNCING`으로 전이한다.
- Agent가 요청을 수락한 것과 동기화를 완료한 것을 구분한다.
- 제출 자체가 실패하면 Repository 등록은 보존하고 상태를 결정한 실패 상태로 전이한다.
- 동일 등록 이벤트가 재처리돼도 중복 실행이나 상태 역행이 발생하지 않게 한다.

### 5. Agent 완료 결과 수신

- 결정한 callback 또는 polling 경계에서 성공·실패 결과를 받는다.
- 현재 실행 식별자와 일치하는 결과만 적용해 늦게 도착한 callback을 무시한다.
- 성공 시 `SYNCED`와 `last_synced_at`을 기록한다.
- 실패 시 `FAILED`와 제한된 실패 정보를 기록한다.

### 6. 재시도 진입점 구현

- `FAILED` 또는 장시간 `SYNCING`인 Repository를 다시 제출할 명시적 use case를 만든다.
- 재시도는 새 실행 식별자를 발급하되 Git mirror의 멱등 등록 동작을 활용한다.
- 자동 재시도 여부와 횟수는 결정 포인트에 따라 적용한다.

### 7. 자동화 검증

- Agent client가 정확한 `repository_added` payload를 보내는지 검증한다.
- 등록 커밋 전에는 호출하지 않고 커밋 후 한 번 호출하는지 통합 테스트로 검증한다.
- 제출 성공·실패, callback 성공·실패, 중복·지연 callback을 검증한다.
- 상태 전이와 `last_synced_at` 갱신을 검증한다.
- 기존 프로젝트·Bug Agent 연동 테스트와 전체 Gradle 테스트를 실행한다.

### 8. 로컬 통합 검증

- Clio Server, Agent Graph, PostgreSQL, Ollama를 실행한다.
- 공개 Repository를 등록해 `PENDING → SYNCING → SYNCED` 전이를 확인한다.
- Agent의 bare mirror와 활성 commit 생성 결과를 확인한다.
- Agent를 중단한 상태에서 등록해 실패 상태와 재시도를 확인한다.

## 결정 포인트

### D1. Agent 완료 확인 방식

- 대안 A: LangGraph `/runs`의 native `webhook`을 사용한다.
  - Server가 run 제출 시 callback URL을 지정하고 완료 payload를 받는다.
  - Agent Graph의 repository workflow를 수정하지 않고 실행 완료를 통지할 수 있다.
- 대안 B: Agent Graph가 Server internal API를 직접 호출한다.
  - domain 결과를 명시적으로 전달할 수 있지만 모든 종료·예외 경로에 callback 책임이 생긴다.
- 대안 C: Server가 run 상태를 polling한다.
  - callback endpoint가 필요 없지만 scheduler, polling 주기와 장기 실행 복구가 필요하다.
- 추천: **대안 A**. 현재 LangGraph API가 webhook을 지원하며 두 서비스의 기능 코드 결합이 가장 작다.

### D2. 등록 이벤트 전달 보장

- 대안 A: 기존 Bug 처리와 같은 `AFTER_COMMIT` in-process event를 사용한다.
  - 구현이 단순하고 등록 직후 전송되지만 커밋 직후 프로세스가 종료되면 이벤트가 유실될 수 있다.
- 대안 B: transactional outbox에 이벤트를 함께 저장하고 worker가 전달한다.
  - 재시작 후에도 전달할 수 있지만 outbox schema, worker와 보관 정책이 추가된다.
- 추천: **대안 A**. pre-MVP와 기존 패턴에 맞추고, 명시적 재시도로 복구한다. 운영 전달 보장이
  필요해지는 시점에 outbox로 교체한다.

### D3. Agent 제출 실패 시 등록 API 의미

- 대안 A: Repository 등록은 `201 Created`로 유지하고 상태를 `FAILED`로 기록한다.
  - DB 등록과 외부 동기화를 분리하며 사용자가 재시도할 수 있다.
- 대안 B: Agent 제출 실패를 등록 API의 `5xx`로 반환하고 Repository도 삭제한다.
  - 겉보기에는 원자적이지만 AFTER_COMMIT 호출과 맞지 않고 입력 정보를 다시 작성해야 한다.
- 추천: **대안 A**.

### D4. 현재 실행 식별과 stale callback 차단

- 대안 A: `project_sources`에 현재 Agent run ID와 동기화 시작 시각을 저장한다.
  - 조회와 callback 검증이 단순하며 현재 실행 하나만 관리한다.
- 대안 B: Repository sync attempt 테이블을 추가해 모든 실행 이력을 보존한다.
  - 감사·분석에는 유리하지만 현재 요구보다 schema와 API가 커진다.
- 추천: **대안 A**. 현재 상태와 최신 실행만 먼저 관리하고 상세 이력은 Agent run 로그를 사용한다.

### D5. 재시도 방식

- 대안 A: 명시적인 `POST /api/v1/projects/{projectId}/repositories/{repositoryId}/sync`를 제공한다.
  - 사용자가 실패를 확인하고 재시도할 수 있으며 동작이 예측 가능하다.
- 대안 B: 제한된 횟수로 자동 재시도한 뒤 수동 재시도를 허용한다.
  - 일시 장애는 복구하지만 backoff, 최대 횟수와 중복 callback 정책이 필요하다.
- 추천: **대안 A**. 첫 구현의 상태 전이를 단순하게 유지한다.

### D6. 이번 작업의 lifecycle 범위

- 대안 A: 최초 등록의 `repository_added`만 구현한다.
  - 현재 발생한 문제를 작은 변경으로 해결한다.
- 대안 B: 수정·삭제에 `repository_changed`, `repository_removed`까지 함께 연결한다.
  - lifecycle은 완성되지만 commit 비교, webhook과 수정 의미에 추가 결정이 필요하다.
- 추천: **대안 A**. 등록 흐름을 검증한 뒤 같은 패턴으로 확장한다.

### D7. private Repository 처리

- 대안 A: 이번 작업은 credential이 필요 없는 공개 HTTPS Repository만 지원한다.
  - 현재 Agent 계약과 보안 경계를 변경하지 않는다.
- 대안 B: Server의 credential reference를 Agent가 안전하게 조회하는 internal 계약까지 구현한다.
  - private Repository를 지원하지만 secret 저장소와 권한 경계를 함께 설계해야 한다.
- 추천: **대안 A**. credential 연동은 별도 보안 작업으로 분리한다.

## 예상 변경 위치

- `src/main/java/ax/clio/project/entity/ProjectSource.java`
- `src/main/java/ax/clio/project/service/ProjectService.java`
- `src/main/java/ax/clio/project/controller/`
- `src/main/java/ax/clio/agent/client/ClioAgentClient.java`
- `src/main/java/ax/clio/agent/event/`
- `src/main/resources/db/migration/`
- `src/test/java/ax/clio/project/`
- `src/test/java/ax/clio/agent/`

D1에서 native webhook을 선택하면 `clio-agent-graph`의 공개 request나 graph 코드는 변경하지 않는다.

## 완료 조건

- 모든 결정 포인트가 `03-decisions.md`에 근거와 함께 기록된다.
- Repository 등록 후 Agent 요청과 상태 전이가 자동화 테스트로 검증된다.
- 공개 Repository의 실제 Git mirror와 활성 commit 생성이 로컬에서 확인된다.
- 전체 Gradle 테스트가 통과한다.
- 구현 결과와 남은 lifecycle 범위가 `04-result.md`에 기록된다.

