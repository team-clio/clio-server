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
- **Agent Graph (별도 저장소, Python)** — 에이전트들과 RAG 시스템. 업무 데이터는 Server의
  `internal-api`로만 변경하고, **NormalizedReport·RAG·PCM 테이블은 파이썬이 소유**한다.

> **현재 상태: 전면 재작성 중.** 요구사항 확정에 따라 이전 구현(로컬 코드 분석 파이프라인·RAG·코드 인덱스)을
> 전부 제거하고 골격만 남긴 상태다. 이전 구현 코드는 git 히스토리(`93a87dd`)에 있고,
> **파이썬으로 다시 만들 AX 부분의 설계·결정 기록은 `mydocs/ax-reference/` 에 남겨뒀다.**

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

`spring-boot-docker-compose` 가 붙어 있어 `bootRun` 만으로 `compose.yaml`의 PostgreSQL + pgvector와
Ollama가 함께 뜬다. 직접 띄우려면 `docker compose up -d --wait`를 실행한다. Ollama는 최초 실행 시
`qwen3-embedding:0.6b`를 내려받아 `clio-ollama` volume에 보관하며 11434 포트를 노출한다.

macOS의 Docker 컨테이너에서는 일반적으로 Metal 가속을 사용하지 못하므로 네이티브 Ollama보다 느릴 수 있다.
현재 0.6B 모델은 로컬 재현성을 우선한 CPU 실행 구성이다.

신규 스키마는 현재 `ddl-auto: update`가 만들고, 기존 스키마의 호환 변경은 Flyway migration이
먼저 적용한다. V2는 과거 `bug_occurrences` 명칭을 정규화하고, V3는 각 수집 원문을
개별 `bugs` 행으로 보존하면서 workflow 중심 스키마로 전환한다.

## 개발 규칙

작업 절차는 `mydocs/workflow-rules.md` 를 따른다.
