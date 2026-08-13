# 04. Result — Agent Graph 연동 API 리팩터링

## 결과

Spring은 Bug·Issue와 workflow 생명주기만 관리하고, Agent의 판단 모델은 알지 않도록
경계를 정리했다. Agent는 최상위 `clio_agent` 안에서 후보 검색과 매칭을 수행한 뒤
필요한 도메인 변경만 명시적인 internal API로 요청한다.

## 핵심 Agent 연동 API

| 구분 | 요청 | Spring 책임 |
|---|---|---|
| Bug 조회 | Bug 단건·cursor 목록 조회 | 수집 원문 반환 |
| 연결 조회 | 검색된 Bug ID들의 Issue 연결 조회 | 관계 projection 반환, 후보 판단 없음 |
| Issue 생성 | 새 Issue 생성 요청 | Issue 생성과 요청 Bug 연결 |
| Bug 연결 | 기존 Issue에 Bug 연결 요청 | 관계 생성과 Issue 집계 갱신 |
| workflow | 실행 생성·상태 변경 | `PENDING/RUNNING/COMPLETED/FAILED` 관리 |
| 분석 | Issue·Bug 조회와 분석 결과 저장 | immutable 분석 snapshot 보존 |

Issue 생성과 기존 Issue 연결은 서로 다른 API다. Spring에는 `CREATE_NEW`, `AUTO_LINK`,
`REVIEW`를 받는 판정 적용 API가 없고 `bug_match_decisions`도 저장하지 않는다.
`REVIEW`는 Agent workflow의 정상 결과로만 남는다.

전체 JSON 예시는 [`api-contract.md`](api-contract.md)를 참조한다.

## 무결성과 멱등성

- 두 쓰기 API는 `RUNNING`인 `process_report` workflow만 받는다.
- workflow payload와 요청의 `bug_id`가 다르면 `409`다.
- `issue_bugs.bug_id` unique 제약으로 하나의 Bug가 여러 Issue에 연결되지 않는다.
- 새 Issue 생성 재요청에서 Bug가 이미 연결돼 있으면 기존 Issue를 반환한다.
- 기존 Issue 연결 재요청은 같은 관계면 기존 결과를 반환하고 다른 Issue면 `409`다.
- Issue 생성·연결, `bug_count`와 관측 시각 갱신, Bug 상태 전이는 한 트랜잭션이다.

## 남은 과제

- Spring이 Client 이벤트를 받아 Agent 실행을 시작하는 outbound orchestration
- 운영 인증·인가와 관측성
- Agent의 영속 LangGraph checkpoint 구성
