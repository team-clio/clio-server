# 저장소 작업 지침

Clio API Server (Java 21 / Spring Boot). 사용자가 송수신하는 유일한 창구이며, AX·RAG는
`clio-agent-graph`(Python)가 소유한다. 서비스 구성은 [`README.md`](README.md)를 참고한다.

## 작업 시작 전 (반드시 선행)

**첫 파일 변경 전에 이 작업에 작업 절차 규칙을 적용할지 사용자에게 묻는다.**

- 판단 기준: [`mydocs/workflow-rules.md`](mydocs/workflow-rules.md)의 "적용 기준" 표
- 사용자의 답을 `.claude/workflow-mode.json`에 현재 브랜치 이름으로 기록한다
- 기록 전에는 `.claude/hooks/workflow-gate.py`가 파일 변경과 커밋을 차단한다

적용하기로 했으면 `mydocs/task-<이름>/`의 4단계 절차(overview → plan → decisions → result)를
게이트대로 따른다. 적용하지 않기로 했으면 절차 없이 바로 작업한다.

## 작업별 문서

| 작업 상황 | 먼저 읽을 문서 |
|---|---|
| 모든 작업의 절차·커밋·브랜치 | `mydocs/workflow-rules.md` |
| 문서·Issue·PR 글쓰기 | `mydocs/document-writing-rules.md` |
| 새 클래스 위치, 패키지·의존 방향, 테스트 배치 | `mydocs/code-conventions.md` |
| API 경계와 리소스 lifecycle | `mydocs/api-boundaries-and-lifecycle.md` |
| 엔티티·스키마 | `mydocs/erd.md` |
| 파이썬으로 재구현할 AX 설계 기록 | `mydocs/ax-reference/` |

`mydocs/workflow-rules.md`와 `mydocs/document-writing-rules.md`는 `clio-agent-graph`와
**같은 내용의 사본**이다. 한쪽을 고치면 다른 쪽도 같은 변경으로 맞춘다.

## 검증

```bash
./gradlew compileJava compileTestJava
./gradlew test
```

`bootRun`은 `spring-boot-docker-compose`로 PostgreSQL+pgvector와 Ollama를 함께 띄운다.
