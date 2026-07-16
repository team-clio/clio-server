# API 서버 범위 축소 — 에이전트 그래프·RAG 제거

## 이 문서의 목적

요구사항 확정에 따라 **Clio 를 두 서비스로 쪼갠다.** Java 는 **API 서버**만 담당하고,
**에이전트 그래프(분석 파이프라인)와 RAG 는 파이썬**이 가져간다. 이 태스크는 **Java 쪽에서 이관·불필요
코드를 걷어내 요구사항에 맞는 베이스를 만드는 것**이다.

## 배경 — 아키텍처 전환

```
External Services ─┐
                   ├─▶ API Server (Java) ──▶ DBMS (PostgreSQL)
Coding Agent ──────┘         ▲  │                    ▲
                             │  ▼                    │
                        AGENT GRAPH (Python)  ───────┘
                        Issue Resolve / Code Review / Grade Decision Agent
                        + RAG System
```

- **사용자는 무조건 API 서버와만 송수신한다.** Java 가 요청을 받아 파이썬으로 비동기로 쏜다.
- **파이썬은 같은 PostgreSQL 에 직접 접근한다.** (원래 그림엔 AGENT GRAPH → DBMS 화살표가 빠져 있었음)
- **RAG 테이블은 파이썬 소유** — 파이썬이 자기 마이그레이션으로 관리.

## 확정된 요구사항 (API 서버)

| 구분 | 항목 |
|------|------|
| 시스템 관리 | LLM Provider 설정 · LLM 모델 설정 · 유저 관리 · 권한 관리(Viewer/Maintainer/Admin) |
| 프로젝트 관리 | 프로젝트 생성 · **Git 저장소 연동(토큰 등록)** · **맥락 데이터 등록**(기획·문서) |
| 버그 관리 | 버그 수집 · **버그 그룹화(이슈 변환)** · 버그 조회(기간) · 버그 관리(삭제·수정) · **우선순위 피드백**(장기기억) |
| 이슈 관리 | **이슈 목록 조회** · **이슈 통계(대시보드)** · **브랜치 관리**(머지 상태·페이지 이동) |
| 에이전트 연동 | TBD |

## 진단 — 요구사항 대비 현재 코드

현재 main 소스 **약 122개**. 요구사항에 대응시키면:

| 요구사항 | 현재 |
|---|---|
| LLM Provider·모델 설정 | ✅ `llm/`(11) |
| 프로젝트 생성 | ✅ `project/`(7) |
| 버그 수집 | ✅ `report/`(8) |
| 버그 조회(기간) | △ 조회만, 기간 필터 없음 |
| 버그 관리(삭제·수정) | △ 상태 변경만 |
| 유저·권한 | ❌ **Spring Security 자체가 없음** |
| Git 저장소 연동 | ❌ `Project` 는 `rootPath`(로컬 경로), `ProjectPathValidator` 가 `/workspace` 하위만 허용 |
| 맥락 데이터 등록 | ❌ |
| 버그 그룹화 → 이슈 | ❌ **Issue 엔티티 자체가 없음** |
| 우선순위 피드백 | ❌ |
| 이슈 목록·통계·브랜치 관리 | ❌ 전무 |

**즉 이건 "정리"가 아니라 재시작에 가깝다.** 93개가 사라지고 29개가 남는데, 남는 29개도 요구사항의 일부만
커버한다. 이번 태스크는 그중 **삭제(음의 작업)만** 다루고, 신규 도메인은 별도 로드맵으로 넘긴다.

## 삭제 대상 (확정)

| 패키지 | 개수 | 사유 |
|--------|------|------|
| `analysis/` | 45 | 분석 파이프라인 → 파이썬 (job 포함 — 연동이 TBD 라 어차피 재설계) |
| `memory/` | 31 | RAG → 파이썬 (decision 포함 — 파이썬 이관) |
| `code/` | 16 | 코드 인덱스·검색 → **요구사항에 없음** |
| `mcp/` | 1 | 빈 패키지 |
| **합계** | **93** | + 딸린 테스트 **37개** |

