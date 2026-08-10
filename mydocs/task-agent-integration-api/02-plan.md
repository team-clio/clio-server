# 02. Plan — Agent Graph 연동 API 구현

## 독자와 목적

- 예상 독자: Clio Server·Agent Graph 구현자와 API 리뷰어
- 목적: 6개 연동 API와 의존 API의 구현 순서, 경계, 결정할 사항을 합의한다.
- 기준 계약: `clio-agent-graph`의 `normalization`, `matching`, `analysis`, `pcm` 공개 모델

## 구현 단계

### S1. 선행 모델 정리

- `main`에 남은 Agent 소유 AI/RAG 엔티티의 처리 방향을 확정한다(D1).
- Server 소유 데이터와 Agent 소유 데이터를 구분한다.
- 패키지는 기존 도메인별 `controller/service/repository/entity/dto` 구성을 따른다.

### S2. API 계약과 공통 기반

- 6개 API의 요청·응답 DTO, enum, validation을 정의한다.
- Agent 연동 DTO만 `snake_case` JSON을 사용하고 기존 사용자 API 형식은 유지하는 방안을 검토한다(D2).
- 404·409·422 등 공통 오류 응답과 `@RestControllerAdvice`를 구현한다.
- 프로젝트 소속 검증과 멱등 처리 기반을 구현한다(D4).

### S3. 버그 리포트 기반과 매칭 결과 반영

- `Project`, `Bug`, `BugOccurrence`, `Issue`, `IssueBug` Repository를 추가한다.
- 버그 리포트 수집·목록·상세 조회를 구현한다.
- 원문 Report와 정규화 스냅샷의 저장 관계를 구현한다(D3, D8).
- 매칭 결정을 기록하고 기존 이슈 연결·검토 대기·신규 이슈 생성을 적용한다(D5).

### S4. 분석 작업과 결과

- `AnalysisJob`, `AnalysisResult` Repository와 Service를 추가한다.
- 분석 문맥 조회에서 최대 5개 Bug 식별자와 이전 분석 스냅샷을 반환한다.
- 작업 상태 전이와 완전한 `IssueAnalysis` 결과 스냅샷 저장을 구현한다(D6).

### S5. PCM 소유권 경계 확인

- PCM revision·commit·검색 snapshot은 Agent 내부 계약으로 유지한다(D7).
- Server에는 PCM 필드나 동기화 결과 API를 추가하지 않는다.
- Client 요청 전달과 Agent 업무 결과 저장에 필요한 식별자만 Server 계약에 둔다.

### S6. 의존 이슈 조회 API

- 기존 이슈 목록·상세·통계 API의 `501`을 제거한다.
- 필터·페이지·정렬을 Repository query로 처리한다.
- Issue 통계와 연결된 Report 수를 실제 데이터로 계산한다.

### S7. 검증과 결과 문서

- Service 단위 테스트와 MockMvc 통합 테스트를 작성한다.
- 프로젝트 소속 오류, 계약 validation, 상태 전이, 멱등 재시도, 충돌을 검증한다.
- H2 전체 테스트와 PostgreSQL용 매핑 컴파일을 확인한다.
- `04-result.md`와 API 계약 문서를 갱신한다.
- `mydocs/remaining-roadmap.md`가 없으므로 새로 만들지 여부를 결과 단계에서 사용자에게 확인한다.

## 목표 API 계약

| API | 정상 응답 | 핵심 동작 |
|---|---:|---|
| `POST .../bugs/{bugId}/match-decisions` | 200/201 | 매칭 결정 기록 및 Issue 연결 적용 |
| `POST .../issues/{issueId}/analysis-jobs` | 201 | 최초 분석·재분석 작업 생성 |
| `PUT .../analysis-jobs/{jobId}/result` | 200/201 | 완전한 분석 snapshot 저장 |
| `PATCH .../analysis-jobs/{jobId}` | 200 | 작업 상태 전이 |
| `GET .../bug-reports/{reportId}` | 200 | Agent NM 입력과 원문 payload 반환 |
| `GET .../analysis-jobs/{jobId}/context` | 200 | IA 입력 문맥과 이전 분석 반환 |

`reportId`는 한 번의 수집 사건인 `BugOccurrence.id`, `bugId`는 중복 발생을 묶는 `Bug.id`로
구분하는 것을 기본안으로 한다. 상세 필드 이름은 D2에서 확정한다.

## 결정 포인트

### D1. Agent 소유 엔티티 제거 변경의 선행 반영

