# 분석 파이프라인 포트화 + 레이어드 패키징 결정 히스토리

각 항목: **선택 → 이유**. plan의 결정 포인트 P1~P8.
전제: 동작 보존 1순위(순수 리팩터). 의존 규칙 — 단계 impl은 `pipeline`(port·contract)에만 의존, 그래프는 포트만 의존.

---

## P1. 포트 경계·개수
**선택: 6단계 1:1 포트** — `ReportPreparer`·`CandidateSearcher`·`FlowTracer`·`MemoryRetriever`·`Scorer`·`ReportGenerator`.
**이유**: 그래프 노드=포트=교체 단위 일치로 "한 단계 교체" 원칙이 선명. report 포트는 내부에 근거검증 포함.

## P2. 계약 record 위치
**선택: `pipeline.contract`에 집중.**
**이유**: 단계가 자기 출력을 소유하면 하류가 상류 패키지를 참조해 결합. 중립 위치에 모으면 모든 단계가
`pipeline`에만 의존 → 원칙 충족. record는 이미 Serializable(#12).

## P3. memory 포트화
**선택: `MemoryRetriever` 포트로 감쌈** (similarIssues·relatedDecisions entry 반환).
**이유**: 그래프가 memory 도메인 concrete(IssueMemoryService·DecisionMemoryService)에 직접 결합하지 않게.
memory 노드의 두 서비스 호출+entry 매핑을 구현으로 이동. memory 도메인의 저장·검색 자체는 그대로.

## P4. 최상위 패키지 구성
**선택: 도메인 유지**(project·code·report·analysis·memory·llm) + analysis 내부를 단계 패키지로.
**이유**: 단계 독립은 `analysis.pipeline` 구조로 충족되고, 제품 도메인 경계는 유지가 응집도 높음.

## P5. 레이어 이름
**선택: `controller` / `service` / `repository` / `entity` / `dto`** (+ 필요한 도메인에만 `config`·`client`
·`embedding`·`vectorsearch` 등 성격 반영).

## P6. 이동 범위 (사용자 선택)
**선택: 전 도메인 일괄.** analysis 포트화·단계 재편 + project·code·report·llm·memory 내부 레이어드까지 한 태스크에.
**이유(사용자)**: 일관된 구조를 한 번에. → 변경 파일이 많으므로 **도메인별로 커밋을 쪼개고** 매 커밋 컴파일·테스트 통과로 위험 관리.

## P7. scoring 추출
**선택: `Scorer` 포트 + `RuleBasedScorer` 구현.** `AnalysisGraph.buildDraft`와 헬퍼(scoreImportance·layerSpan·
describeFlows·searchTerms·reportType·clamp·emptyAsDash)를 통째 이동. 그래프 draftNode는 `scorer.score(...)` 호출만.
**이유**: 오케스트레이터에서 점수 로직 완전 제거(원칙 핵심 교정). report 단계가 텍스트 덮어쓰는 현 동작 유지.

## P8. 동작 보존 검증 / 커밋 분할
**선택**: 매 단계 전체 스위트 그린. `AnalysisGraphTest`(rule-based 점수 보존)가 안전망. 커밋 순서: 포트·계약
스캐폴딩 → scoring 추출 → 단계 포트화 → analysis 패키지 이동 → 도메인별 레이어드(project·code·report·llm·memory).

---

## 목표 패키지 구조 (확정)

```
ax.clio/
├─ common/                         (유지)
├─ project/  controller·service·repository·entity·dto
├─ report/   controller·service·repository·entity·dto
├─ code/     controller·service·repository·entity·dto  (+ scanner·indexer는 service)
├─ llm/      controller·service·repository·entity·dto·client(LlmClient·OpenAiCompatible)
├─ memory/   embedding·code·issue·decision (각 service·repository·entity·vectorsearch)
└─ analysis/
   ├─ pipeline/  AnalysisGraph·AnalysisState + port/(6) + contract/(record)
   ├─ prepare/   RawReportPreparer·LlmReportPreparer
   ├─ search/    CodeCandidateRanker(→CandidateSearcher)
   ├─ flow/      FlowTracer 구현·CodeDependencyGraph
   ├─ scoring/   RuleBasedScorer
   ├─ report/    LlmReportGenerator(writer+verifier)
   └─ job/       controller·service·repository·entity·dto·config
```

## 결정 완료 — 다음 단계
P1~P8 확정. 구현(S1~S6) 착수. 대규모 이동이라 도메인/단계별 커밋 + 매 커밋 그린.