**남는 것**: `common`(3) · `project`(7) · `report`(8) · `llm`(11) + `ClioApplication`

### 함께 정리해야 하는 것

1. **`llm/client` · `llm/config`** — `LlmClient`·`OpenAiCompatibleLlmClient`·`LlmHttpClientConfig` 의 사용처는
   `analysis`·`memory` 뿐이다. 삭제하면 **죽은 코드**가 된다. (파이썬이 자체 LLM 클라이언트를 가지므로
   Java 는 LLM 을 직접 호출하지 않는다 — 설정 CRUD 만 소유)
2. **Flyway 마이그레이션** — 삭제되는 테이블이 남는다.
   - `V1`: `projects`(유지) · `code_files`(삭제) · `code_symbols`(삭제)
   - `V2`: `bug_reports`(유지)
   - `V3`: `analysis_jobs`(삭제)
   - RAG 테이블(`code_chunks` 등)은 **Flyway 에 없다** — `ddl-auto: update` 가 JPA 엔티티로 만들고 있었다.
     엔티티를 지우면 Java 는 더 이상 안 건드리고, 파이썬이 자기 마이그레이션으로 소유한다.
3. **`BugReportStatus` 의 전이 주체 상실** — `PENDING/ANALYZING/COMPLETED/FAILED` 중 `ANALYZING`·`COMPLETED`
   ·`FAILED` 는 `AnalysisWorker` 가 전이시켰는데 그게 사라진다. 남는 건 수동 PATCH(`BugReportService`) 뿐.
4. **`mydocs` 문서** — task 문서 7~8개가 죽은 기능의 문서가 되고, `remaining-roadmap.md` 는 통째로 무효.

## 주의사항

1. **테스트 안전망이 함께 사라진다.** 지금까지의 리팩터는 전체 스위트 그린이 안전망이었으나, 이번엔
   **테스트 40개 중 37개가 삭제 대상**이고 **살아남는 건 3개**(`ClioApplicationTests`,
   `LlmConfigResponseTest`, `LlmConfigServiceTest`)뿐이다. "그린"이 보증하는 범위가 매우 좁아진다.
2. **살아남는 4개 패키지는 삭제 대상을 전혀 참조하지 않는다** (확인 완료) — 삭제 자체는 깨끗하게 떨어진다.
3. **기능 공백을 받아들이는 결정이다.** 삭제 후 한동안 분석·RAG 기능은 동작하지 않는다(사용자 승인됨).
4. 되돌리기 대비 — 삭제 커밋은 기능 단위로 쪼개 어떤 것이 왜 빠졌는지 커밋 목록으로 읽히게 한다.

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **`llm/client`·`llm/config` 삭제 여부** (죽은 코드로 남길지, 지금 걷어낼지).
- **Flyway 정리 방식**: `V4` drop 마이그레이션 신설 / 마이그레이션 squash 재작성(운영 DB 유무에 달림).
- **`BugReportStatus` 축소 여부**: 전이 주체가 없는 값을 남길지(파이썬 연동 시 재사용) / 지금 줄일지.
- **`mydocs` 죽은 문서 처리**: 삭제 / `archive/` 이관 / 유지(히스토리).
- **`remaining-roadmap.md` 재작성 시점**: 이번 태스크 / 별도.
- **커밋 분할 단위.**

## 범위

**포함(예정)**: 위 삭제 대상 93 + 테스트 37 제거, 딸린 정리(llm client·Flyway·문서), 남는 코드 컴파일·기동 확인.
**제외**: 신규 도메인 구현(유저·권한·Issue·Git 연동·맥락 데이터·대시보드·브랜치 관리), 파이썬 쪽 작업,
에이전트 연동 API 설계(요구사항 TBD).

## 워크플로우

이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
