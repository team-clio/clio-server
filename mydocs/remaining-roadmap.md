# Clio Server 개발 로드맵

## 목적

이 문서는 `clio-server`의 개발 순서와 진행 상태를 관리한다.
상세 설계와 결정 근거는 각 `mydocs/task-*/` 문서를 참조한다.

## 1. 책임 경계

- Server: Client 요청, 업무 데이터, Agent 결정과 분석 결과의 영속화
- Agent: 정규화, Bug grouping, Issue matching, 분석, PCM과 Repository snapshot
- Client와 Agent 사이의 요청·응답은 Server가 중계한다.
- Server는 Agent 내부 PCM 모델과 저장 구조를 알지 않는다.

## 2. 완료된 기반 작업

- ✅ 설계 대비 엔티티 차이 정리 (`task-design-entity-gap`)
- ✅ Controller DTO 분리 (`task-controller-dto-split`)
- ✅ Agent 소유 AI/RAG 엔티티 제거
- ✅ BugReport·Bug·Issue·AnalysisJob·IssueAnalysis 영속 모델 정리

## 3. Agent 연동 API

- ✅ BugReport 단건 조회
- ✅ Bug grouping 결정 반영
- ✅ Bug의 Issue 매칭 결정 반영
- ✅ AnalysisJob 생성·상태 변경·context 조회
- ✅ IssueAnalysis 결과 저장
- ✅ BugReport 수집·목록과 Issue 목록·상세·통계 API
- ✅ 요청 멱등 처리와 전체 생명주기 통합 테스트

상세 계약은 [`task-agent-integration-api/api-contract.md`](task-agent-integration-api/api-contract.md)를 참조한다.

## 4. Agent 실행 연결

- ⬜ Client 요청을 받아 Agent 실행을 시작하는 orchestration 경계 정의
- ⬜ Server→Agent 호출 방식과 timeout·retry·실패 전달 정책 결정
- ⬜ 비동기 실행 상태를 Client에 전달하는 조회 또는 push 방식 결정

## 5. 운영 준비

- ⬜ 운영 DB migration 작성과 기존 데이터 전환 검증
- ⬜ 인증·인가와 프로젝트 접근 제어
- ⬜ Agent 호출·결정 반영·분석 작업의 관측성 보강
- ⬜ PostgreSQL 통합 테스트와 장애 복구 시나리오 검증

## 6. 이관 이력

기존 로드맵의 Code/Issue/Decision Memory, LLM, LangGraph, PCM 항목은
`df9125a`에서 `clio-agent-graph` 책임으로 이관됐다. Server 로드맵에서 중복 관리하지 않는다.

## 7. 추천 개발 순서 (단일 진행 기준)

> 상태: ✅ 완료 · 🔶 부분 · ⏭️ 보류 · ⬜ 미착수 · ◀ 다음

1. Server 도메인·영속 모델 정리 — ✅
2. Controller DTO 분리와 공통 오류 계약 — ✅
3. Agent Graph 연동 API와 의존 조회 API — ✅ (`task-agent-integration-api`)
4. Server→Agent 실행 orchestration 계약과 구현 — ◀ 다음
5. 운영 DB migration과 PostgreSQL 통합 검증 — ⬜ 미착수
6. 인증·인가와 프로젝트 접근 제어 — ⬜ 미착수
7. 관측성·재시도·장애 복구 보강 — ⬜ 미착수

## 변경 이력

- 2026-08-10: 삭제 이력을 복구하고 Server 책임 기준으로 재작성했다.
- 2026-08-10: Agent Graph 연동 API 완료 상태와 다음 작업을 반영했다.
