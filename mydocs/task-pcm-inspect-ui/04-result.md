# PCM 메모리 inspect 화면 — 결과

## 완료 사항

D6 순서대로 세 저장소에 구현을 마쳤다.

| 저장소 | 구현 | 커밋 |
|---|---|---|
| clio-agent-graph | standalone FastAPI inspect 앱 + `list_knowledge` Reader 추가 | `3a05f93` |
| clio-agent-graph | README에 inspect API 실행 문서 | `ce121b5` |
| clio-server | PCM 중계 API (properties/client/service/controller) | `fb1b436` |
| clio-admin | PCM 메모리 inspect 화면 + API client + hooks | `869b09c` |

모두 `main`에서 분기한 `task/pcm-inspect-ui` 브랜치에서 커밋했다.

### 에이전트 PCM read API

- `src/clio_agent_graph/context/pcm/inspect_api.py` — `create_app(reader)` 주입 방식의
  FastAPI 앱. `ProjectContextReader` Protocol에 `list_knowledge`를 추가했다.
- 엔드포인트: `GET /pcm/projects/{project_id}/snapshot`,
  `GET /pcm/projects/{project_id}/knowledge`,
  `GET /pcm/projects/{project_id}/knowledge/{knowledge_id}` (404 = `KnowledgeNotFoundError`).
- 실행: `uvicorn clio_agent_graph.context.pcm.inspect_api:app --port 2025`
  (`CLIO_PCM_DATABASE_URL` 없으면 in-memory PCM).

### Spring 중계 API

- `PcmInspectProperties`(`clio.pcm-inspect.url`) + `PcmInspectClient`(RestClient, 404→
  `ResourceNotFoundException`) + `PcmInspectService` + `ExternalPcmInspectController`.
- 경로: `/external-api/v1/pcm/projects/{projectId}/snapshot`,
  `/external-api/v1/pcm/projects/{projectId}/knowledge`,
  `/external-api/v1/pcm/projects/{projectId}/knowledge/{knowledgeId}`.
- 에이전트 JSON을 Map/List로 그대로 통과시킨다(D1 소유권 원칙). projectId는
  `String(projectId)` 변환(D5).

### admin 화면

- `src/api/pcm.ts` — 스냅샷·지식 문서 타입과 `request<T>` 기반 클라이언트.
- `src/api/hooks.ts` — `usePcmSnapshot` / `usePcmKnowledge` / `usePcmKnowledgeDetail`.
- `src/pages/PcmInspectPage.tsx` — 상단 snapshot 요약, 왼쪽 목록, 오른쪽 상세 패널(D4).
  tombstone 배지, 본문·출처·관련 문서·유효 범위 표시.
- `Sidebar.tsx`(개발자 도구 → PCM 메모리)와 `App.tsx`에 페이지 등록.
- vite proxy는 기존 `/external-api → :8080`을 그대로 사용한다.

## 검증

```bash
# clio-agent-graph
.venv/bin/pytest tests/pcm/test_inspect_api.py   # 7 passed
.venv/bin/ruff check .                            # 내 변경 파일은 통과 (기존 2건만 잔존)

# clio-server
./gradlew test                                    # BUILD SUCCESSFUL (전체)

# clio-admin
npm run typecheck                                 # 통과
npm run lint                                      # 통과
npm run build                                     # 통과
```

## 실행 방법

1. clio-agent-graph: `uvicorn clio_agent_graph.context.pcm.inspect_api:app --port 2025`
2. clio-server: `./gradlew bootRun`
3. clio-admin: `npm run dev` → 개발자 도구 > PCM 메모리에서 프로젝트를 선택해 조회

## 알려진 사항

- `clio-agent-graph` 전체 pytest의 `test_new_report_persists_complete_analysis_with_repository_citation`
  은 live LLM 분석 결과 의존 flake로, 이 작업 전부터 실패한다(기존 결함, 범위 밖).
- `ruff check .`의 E501(models.py), I001(graphs/__init__.py)과 format 7건도 모두 이 작업
  이전의 기존 항목이며 이 작업에서 변경한 파일은 포함되지 않는다.
- clio-admin의 `main` 브랜치에는 이 작업과 무관한 staged 변경(e2e 리포트, reasonix.toml
  삭제)이 있어 작업 파일만 지정해 커밋했다. 그 staged 변경은 이후 사용자 판단으로 처리한다.
