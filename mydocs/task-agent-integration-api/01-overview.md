# 01. Overview — Agent Graph 연동 API 구현

## 배경

`clio-agent-graph`의 매칭·분석·동기화 그래프는 입력 계약과 결과 계약을 갖고 있지만,
`clio-server`에는 해당 데이터를 안정적으로 조회하거나 처리 결과를 반영할 API가 없다.
현재 서버의 버그 리포트·이슈 컨트롤러도 응답 계약만 있고 `501 Not Implemented`를 반환한다.

따라서 API Server를 사용자 요청과 영속 데이터의 기준점으로 두고, Agent Graph 실행 전후에
필요한 조회·결과 반영 경계를 HTTP API로 구현한다.

## 현재 상태

- `clio-agent-graph`
  - 매칭 그래프 입력: 프로젝트, 버그 ID, 버그 리포트
  - 매칭 그래프 출력: 정규화 리포트, 매칭 결정
  - 분석 그래프 입력: 분석 작업 ID, 프로젝트, 이슈, 버그 목록, 트리거 버그
  - 분석 그래프 출력: 구조화된 분석 결과
  - PCM과 Repository snapshot을 Agent 내부 저장소에서 직접 관리
- `clio-server`
  - 버그·이슈·분석 작업·프로젝트 컨텍스트·프로젝트 소스 엔티티가 존재
  - Repository/Service 계층이 없고 관련 컨트롤러는 `501` 반환
  - Agent 결과의 멱등 반영과 작업 상태 전이 계약이 없음
- `main`에는 Agent가 직접 소유할 예정인 일부 AI/RAG 엔티티가 남아 있으며,
  `refactor/drop-ai-only-entities` 브랜치에서 제거된 상태다. 이번 작업의 선행 관계를 Plan에서
  결정해야 한다.

## 목표

다음 7개 Agent 연동 API를 실제 DB 트랜잭션까지 동작하도록 구현한다.

### Agent 결과 반영 API

1. `POST /api/v1/projects/{projectId}/bug-reports/{reportId}/grouping-decisions`
   - Agent의 기존 Bug 연결·신규 Bug 생성·검토 대기 결정을 멱등 반영
2. `POST /api/v1/projects/{projectId}/bugs/{bugId}/match-decisions`
   - Agent의 Issue 매칭 결정을 멱등 반영
   - 기존 이슈 연결, 검토 대기, 새 이슈 생성 결과 처리
3. `POST /api/v1/projects/{projectId}/issues/{issueId}/analysis-jobs`
   - 최초 분석 또는 재분석을 위한 분석 작업 생성
4. `PUT /api/v1/projects/{projectId}/analysis-jobs/{jobId}/result`
   - 구조화된 분석 결과를 멱등 저장
5. `PATCH /api/v1/projects/{projectId}/analysis-jobs/{jobId}`
   - 분석 작업의 실행 상태·실패 사유 갱신

### Agent 입력 조회 API

6. `GET /api/v1/projects/{projectId}/bug-reports/{reportId}`
   - 원문 payload를 포함한 단일 버그 리포트 조회
7. `GET /api/v1/projects/{projectId}/analysis-jobs/{jobId}/context`
   - 분석 실행에 필요한 이슈·대표 버그 식별자·이전 분석 스냅샷 조회

## 함께 구현할 기반 기능

위 API가 독립적으로 동작하려면 현재 `501`인 다음 기능도 함께 완성해야 한다.

- 버그 리포트 수집과 목록 조회
- 이슈 목록·상세·통계 조회
- 프로젝트·버그·발생 건·이슈·연결·분석 작업·분석 결과 Repository
- 공통 예외 응답과 프로젝트 소속 검증
- 요청 validation, 트랜잭션, 중복 요청 멱등 처리

## 범위

- HTTP 요청·응답 DTO와 Controller
- Service·Repository와 필요한 도메인 생성/변경 메서드
- Agent 업무 결과 스냅샷을 보관하는 영속 모델
- 분석 작업 상태 전이와 결과 저장 규칙
- 매칭 결정 적용 및 `IssueBug` 연결
- 단위·통합 테스트와 API 계약 문서

## 비범위

- LLM 공급자·모델 관리자 API 구현
- 서버에서 Agent Graph 프로세스를 직접 기동하는 HTTP/Python 클라이언트
- Agent 내부 벡터 검색·임베딩·RAG 테이블 구현
- PCM revision·commit·동기화 상태의 Server 저장 및 조회 API
- 관리자 화면, 인증/인가, 배포 인프라 변경
- 과거 데이터의 운영 DB 마이그레이션 실행

## 완료 기준

- 7개 연동 API와 의존 버그/이슈 API가 더 이상 `501`을 반환하지 않는다.
- 잘못된 프로젝트 소속, 존재하지 않는 리소스, validation 실패가 일관된 4xx 응답을 반환한다.
- 결과 반영 API는 동일 요청 재시도 시 데이터가 중복 생성되지 않는다.
- 매칭 결정과 분석 작업 상태 변경은 트랜잭션 경계에서 일관되게 반영된다.
- 전체 테스트와 Java 컴파일이 통과한다.

## 주요 위험과 Plan에서 결정할 내용

- Agent Graph와 Server 사이의 요청·응답 JSON 상세 계약
- 멱등 키 저장 방식과 충돌 응답 정책
- 정규화 리포트/분석 결과를 구조화 컬럼으로 나눌지 JSON 스냅샷으로 보관할지
- 매칭 결정별 이슈 생성·연결·검토 대기 규칙
- 분석 작업 상태 전이 규칙
- `refactor/drop-ai-only-entities` 변경을 이번 브랜치에 어떤 방식으로 선행 반영할지

## 확인 요청

위 범위가 맞는지 컨펌해 주세요. 컨펌되면 `02-plan.md`에 구현 단계와 각 결정 포인트의
대안·추천안을 정리하겠습니다.
