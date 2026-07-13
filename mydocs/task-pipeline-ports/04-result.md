# 분석 파이프라인 포트화 + 레이어드 패키징 결과

결정(P1~P8) 확정 후 구현 완료. 순수 구조 리팩터 — **동작 불변, 전체 스위트 그린.**

## 무엇을 했나 (3 Phase)

### Phase 1 — 포트 추출 (수용 기준 달성, 핵심)
분석 파이프라인 각 단계를 **6개 포트(인터페이스)** 뒤로 분리하고, 그래프가 포트에만 의존하게 함.

| 포트 | 구현 |
|------|------|
| `ReportPreparer` | `DefaultReportPreparer`(rule/LLM 선택) |
| `CandidateSearcher` | `DefaultCandidateSearcher`(입력구성+랭킹) |
| `FlowAnalyzer` | `DefaultFlowAnalyzer` |
| `MemoryRetriever` | `DefaultMemoryRetriever`(#8+#9, `MemoryContext` 반환) |
| `Scorer` | `RuleBasedScorer` ← `AnalysisGraph.buildDraft` **완전 추출** |
| `ReportGenerator` | `LlmReportGenerator`(#10 리포트+#11 검증) |

→ **점수 로직을 오케스트레이터에서 완전히 제거**. `AnalysisGraph` 노드는 포트 호출만(로직 0).

### Phase 2 — analysis 단계별 하위 패키지
`pipeline`(포트·계약·오케스트레이션) / `prepare` / `search` / `flow` / `scoring` / `report` / `job`.
단계 impl은 `pipeline`(port·contract)에만 의존, 서로 참조하지 않음.

### Phase 3 — 전 도메인 레이어드
- project/report/llm/code: `controller·service·repository·entity·dto`(+ llm은 `client`·`config`).
- memory: RAG 기능별 `embedding·code·issue·decision`.

## 수용 기준 충족 확인

> **한 단계의 구현/기술이 바뀌어도 다른 단계·오케스트레이터 코드가 안 바뀐다.**

- 그래프는 6개 **포트 인터페이스에만** 의존(concrete 0). 한 단계를 갈아끼우려면 그 단계 impl(자기 패키지)만
  교체·수정하면 되고, 그래프·다른 단계는 무변경.
- 예: 향후 `RuleBasedScorer` → `LlmScorer`로 교체 시 `scoring` 패키지에 새 `Scorer` 구현만 추가(그래프 불변).
- 각 단계 impl은 `pipeline`(port·contract)에만 의존 → 단계 간 결합 없음.

## 검증

- 전 과정 **전체 스위트 그린**(Phase 1/2/3 각각). `AnalysisGraphTest`가 포트 조합·rule-based 점수 보존을 검증
  (동작 보존 안전망). `@SpringBootTest contextLoads`가 재편된 전 패키지의 빈 부팅 확인.
- 파일 이동·import는 스크립트로 처리하고 **컴파일러를 안전망**으로 반복 수정 → 누락 import·가시성 오류 0.

## 구현 중 처리한 것

- `IssueMemoryService.embeddingText`를 **public화**(memory를 issue/decision 패키지로 나누며 도메인 간 접근 발생).
- `RuleBasedScorer` public화(테스트 인스턴스화). 그 외 단계 impl은 package-private @Component 유지(캡슐화).
- 인라인 FQN(`ax.clio.memory.CodeMemorySearchService` 등)도 새 경로로 치환.
- 분석/메모리 테스트를 대상 하위 패키지로 이동(package-private 접근·관례).

## 최종 구조

```
analysis/ pipeline · prepare · search · flow · scoring · report · job
memory/   embedding · code · issue · decision
project/report/code/llm/  controller · service · repository · entity · dto (+ llm: client·config)
common/ (유지)
```

## 정직한 한계 / 남은 것

1. **package 순환**: `pipeline ↔ job`(그래프가 AnalysisJob 참조, job이 계약 record 참조). Java 허용이나 이상적이진 않음.
   필요 시 job이 참조하는 계약을 더 좁히는 후속 여지.
2. prepare의 raw/LLM 선택은 `DefaultReportPreparer`에 있음 — 이후 전략을 더 쪼갤 수 있음(범위 밖).
3. 실제 기술 교체(rule-based → LLM scorer 등)는 이번이 아니라 **이 구조가 그걸 무통증으로 받게 만든 것**이 목표.
4. 순수 리팩터라 로드맵 항목 진행도는 불변(기능 추가 없음).
