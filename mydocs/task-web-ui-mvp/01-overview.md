# 최소 Web UI

## 이 문서의 목적

지금까지 백엔드로만 존재하던 흐름(프로젝트 등록 → 코드 스캔 → 버그 리포트 → 분석 → 결과 확인)을
**브라우저에서 눈으로 확인**할 수 있는 최소 UI를 만든다. roadmap 2 / "추천 개발 순서" #6.

## 배경

- 백엔드 MVP와 분석 파이프라인(랭킹·흐름 추적)이 REST API로 모두 동작한다.
- 하지만 확인하려면 curl/HTTP 클라이언트를 써야 한다. 실제로 써보고 결과를 판단하려면 UI가 필요하다.
- 목표는 예쁜 UI가 아니라 **핵심 흐름을 끝까지 밟아볼 수 있는 최소 화면**이다.

## 현재 구현 상태

### 이미 존재하는 REST API (UI가 소비할 대상)

| 기능 | 메서드 · 경로 |
|------|--------------|
| 프로젝트 등록 | `POST /api/projects` |
| 프로젝트 목록 | `GET /api/projects` |
| 프로젝트 상세 | `GET /api/projects/{id}` |
| 프로젝트 삭제 | `DELETE /api/projects/{id}` |
| 코드 스캔 실행 | `POST /api/projects/{projectId}/scan` |
| 파일 목록 | `GET /api/projects/{projectId}/files` |
| 심볼 목록 | `GET /api/projects/{projectId}/symbols` |
| 코드 검색 | `GET /api/projects/{projectId}/search` |
| 버그 리포트 등록 | `POST /api/reports` |
| 버그 리포트 목록 | `GET /api/reports` |
| 버그 리포트 상세 | `GET /api/reports/{id}` |
| 분석 실행 | `POST /api/reports/{reportId}/analysis-jobs` |
| 분석 Job 목록 | `GET /api/reports/{reportId}/analysis-jobs` |
| 분석 결과 상세 | `GET /api/analysis-jobs/{id}` |
| LLM 설정 CRUD | `/api/llm-configs` |

→ **UI가 필요로 하는 API는 이미 다 있다.** 새 백엔드 엔드포인트는 원칙적으로 만들지 않는다.

### 프론트 자산

- `src/main/resources/static/` (비어 있음) — 정적 파일을 두면 Spring이 그대로 서빙.
- `src/main/resources/templates/` (비어 있음) — Thymeleaf 서버 렌더링도 가능.

## 문제점 / 필요

1. 브라우저에서 프로젝트를 만들고 스캔하고 리포트를 넣고 분석을 돌릴 방법이 없다.
2. 분석 결과(중요도/난이도/위험도, 관련 코드, 영향 흐름, 근거)를 사람이 읽기 좋게 볼 화면이 없다.
3. 분석 Job은 비동기라 진행 상태(PENDING/RUNNING/COMPLETED/FAILED)를 확인할 방법이 필요하다.

## 개선 방향

### A. 핵심 흐름 화면
프로젝트 목록/등록 → 상세(스캔·파일·심볼) → 리포트 목록/등록 → 분석 실행 → 진행 상태 → 결과 상세.

### B. 분석 결과 표시
점수 3종, 관련 코드(RelatedCodeEntry), 영향 흐름(CodeFlow), 근거(rationale), 추천을 분리해 표시.

### C. 비동기 상태 폴링
분석 실행 후 Job 상태를 주기적으로 조회해 완료되면 결과를 보여준다.

## 범위

포함(예정): 위 핵심 흐름을 밟을 수 있는 최소 화면, 분석 결과 표시, 상태 폴링.
제외: 인증/로그인, 디자인 시스템, 반응형 완성도, 실시간(WebSocket), i18n, 접근성 완비.

## 이 작업에서 사용자가 정할 것 (plan에서 결정 포인트로 다룸)

- **프론트 기술 스택**: 정적 HTML+바닐라 JS(빌드 없음) / Thymeleaf 서버 렌더 / 별도 SPA(React 등)
- **화면 범위**: roadmap 2의 10개 화면 중 최소 세트를 어디까지 포함할지
- **LLM 설정 화면 포함 여부**
- **분석 상태 폴링 주기/방식**
- **정적 파일 배치 위치와 라우팅**

## 관련 파일

```
src/main/resources/static/            - (신규) 정적 프론트 자산
src/main/java/ax/clio/**/*Controller  - 소비할 REST API (변경 없음 목표)
src/main/java/ax/clio/**/*Response    - 응답 스키마(화면 매핑 참고)
```

## 주의사항

1. **백엔드 API는 변경하지 않는 것을 목표로 한다.** 부득이하면 결정 포인트로 올린다.
2. 최소·명확 우선. 과한 프레임워크/빌드 도입은 지양(결정 포인트).
3. 워크플로우 규칙 준수: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
