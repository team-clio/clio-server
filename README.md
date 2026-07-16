# Clio

버그를 수집해 이슈로 묶고, 우선순위 판단을 돕는 서비스.

두 개의 서비스로 구성된다.

```
External Services ─┐
                   ├─▶  API Server (Java)  ──▶  DBMS (PostgreSQL)
Coding Agent ──────┘          ▲   │                    ▲
                              │   ▼                    │
                        AGENT GRAPH (Python)  ─────────┘
                   Issue Resolve / Code Review / Grade Decision Agent
                   + RAG System
```

- **API Server (이 저장소)** — 사용자가 송수신하는 유일한 창구. 요청을 받아 에이전트 그래프로 비동기 전달.
- **Agent Graph (별도 저장소, Python)** — 에이전트들과 RAG 시스템. 같은 PostgreSQL 에 직접 접근하며,
  **RAG 테이블은 파이썬이 소유**한다.

> **현재 상태: 전면 재작성 중.** 요구사항 확정에 따라 이전 구현(로컬 코드 분석 파이프라인·RAG·코드 인덱스)을
> 전부 제거하고 골격만 남긴 상태다. 이전 구현과 그 설계 결정 기록은 git 히스토리에 남아 있다
> (`93a87dd` 이전).

## 요구사항 (API Server)

| 구분 | 항목 |
|------|------|
| 시스템 관리 | LLM Provider 설정 · LLM 모델 설정 · 유저 관리 · 권한 관리(Viewer / Maintainer / Admin) |
| 프로젝트 관리 | 프로젝트 생성 · Git 저장소 연동(토큰 등록) · 맥락 데이터 등록(기획·문서) |
| 버그 관리 | 버그 수집 · 버그 그룹화(이슈로 변환) · 기간별 조회 · 수정·삭제 · 우선순위 피드백(장기기억) |
| 이슈 관리 | 이슈 목록 조회 · 이슈 통계(대시보드) · 브랜치 관리(머지 상태 조회 등) |
| 에이전트 연동 | TBD |

## 로컬 실행

```bash
./gradlew bootRun
```

`spring-boot-docker-compose` 가 붙어 있어 `bootRun` 만으로 `compose.yaml`(PostgreSQL + pgvector)이 함께 뜬다.
직접 띄우려면 `docker compose up -d`.

스키마는 현재 `ddl-auto: update` 가 만든다. 마이그레이션 도구는 도입돼 있지 않다.

## 개발 규칙

작업 절차는 `mydocs/workflow-rules.md` 를 따른다.
