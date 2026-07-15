# 패키지 컨벤션 정리 구현 계획

overview 컨펌 완료. 이 문서는 **구현 단계(S1~S7)**와 **결정 포인트(P1~P8)**를 담는다.
결정은 3단계에서 하나씩 확정하고 `03-decisions.md`에 기록한다.

> **동작 보존 1순위**: 순수 리팩터 — 전체 스위트 그린 유지.
> **수렴할 규칙**: 컨트롤러(HTTP 표면)를 가진 도메인은 레이어드, 내부 컴포넌트 패키지는 평평.

---

## overview 이후 추가로 발견한 것 (중요)

plan 근거를 모으다 **직전 작업의 수용 기준이 실제로는 깨져 있는 것**을 발견했다.
`task-pipeline-ports/02-plan.md` 가 "헌법"으로 선언한 의존 규칙:

```
pipeline(port + contract)  ◀── prepare / search / flow / memory / scoring / report
단계 impl 은 pipeline(port·contract)에만 의존, 서로 참조하지 않는다.
```

`04-result.md` 는 이를 달성했다고 서술한다("각 단계 impl은 pipeline에만 의존 → 단계 간 결합 없음").
**실제로는 아니다.**

### A. `scoring → flow` — 진짜 결합 (위반)

```java
// analysis/scoring/RuleBasedScorer.java:97
.map(CodeDependencyGraph::layerOf)                       // flow 패키지의 클래스
.filter(layer -> layer != CodeDependencyGraph.UNKNOWN_LAYER)
```

`RuleBasedScorer` 가 `analysis.flow.CodeDependencyGraph` 의 static 멤버를 직접 호출한다.
**flow 구현을 갈아끼우면 scoring 이 깨진다** — 수용 기준("한 단계가 바뀌어도 다른 단계 코드 무변경")의 정면 위반.

다만 원인은 단순하다. `layerOf(String role)` 는 그래프 상태와 무관한 **순수 함수**로,
role 문자열(`CONTROLLER`/`SERVICE`/`REPOSITORY`/`ENTITY`) → 레이어 인덱스 매핑일 뿐이다.
그리고 그 role 어휘는 이미 **계약**(`FlowNode.role()`)에 속한다 → `CodeDependencyGraph` 에 있는 게 잘못된 자리.

### B. `report → prepare` — 장식 (실질 결합 아님)

`LlmReportWriter` 의 `import ax.clio.analysis.prepare.LlmReportSearchPreparer` 는 **javadoc `{@link}` 전용**.
실제 코드 참조 0. import 만 지우면 끝.

### C. 포트가 JPA 엔티티를 받는다 — `pipeline ↔ job` 순환의 진짜 원인

포트 6개 중 5개가 영속 엔티티를 시그니처에 노출한다.

| 포트 | 시그니처 | 유출 |
|------|----------|------|
| `ReportPreparer` | `prepare(AnalysisJob)` | `analysis.job` |
| `ReportGenerator` | `generate(AnalysisJob, draft)` | `analysis.job` |
| `Scorer` | `score(BugReport, ...)` | `report.entity` |
| `CandidateSearcher` | `search(BugReport, ...)` | `report.entity` |
| `MemoryRetriever` | `retrieve(BugReport)` | `report.entity` |
| `FlowAnalyzer` | `trace(Long, List<RankedCodeCandidate>)` | 없음 (유일하게 깨끗) |

`pipeline` 은 모두가 의존하는 **중립 계약 자리**여야 하는데, 그 자신이 `analysis.job` 을 의존하고
`analysis.job` 은 다시 `pipeline` 을 의존한다 → `04-result.md` 가 "한계 1번"으로 적어둔 **패키지 순환의 실체**.
`pipeline/port` 를 분리해도 이 순환은 그대로 남는다.

---

## 구현 단계

| 단계 | 내용 | 대응 결정 |
|------|------|-----------|
| **S1** | `report → prepare` 장식 import 제거 (javadoc 표현 조정) | — (자명) |
| **S2** | `scoring → flow` 결합 해소 — `layerOf`/`UNKNOWN_LAYER` 를 계약으로 이동 | P3 |
| **S3** | `analysis/pipeline` → `port/` + `contract/` 분리 | P1, P2 |
| **S4** | `DefaultMemoryRetriever` → `analysis/memory/` 이동 | — (자명) |
| **S5** | `analysis/job` 레이어드 | P6 |
| **S6** | `memory/decision` 레이어드 | P5 |
| **S7** | 테스트 패키지 정렬 + 규칙 문서화 + `task-pipeline-ports/04-result.md` 교정 | P7 |

S1·S2 를 앞에 두는 이유: **단계 간 결합을 먼저 끊어야** 이후 패키지 이동이 의미를 갖는다.
(결합을 남긴 채 폴더만 나누면 컨벤션은 맞아 보이고 원칙은 여전히 깨진 상태가 된다.)

---

## 결정 포인트

### P1. `contract` 내부 구성
계약 record·enum 13개(`ReportSearchPreparation`·`RankedCodeCandidate`·`CodeFlow`·`FlowNode`·
`AnalysisResultDraft`·`MemoryContext`·`*Entry`·`ReportSearchInput*`·`GeneratedReport`)를

- **(a) `contract/` 에 평평하게** — 13개 한 폴더. **[추천]** 계약은 원래 한 덩어리로 읽히는 게 낫고,
  단계별로 나누면 "이 record 는 누구 것인가" 논쟁이 생겨 P2(중립 위치)의 취지가 흐려진다.
