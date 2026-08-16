# PCM inspect 미가용 오류 처리 — 결과

## 완료 사항

`task/pcm-inspect-unavailable` 브랜치에서 clio-server와 clio-admin을 함께 변경했다.
clio-agent-graph 실행 편의는 별도 작업 `task/agent-run-convenience`로 진행했다.

| 저장소 | 변경 |
|---|---|
| clio-server | inspect 미가용을 503 `PCM_INSPECT_UNAVAILABLE`로 매핑 |
| clio-admin | PCM 화면 오류 배너·재시도·빈 상태 구분 |
| clio-agent-graph | `make dev`로 inspect 서버 + `langgraph dev` 함께 실행 |

## clio-server 구현

- `PcmInspectUnavailableException` 추가
- `GlobalExceptionHandler`에 503 매핑 추가
- `PcmInspectClient`가 5xx와 연결 실패를 unavailable로 변환하고 404 매핑은 유지
- `PcmInspectClientTest`에 upstream 503 변환 테스트 추가

## clio-admin 구현

- snapshot 조회 실패 시 스냅샷 카드에 오류 표시
- Knowledge 목록 조회 실패 시 오류 배너와 “다시 시도” 버튼 표시
- 오류일 때는 “저장된 지식 문서가 없습니다” 빈 상태를 보여주지 않음

## 검증

```bash
# clio-server
./gradlew test                                    # BUILD SUCCESSFUL

# clio-admin
npm run typecheck                                 # 통과
npm run lint                                      # 통과
npm run build                                     # 통과

# clio-agent-graph
make help                                         # dev/inspect/infra 표시
bash -n scripts/dev.sh                            # 문법 통과
uvicorn --env-file .env ... --port 2025 smoke test # /docs 200
```

## 남은 과제

- clio-server와 clio-admin PR(base=main)을 열어 머지한다.
- clio-agent-graph `task/agent-run-convenience`도 별도 PR로 머지한다.
- inspect 서버를 실행하지 않은 상태에서 admin 화면이 오류를 명확히 보여주는지
  실제 브라우저에서 최종 확인한다.
