# 분석 파이프라인 포트화 + 레이어드 패키징 구현 계획

overview 컨펌 완료. 이 문서는 **구현 단계**와 **결정 포인트(P1~P8)**를 담는다.
결정은 3단계에서 하나씩 확정하고 `03-decisions.md`에 기록한다.

> **수용 기준**: 한 단계의 구현/기술이 바뀌어도 다른 단계·오케스트레이터 코드가 안 바뀐다.
> **동작 보존 1순위**: 순수 리팩터 — 전체 스위트 그린 유지.

## 의존 규칙 (이 작업의 헌법)

```
pipeline(port + contract)  ◀── prepare / search / flow / memory / scoring / report  (각 단계 impl)
        ▲
    AnalysisGraph (포트에만 의존)
```
- 단계 impl은 **`pipeline`(port·contract)에만 의존**. 다른 단계 패키지를 절대 참조하지 않는다.
- 그래프는 **포트 인터페이스만 주입**받는다(concrete 금지).
- 계약 record는 `pipeline.contract`에 모은다(상류 단계 패키지 참조 방지).

## 구현 단계 (결정에 종속, 매 단계 컴파일/테스트 통과 커밋)

- **S1. 포트·계약 스캐폴딩** — `pipeline.port`(인터페이스 6개) + `pipeline.contract`(record 이동). 관련: **P1, P2**
- **S2. scoring 추출** — `AnalysisGraph.buildDraft` → `Scorer` 구현으로 분리. 관련: **P7**
- **S3. 나머지 단계 포트 뒤로** — search/flow/report/memory를 포트 구현으로(그래프는 포트 의존). 관련: **P1, P3**
- **S4. 단계별 패키지 재편** — analysis 하위를 prepare/search/flow/scoring/report/job/pipeline로 이동. 관련: **P4, P6**
- **S5. 도메인 내부 레이어드** — (범위에 따라) project·code·report·llm·memory 내부 정리. 관련: **P5, P6**
- **S6. Result** — `04-result.md`.

---

## 결정 포인트 (하나씩 확정)

### P1. 포트 경계·개수
- (a) **6단계 1:1 포트**: `ReportPreparer`·`CandidateSearcher`·`FlowTracer`·`MemoryRetriever`·`Scorer`·`ReportGenerator`.
- (b) 일부 통합(예: report+verify를 한 포트).
- **AI 추천: (a) 6포트, 그래프 노드와 1:1.** 노드=포트=교체 단위가 일치해 "한 단계 교체" 원칙이 가장 선명.
  report 포트 내부에 검증을 포함(LlmReportGenerator가 writer+verifier 조합)해 노드 경계와 맞춘다.