- (b) `contract/prepare`·`contract/search`… 단계별 그룹핑 — 찾기는 쉬우나 소유권 착시.

### P2. `AnalysisGraph`·`AnalysisState` 위치
- **(a) `pipeline/` 루트 유지** (`pipeline/port`, `pipeline/contract` 와 형제). **[추천]** 오케스트레이터는
  포트도 계약도 아니고, "pipeline 그 자체"라는 게 드러난다.
- (b) `pipeline/graph/` 신설.

### P3. `scoring → flow` 결합 해소 방법 (S2)
- **(a) `layerOf`/`UNKNOWN_LAYER` 를 계약으로 이동** — role 어휘의 소유자가 계약(`FlowNode.role()`)이므로
  제자리를 찾아주는 것. flow·scoring 둘 다 계약만 의존하게 된다. **[추천]** 원인에 정확히 대응하고 중복도 없다.
- (b) role 을 `String` → enum 으로 승격하고 레이어를 enum 에 내장 — 가장 깔끔하나 계약·파서·테스트가
  함께 바뀌어 순수 리팩터 범위를 넘는다.
- (c) `scoring` 이 자체 `layerOf` 복제 — 결합은 끊기나 매핑이 두 곳에 생겨 드리프트 위험.

### P4. 포트의 JPA 엔티티 유출 / `pipeline ↔ job` 순환 (위 C)
- **(a) 이번 범위 제외, 별도 태스크로 기록** — **[추천]** 포트 5개 시그니처 + 그래프 + 단계 impl 전부와
  테스트가 바뀌는 **설계 변경**이지 컨벤션 정리가 아니다. 이번 작업(구조 수렴)과 섞으면 리뷰가 불가능해지고
  "동작 보존" 안전망도 약해진다. `04-result.md` 한계 1번을 정확한 원인으로 갱신만 해둔다.
- (b) 이번에 같이 해소 — 계약 record(예: `AnalysisRequest`) 도입해 엔티티를 포트에서 제거.
- (c) 그대로 두고 기록도 안 함 — 비추천.

### P5. `memory/decision` 의 vectorsearch 3종 배치 (S6)
`DecisionVectorSearch`(interface) + `InMemoryDecisionVectorSearch` + `PgVectorDecisionVectorSearch`
- **(a) `vectorsearch/` 하위 패키지** — **[추천]** 직전 P5 가 "필요한 도메인에만 `vectorsearch` 등 성격 반영"을
  이미 허용했고, 레이어(controller/service/…)가 아닌 **인프라 어댑터**라 성격이 다르다.
- (b) `service/` 에 포함 — 서비스 5개가 섞여 성격이 흐려진다.
- (c) `repository/` 에 포함 — pgvector 는 저장소성이나, `InMemory` 구현이 함께 있어 어색.

### P6. `analysis/job` 의 `AnalysisWorker`·`AnalysisTaskExecutorConfig` 배치 (S5)
- **(a) `AnalysisWorker` → `service/`, `AnalysisTaskExecutorConfig` → `config/`** — **[추천]**
  worker 는 비동기 실행 서비스, config 는 `llm/config` 선례가 이미 있다.
- (b) 둘 다 `service/`.
- (c) `worker/` 별도 패키지 — 파일 1개짜리 디렉터리.

### P7. 규칙 문서화 위치 (S7)
- **(a) `mydocs/workflow-rules.md` 에 "패키지 규칙" 절 추가** — **[추천]** 이미 "절대 준수" 권위 문서이고
  작업 시작 전 반드시 읽는 문서라 실제로 지켜질 확률이 가장 높다.
- (b) 루트 `CLAUDE.md` 신설 — 현재 이 레포엔 없다. 새로 만들면 권위 문서가 둘로 갈린다.
- (c) `package-info.java` 에 분산 — 지역적이라 전체 규칙이 안 읽힌다.
- (d) (a) + 각 패키지 `package-info.java` 보강 — 중복이나 발견성은 최고.

### P8. 커밋 분할
- **(a) S1~S7 단계별 7커밋** — **[추천]** PR 커밋 목록만으로 전개가 읽히고(워크플로우 규칙),
  이동 커밋과 로직 커밋이 섞이지 않아 리뷰 가능.
- (b) "결합 해소(S1·S2)" / "패키지 이동(S3~S6)" / "문서(S7)" 3커밋.

---

## 검증

- **매 단계 전체 스위트 그린.** `AnalysisGraphTest`(포트 조합·rule-based 점수 보존)가 동작 보존 안전망.
- `@SpringBootTest contextLoads` 가 재편된 패키지의 빈 부팅 확인.
- S2 는 유일하게 **코드가 움직이는** 단계 → `layerSpan` 결과 불변을 테스트로 확인.
- 이동·import 는 컴파일러를 안전망으로.

## 주의사항

1. S2 이후 `CodeDependencyGraph` 의 `layerOf` 가 사라지므로 flow 내부 사용처도 함께 정리.
2. S3 은 import 가 광범위하게 바뀐다(계약 13개 × 전 단계 impl + 테스트).
3. P4 를 (a)로 정하면 `pipeline ↔ job` 순환은 **이번 작업 후에도 남는다.** 04-result 교정 시 이를 정직하게 적는다.
4. 워크플로우: 이 plan 컨펌 → P1~P8 하나씩 결정·커밋 → result.