- A. `refactor/drop-ai-only-entities`의 커밋을 이번 브랜치에 반영한다.
- B. `main` 상태를 유지하고 이번 API 구현에서 해당 엔티티를 사용하지 않는다.
- 추천: **A**. 소유권이 겹치는 Java 엔티티를 남기면 Hibernate가 Agent의 Alembic 테이블을 변경할 위험이 있다.

### D2. Agent 연동 JSON과 ID 계약

- A. Agent 연동 DTO만 `snake_case`, ID는 양수 JSON number로 고정한다.
- B. Server 기본 `camelCase`를 유지하고 Agent adapter에서 변환한다.
- 추천: **A**. Pydantic 공개 모델을 변환 없이 검증할 수 있고 기존 사용자 API에는 영향이 없다.

### D3. 정규화 리포트와 분석 결과 저장 방식

- A. 원본 계약 전체를 JSON snapshot으로 저장하고 식별·조회용 메타데이터만 컬럼화한다.
- B. 모든 중첩 필드를 관계형 테이블과 컬럼으로 분해한다.
- C. 최신 결과만 `Bug`·`Issue` 본문 컬럼에 덮어쓴다.
- 추천: **A**. Agent 계약의 구조를 손실 없이 보존하면서 버전과 재분석 이력을 유지할 수 있다.

### D4. 결과 반영 API의 멱등성

- A. 모든 write API에 `Idempotency-Key` 헤더를 요구하고 공통 처리 기록을 저장한다.
- B. 요청 body의 `request_id`를 각 도메인 테이블에 개별 저장한다.
- C. 리소스 ID와 unique constraint만으로 중복을 막는다.
- 추천: **A**. 동일 키·동일 요청은 이전 응답을 반환하고, 동일 키·다른 요청은 `409`로 일관되게 막을 수 있다.

### D5. 매칭 결정 적용 규칙

- A. `AUTO_LINK`는 기존 Issue 연결, `REVIEW`는 결정만 저장, `CREATE_NEW`는 새 Issue 생성 후 연결한다.
  이미 다른 Issue에 연결된 Bug는 `409`로 거부한다.
- B. `REVIEW`도 후보 Issue에 임시 연결하고 기존 연결은 새 결정으로 이동한다.
- 추천: **A**. 검토 전 데이터 변경을 피하고 잘못된 자동 재배치를 막는다.

### D6. 분석 작업 상태 전이와 결과 완료 처리

- A. `PENDING → RUNNING → COMPLETED`, `PENDING/RUNNING → FAILED`만 허용하고 결과 PUT이 저장과
  `COMPLETED` 전이를 한 트랜잭션에서 수행한다.
- B. PATCH에서 모든 상태 전이를 허용하고 결과 저장과 완료를 분리한다.
- 추천: **A**. 결과 없는 완료 상태와 완료됐지만 결과가 없는 중간 실패를 방지한다.

### D7. PCM과 Server의 소유권 경계

- A. Server는 PCM을 알지 않는다. PCM revision·commit·snapshot은 Agent가 전부 소유하고,
  Server는 Client 요청과 Agent 업무 결과를 연결한다.
- B. Agent의 PCM commit·knowledge 변경 이력 전체를 Server에도 복제한다.
- 추천: **A**. PCM snapshot은 Agent가 이미 실행 시작 시 직접 고정하므로 Server에 복제할 이유가 없다.

### D8. 버그 수집 시 동일 Bug 판정

- A. `errorType + message + 첫 application stack frame + title`의 정규화 문자열을 SHA-256으로
  fingerprint하고, 같은 프로젝트 내 동일 fingerprint는 새 `BugOccurrence`로 누적한다.
- B. 모든 수집 요청마다 새 Bug를 만든다.
- C. 호출자가 fingerprint를 제공하게 한다.
- 추천: **A**. 현재 요청 계약을 유지하면서 반복 발생을 안정적으로 묶고 원문 사건은 보존할 수 있다.

## 결정 후 커밋 단위

1. D1 선행 모델 정리
2. D2 Agent API JSON·ID 계약
3. D3 결과 snapshot 영속 모델
4. D4 공통 멱등 처리 기반
5. D5 매칭 결정 반영
6. D6 분석 문맥·작업·결과
7. D7 PCM 소유권 경계 정리
8. D8 버그 수집·조회와 fingerprint
9. 이슈 조회·통계와 전체 통합 테스트
10. Result와 문서 정리

## 확인 요청

Plan과 D1~D8 결정 포인트 목록을 컨펌해 주세요. 컨펌 후 D1부터 한 항목씩 결정하고,
결정 기록·구현·검증·커밋을 반복합니다.