### P2. 계약 record 위치
- (a) **`pipeline.contract`에 집중**.
- (b) 각 단계가 자기 출력 record 소유(하류가 참조).
- **AI 추천: (a) 집중.** (b)는 하류가 상류 패키지를 참조하게 돼 결합이 생긴다. 계약을 중립 위치에 모으면 모든
  단계가 `pipeline`에만 의존 → 원칙 충족. (record는 이미 Serializable, #12에서 부여.)

### P3. memory 포트화 범위
- (a) **`MemoryRetriever` 포트로 감쌈**(similarIssues·relatedDecisions entry 반환).
- (b) 그래프가 `IssueMemoryService`/`DecisionMemoryService` 직접 사용 유지.
- **AI 추천: (a) 포트로 감쌈.** 그래프가 memory 도메인 concrete에 직접 결합하지 않게. memory 노드가 두 서비스
  호출+entry 매핑하는 로직을 `MemoryRetriever` 구현으로 이동. memory 도메인 자체(저장·검색)는 그대로.

### P4. 최상위 패키지 구성
- (a) **도메인 유지**(project·code·report·analysis·memory·llm) + analysis 내부를 단계 패키지로.
- (b) flow 큰 단계를 최상위 도메인으로 승격(analysis 해체).
- **AI 추천: (a).** 원칙(단계 독립)은 `analysis.pipeline` 구조로 충족되고, 제품 도메인 경계(리포트·프로젝트·
  코드·메모리·LLM)는 유지하는 게 응집도가 높다. 단계 승격(b)은 제품 도메인과 파이프라인 단계를 섞어 혼란.

### P5. 레이어 이름
- **AI 추천: `controller` / `service` / `repository` / `entity` / `dto`** (+ 필요한 도메인에만 `config`·`client`).
  llm 도메인엔 외부 통합용 `client`(LlmClient·OpenAiCompatible), memory엔 `embedding`·`vectorsearch` 등 성격 반영.

### P6. 이동 범위 (중대 — 태스크 크기 결정)
- (a) **analysis 포트화·단계 재편만 이번에**(원칙 핵심), 나머지 도메인 레이어드는 후속.
- (b) analysis + 전 도메인 레이어드까지 한 태스크에.
- **AI 추천: (a) analysis 우선 완결.** 원칙의 본질은 analysis 파이프라인이라 여기에 집중해 리뷰 가능한 크기로.
  project·code·report·llm·memory 내부 레이어드는 위험 낮은 단순 이동이라 **후속 커밋 or 별도 태스크**로 분리.
  → 사용자와 상의(한 번에 다 원하면 (b)).

### P7. scoring 추출 방식
- Scorer 포트: `(report, preparation, candidates, flows, similarIssues, relatedDecisions) → AnalysisResultDraft`
  (현 `buildDraft`의 rule-based draft 생산 = 점수·rationale·relatedCode·유사이슈/결정 entry + placeholder 텍스트).
- **AI 추천**: `Scorer` 포트 + `RuleBasedScorer` 구현으로 `buildDraft`와 헬퍼(scoreImportance·layerSpan 등)를
  통째 이동. 그래프 draftNode는 `scorer.score(...)` 호출만. **오케스트레이터에서 점수 로직 완전 제거**(원칙 핵심 교정).
  report 단계가 summary/fix/tests를 덮어쓰는 현 동작 유지.

### P8. 동작 보존 검증 / 커밋 분할
- **AI 추천**: 매 단계 전체 스위트 그린. 기존 `AnalysisGraphTest`(rule-based 점수 보존)가 리팩터 안전망 —
  포트 전환·scoring 추출 후에도 동일 점수 유지 확인. 커밋: 포트·계약 스캐폴딩 → scoring 추출 → 단계 포트화 →
  패키지 이동 → (범위면) 도메인 레이어드.

---

## 결정 포인트 요약표

| ID | 주제 | AI 추천 |
|----|------|---------|
| P1 | 포트 개수 | 6포트, 그래프 노드와 1:1 |
| P2 | 계약 위치 | pipeline.contract 집중 |
| P3 | memory | MemoryRetriever 포트로 감쌈 |
| P4 | 최상위 | 도메인 유지 + analysis 내부 단계화 |
| P5 | 레이어 이름 | controller/service/repository/entity/dto(+config·client) |
| P6 | 이동 범위 | analysis 우선(나머지 도메인은 후속) |
| P7 | scoring | Scorer 포트 + RuleBasedScorer로 buildDraft 추출 |
| P8 | 검증 | 전체 그린 + 단계별 커밋 |

## 완료 기준(이 작업)

- 그래프가 **포트 인터페이스에만** 의존하고, 점수화가 오케스트레이터에서 완전히 빠진다.
- 각 단계 impl은 `pipeline`(port·contract)에만 의존하고 서로를 참조하지 않는다 → 한 단계 교체 시 그 impl만 수정.
- 분석 결과가 기존과 **동일**(동작 보존). 전체 스위트 그린.

## 열린 질문 / 리스크

- 대규모 package/import 변경 — 단계별 커밋으로 위험 관리, 매 커밋 컴파일·테스트.
- prepare의 raw/LLM 선택 로직(현 `AnalysisGraph.prepare`)을 어디 둘지 — 포트 뒤 전략으로 옮길지 구현서 확정(P1/P7 인접).
- P6에서 analysis만 하면 나머지 도메인은 이번 태스크 후에도 평평한 채로 남음(의도).
