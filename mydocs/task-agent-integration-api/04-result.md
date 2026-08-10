# 04. Result — Agent Graph 연동 API 구현

## 결과

Server가 Client 업무 데이터와 Agent의 판단 결과를 연결·보관하도록 7개 연동 API를 구현했다.
PCM, 정규화, 검색과 분석 판단은 Agent에 남겨 Server가 Agent 내부 모델을 알지 않도록 경계를 정리했다.

## 구현된 Agent 연동 API

| 구분 | API | 결과 |
|---|---|---|
| 입력 조회 | `GET .../bug-reports/{reportId}` | 원본 BugReport 반환 |
| 결정 반영 | `POST .../bug-reports/{reportId}/grouping-decisions` | Bug grouping 적용 |
| 결정 반영 | `POST .../bugs/{bugId}/match-decisions` | Issue 연결·생성·검토 기록 |
| 작업 생성 | `POST .../issues/{issueId}/analysis-jobs` | 최초 분석·재분석 Job 생성 |
| 상태 변경 | `PATCH .../analysis-jobs/{jobId}` | 실행·실패 상태 전이 |
| 입력 조회 | `GET .../analysis-jobs/{jobId}/context` | 분석 context와 이전 결과 반환 |
| 결과 반영 | `PUT .../analysis-jobs/{jobId}/result` | 결과 snapshot 저장과 완료 처리 |

전체 JSON 예시는 [`api-contract.md`](api-contract.md)를 참조한다.

## 함께 완료한 기능

- BugReport 수집·목록 조회와 Issue 목록·상세·통계 API의 `501`을 제거했다.
- Agent의 `MATCH_EXISTING`, `CREATE_NEW`, `REVIEW` Bug grouping 결정을 원자적으로 반영한다.
- 기존 Bug에 Issue가 연결돼 있으면 같은 Issue를 반환하고 Issue matcher 재실행을 막는다.
- Issue match 결정과 Issue 생성·연결을 하나의 트랜잭션에서 처리한다.
- 재분석은 완료된 이전 Job과 전체 IssueAnalysis snapshot을 참조한다.
- 같은 `request_id`와 요청은 결과를 재생하고, 내용이 다르면 `409`를 반환한다.
- Java DTO는 camelCase를 유지하고 Agent 연동 JSON 응답·요청만 snake_case로 직렬화한다.

## Agent 변경

`clio-agent-graph`의 `feat/bug-grouping-contract` 브랜치에 Agent 소유 Bug grouping을 추가했다.

- 공개 `bug_grouper` graph와 입출력 모델
- 기존 Bug 후보 조회 port와 PostgreSQL adapter
- grouping 판단 service와 LangChain structured-output adapter
- 기존 matcher가 grouping 결과 뒤에서 동작하도록 계약 갱신

관련 커밋: `9b9e948 feat: add Agent-owned Bug grouping`

## 검증

- Server 전체 Gradle 테스트: 27개 성공, 실패 0개
- 수집 → grouping → Issue matching → Job 실행 → context → 결과 저장 → Issue 조회 통합 흐름 성공
- Agent 전체 pytest 성공
- Agent Ruff 검사와 format 검사 성공

## 결정 반영 요약

- Server의 AI 전용 엔티티를 제거했다.
- Agent 연동 JSON만 snake_case를 사용한다.
- 분석 결과는 완전한 JSON snapshot으로 보존한다.
- write API는 body의 `request_id`로 멱등 처리한다.
- PCM과 Bug 동일성 판단은 Agent가 소유한다.
- Server는 Agent 판단에 따른 업무 관계와 상태만 변경한다.

## 남은 과제

- Server가 Client 요청을 받아 Agent 실행을 시작하는 outbound orchestration은 별도 작업이다.
- 운영 DB schema migration 작성과 기존 데이터 전환은 이번 범위에 포함하지 않았다.
- 인증·인가, 운영 관측성, PostgreSQL 장애 복구 검증이 필요하다.

후속 작업은 Server가 Client 요청을 받아 Agent 실행을 시작하는 outbound orchestration이다.
