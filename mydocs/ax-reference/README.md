# AX Reference — 파이썬 재구현 참고자료

여기 문서들은 **Spring(Java)으로 구현했던 AX 부분**(분석 파이프라인 · RAG · LLM 활용)의 설계·결정 기록이다.
해당 기능은 **파이썬 에이전트 그래프로 이관**하기로 하면서 이 저장소에서 삭제됐고, **그때 다시 만들 때 보려고**
문서만 남겼다.

## 읽기 전에 알아야 할 것

> **이 문서들이 참조하는 코드는 이 저장소에 더 이상 없다.**
> `src/main/java/ax/clio/analysis/...` 같은 경로, `CodeChunker`·`AnalysisGraph` 같은 클래스명은 전부
> **삭제된 Java 구현**을 가리킨다. 실제 코드를 보려면 초기화 직전 커밋 **`93a87dd`** 를 확인한다.
>
> ```bash
> git show 93a87dd:src/main/java/ax/clio/analysis/pipeline/AnalysisGraph.java
> git checkout 93a87dd -- src/main/java/ax/clio/analysis   # 통째로 꺼내보기
> ```

문서 형식은 `mydocs/workflow-rules.md` 의 플로우를 따른다:
`01-overview`(배경·문제) → `02-plan`(단계·결정 포인트) → `03-decisions`(무엇을 왜 골랐나) → `04-result`(결과·한계).
**재구현할 때 가장 값진 건 `03-decisions`** — 무엇을 시도했고 왜 그 선택을 했는지가 언어와 무관하게 유효하다.
`04-result` 의 "정직한 한계" 절도 같은 함정을 피하는 데 쓴다.

## 무엇이 있나

### RAG 시스템

| 문서 | 내용 |
|------|------|
| `task-code-memory` | 코드 청킹 + 임베딩 + 시맨틱 검색 폴백 (RAG 3.1) |
| `task-issue-memory` | 유사 과거 이슈 검색 (RAG 3.2) |
| `task-decision-memory` | 설계 결정 등록 · 관련 결정 검색 (RAG 3.3) |

### 분석 파이프라인

| 문서 | 내용 |
|------|------|
| `analysis-pipeline-assumptions.md` | 파이프라인 전제 — **먼저 읽을 것** |
| `report-search-plan-schema.md` | 리포트 → 검색 입력 정규화 스키마. **LLM 프롬프트·출력 스키마 설계에 직접 유효** |
| `task-search-improvement` | 코드 후보 탐색·랭킹 개선. **평가 하네스(ground truth 마이닝·랭킹 지표) 포함** |
| `task-flow-tracking` | 관련 코드 흐름 추적 (Controller→Service→Repository) |
| `task-analysis-graph` | **langgraph4j 로 파이프라인 그래프화 + 체크포인트 재개.** 파이썬 LangGraph 로 갈 때 거의 그대로 유효 |

### LLM 활용

| 문서 | 내용 |
|------|------|
| `task-llm-report` | summary · fix · tests LLM 생성 + 실패 시 rule-based 자동 폴백 |
| `task-evidence-check` | LLM 이 근거 밖을 언급했는지 사후 검증 → 경고 (grounding) |

### 설계 원칙 · 전체 맥락

| 문서 | 내용 |
|------|------|
| `task-pipeline-ports` | 단계 포트화(6포트). **대부분 Java 패키징 얘기라 그대로는 안 쓰이지만**, "단계 = 포트 = 교체 단위" 원칙과 단계 간 결합이 어떻게 새는지(04-result 의 정정 기록)는 유효 |
| `clio-tech-review.md` | 전체 기술 리뷰 · 설계 판단 맥락 |

## 유효하지 않은 것

- **로드맵 번호**(`roadmap #7`~`#12`)는 **폐기된 로드맵**을 가리킨다. 요구사항이 새로 확정됐으므로
  현재 계획과 대응되지 않는다. 문서 안의 번호는 그 시절 순서일 뿐이다.
- **Java 패키지 구조 · 레이어드 컨벤션** 관련 서술 전반.
- **`code_files`·`code_symbols` 코드 인덱스 전제** — 코드 인덱스·검색은 새 요구사항에 없다.
  `task-search-improvement`·`task-flow-tracking` 이 이 인덱스를 전제로 하므로, 파이썬에서 다시 만들 땐
  **입력 전제부터 다시 정해야 한다.**
