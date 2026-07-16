# Clio — AI-native 버그 분석 엔진 · 기술 리뷰

> 버그 리포트를 **결함 위치·우선순위·수정 방향**으로 바꾸는 LLM-증강 코드 분석 엔진
> Java 21 / Spring Boot / pgvector · 약 5,700 LOC
> 키워드: IR-based Bug Localization · RAG · Code Retrieval · Lean SWE Pipeline · langgraph4j 오케스트레이션

*(발표 슬라이드 덱과 동일 내용의 문서 버전. 슬라이드 1~13에 대응.)*

---

## 1. 문제 & 포지셔닝 — 무슨 문제를, 어디에 서서 푸나

**문제.** 자연어 버그 리포트를 입력받아 **어느 코드가 원인인지 국소화(localize)** 하고, 수정 난이도·위험도·우선순위와 방향을 제시한다.

이는 SE 연구의 **IR-based Bug Localization(IRBL)** 과제 — 리포트↔소스 유사도로 결함 파일을 랭킹.
`survey: DL meets IRBL, ACM CSUR 2025`

**지형.** 최근 흐름은 **lean pipeline** — 무거운 에이전트 루프보다 **정밀 검색+국소화**가 핵심이라는 결과.
`Agentless (Xia+ 2024) · Moatless`

Clio의 *구조화→검색→흐름→점수화→리포트→검증* 파이프라인은 이 **localize-then-explain lean 노선**과 같은 계열이다.

> **설계 스탠스** — 판단(검색·랭킹·점수)은 결정론적 IR·rule이, LLM은 **설명·요약**만. 검색이 약하면 추론도 헛돈다 → **retrieval-centric**. 벤치마크 지향점은 `SWE-bench`.

---

## 2. End-to-end — 버그 리포트 → 우선순위

```
리포트 등록 → 분석 잡(PENDING)   [async 트리거]
      → 파이프라인 6단계 (구조화·검색·흐름·메모리·점수화·리포트)
      → 우선순위 + 근거   [결과 polling]
```

사전에 코드를 파싱·인덱싱(JavaParser)해 두고, 리포트가 들어오면 관련 코드·영향 흐름·과거 유사 이슈를 모아 **중요도·난이도·위험도**를 산출하고 사람이 읽을 수정·테스트 방향을 붙인다. 분석은 비동기.

> **우선순위 = 점수화(draft) 단계 산출.** LLM은 그 위에 설명을 얹고, 근거 검증이 뒤따른다.

---

## 3. 엔진은 어디서 무엇을 쓰나 (단계별 구성)

| 단계 · 포트 | 쓰는 엔진 | 산출 |
|---|---|---|
| ① prepare · `ReportPreparer` | LLM 구조화(`LlmClient`, JSON 검증) / rule | 키워드·도메인·증상 |
| ② search · `CandidateSearcher` | 키워드(`CodeSearchService`) + 시맨틱(`EmbeddingClient`+pgvector) | 관련 코드 후보 |
| ③ flow · `FlowAnalyzer` | 심볼 의존관계(`CodeSymbolRepository`) | 영향 흐름 C→S→R |
| ④ memory · `MemoryRetriever` | Issue(#8)+Decision(#9) 임베딩·벡터검색 | 유사이슈·관련결정 |
| **⑤ draft ★ · `Scorer`** | **rule-based 가중합**(파일수·도메인·흐름폭·Entity/Repo) | **중요도·난이도·위험도** |
| ⑥ report · `ReportGenerator` | `LlmReportWriter` + `ReportEvidenceVerifier`(#11) | 요약·수정·테스트 + 근거검증 |

> **포트 뒤 분리** — ⑤를 `LlmScorer`로, ②를 밀집검색으로 바꿔도 **한 단계만 교체**. 오케스트레이터·타 단계 불변.

---

## 4. 설계 원칙 — 왜 이렇게 설계했나

- **판단 vs 설명 분리** — 검색·점수화는 결정론적 IR·rule, LLM은 설명만. 비결정성을 **결과의 겉면**으로 격리 → 재현성·디버깅.
- **Retrieval-centric** — 검색 품질이 상한을 정한다(Agentless/Moatless 관찰). 무거운 추론보다 **정밀 국소화**에 투자.
- **Grounding + 검증** — LLM 입력을 검색 근거로 한정(예방) + 근거 밖 언급 사후 탐지(검증). 환각을 **구조로** 억제.
- **무중단 폴백** — LLM 실패 시 rule-based 결과로 자동 degrade. 설명은 없어도 **점수·근거는 항상** 나온다.

> **포트-앤-어댑터** — 6단계를 인터페이스로 분리해 **기술 교체를 국소화**. rule→LLM 전환의 활주로.

---

## 5. 진행 현황 (로드맵 13항목)

**완료 3 · 부분(뼈대) 7 · 보류 2 · 다음 1**

| # | 항목 | 상태 |
|---|------|------|
| 1 | 분석 결과 구조화 | ✅ 완료 |
| 3 | 코드 흐름 추적 | ✅ 완료 |
| 5 | 검색 품질(랭킹) | ✅ 완료 |
| 4 | 점수화 규칙 | 🔶 부분 |
| 7 | Code Memory | 🔶 부분 |
| 8 | Issue Memory | 🔶 부분 |
| 9 | Decision Memory | 🔶 부분 |
| 10 | LLM 리포트 생성 | 🔶 부분 |
| 11 | 근거 검증 | 🔶 부분 |
| 12 | LangGraph 오케스트레이션 | 🔶 부분 |
| 2 | 관련 테스트 탐색 | ⏭️ 보류 |
| 6 | Web UI | ⏭️ 보류 |
| 13 | MCP (외부 Agent 도구화) | ◀ 다음 |
| + | 포트화·레이어드 리팩터 | ✅ 완료 |

*“부분” = 구조·배선 완성, 실 의미검색 등 마지막 스위치는 미개방(→ 고도화 로드맵).*

---

## 6. 최근 큰 작업

**파이프라인 그래프화 (langgraph4j)** — 절차적 로직을 StateGraph 6노드로. 단계별 체크포인트로 **실패 단계부터 재개** — 향후 에이전트 루프의 토대.

**파이프라인 포트화 + 레이어드** — 각 단계를 포트 뒤로, 점수화를 오케스트레이터에서 분리. **한 단계 통째 교체가 다른 단계 무변경** — rule→LLM 전환 대비.

> 둘 다 순수 구조 개선이면서 **다음 단계(에이전트화·기술 교체)의 활주로**를 깔았다. 전 과정 테스트 그린.

---

## 7. 기술 지형 — SOTA 대비 지금 어디에 있고, 어디로 가나

```
① 어휘 IR            → ② 밀집·RAG (현재)   → ③ 계층 국소화        → ④ 에이전트
BM25·rVSM              임베딩 검색+LLM 설명     file→class→line        reason–act–reflect
BugLocator(2012)       CodeBERT류·RAG          Agentless(2024)        SWE-agent·Reflexion
```

**우리 위치 (정직하게)**
- 랭킹은 **어휘 IR + rule**(①에 가까움) — BugLocator 세대 베이스라인
- 밀집검색·RAG 배선은 있으나 **임베딩이 로컬(가짜)** → ②는 아직 절반
- 그래프는 **선형** — ④의 순환·자가교정 없음

**SOTA와의 간극**
- 하이브리드 **융합(RRF)·리랭킹** 부재 → 검색 정밀도 손해
- **코드 특화 임베딩** 미적용(generic 예정)
- 환각 검증이 **참조 체크** 수준(CoVe 등 미도입)

---

## 8. 고도화 로드맵 — 무엇을, 왜(근거)

| 영역 | 기법 · 근거 | 기대효과 |
|---|---|---|
| 검색 융합 | Hybrid **BM25+Dense → RRF** 융합 `Cormack+ 2009` (rank만 사용 → 스케일 문제 해소) | 어휘+의미 결합, 벤치 +~7% NDCG |
| 재순위 | **Cross-encoder rerank** — retrieve 50→rerank 10 `two-stage` | +5~15 NDCG@10, <200ms |
| 코드 임베딩 | generic → **GraphCodeBERT / UniXcoder / CodeT5+** (구조·데이터흐름 반영) | 코드 의미검색 품질↑ |
| 국소화 | **계층적** file→class→method `Agentless 2024` | 결함 위치 정밀도↑ |
| 환각 완화 | **Chain-of-Verification** `Dhuliawala+ 2023` + faithfulness 지표 | 근거 이탈·오탐 감소 |
| 에이전트화 | **ReAct** `Yao+ 2022` / **Reflexion** `Shinn+ 2023` — 검증 실패→재검색 자가교정 루프 | 순환·자기수정(그래프 위) |
| 평가 | **SWE-bench(-lite)** + Recall@k·MRR·NDCG 하네스 | 표준 벤치마크로 회귀 검증 |

*포트 구조라 위 대부분은 **해당 단계 어댑터 교체**로 흡수 — 파이프라인 재작성 불필요.*

---

## 9. 제안 — 우선순위 보드 (Jira 스타일)

리포트를 모아 **보드로 정렬**: 상태 컬럼 + 카드에 분석 우선순위. 무엇부터 손대야 할지 한눈에.

```
대기(2)               분석중(1)            완료(3)
─ 환불 상태 안 바뀜    ─ 로그인 세션 만료   ─ 결제 취소 미반영   [78]
─ 쿠폰 중복 적용                            ─ 주문 목록 지연     [62]
                                            ─ 알림 중복 발송     [54]
```

> **큰 변경 아님** — 리포트 목록에 점수를 노출(또는 denormalize)하는 작은 추가. 파이프라인·포트 구조는 그대로.

---

## 10. 오늘 정할 것 (논의)

**꼭 정할 핵심 2**
- **① 방향** — rule→LLM 점진 교체? 어느 단계부터? (이후 우선순위를 좌우)
- **② 임베딩** — 코드 특화 dense 검색 켤지·모델·비용 (Memory 3종 동시 실효화)

**②에 의존해 따라오는 것**
- **③ 신뢰성** — CoVe/자동제거로 강화할지
- **④ 인프라** — pgvector ANN·영속 재개 시점
- **⑤ 범위** — 보드 / MCP / 평가 하네스 순서

> **레버리지 순서** — ② 코드 임베딩+RRF → 리랭커 → 평가(SWE-bench) → 에이전트화. 각 단계는 어댑터 교체로.

---

## 11. 결정 매트릭스 — 선택지 · 추천 · 근거

| 안건 | 선택지 | 추천 · 근거 |
|---|---|---|
| **① 방향** | 유지 / **점진 교체** / 전면 에이전트 | 점진 — 점수화·구조화부터. 검색은 IR 유지(정밀·저비용). eval로 A/B |
| **② 임베딩·검색** | generic API / **코드특화 self-host**(GraphCodeBERT·UniXcoder) + **RRF·리랭커** | 코드특화 + 하이브리드가 정공법. 1프로젝트 PoC+비용측정 |
| ③ 신뢰성 | 경고 유지 / 자동제거 / **CoVe** / LLM-judge | ②먼저. faithfulness 지표 붙이고 CoVe는 이후 |
| ④ 인프라 | 보류 / pgvector **HNSW** / postgres-saver(베타) | 데이터 쌓인 뒤 ANN 벤치. 조기 최적화 금지 |
| ⑤ 우선순위 | 보드 / MCP / 평가 하네스 / … | ② → 보드+UI → SWE-bench 평가 → 에이전트화 |
| ＋ 보드 | 컬럼 상태 vs 버킷 / A·B·**C(denormalize)** | 만든다·상태 컬럼·C안(SQL 정렬). 하루치 |

---

## 12. 참고 문헌 · 핵심 개념

**Bug Localization**
- **BugLocator** (Zhou+, ICSE 2012) — rVSM 리포트↔코드 유사도
- **DNNLOC** — DL+IR 결합의 시초
- survey: **When DL Meets IR-based Bug Localization** (ACM CSUR 2025)

**Agentic SWE**
- **SWE-bench** (Jimenez+, 2024) — 실제 이슈 해결 벤치
- **Agentless** (Xia+, 2024) — localize-repair-validate, lite 27.3%·$0.34/issue
- **SWE-agent** (Yang+, 2024) — Agent-Computer Interface · Moatless(검색 중심)

**Retrieval / Embedding**
- **RRF** (Cormack+, SIGIR 2009) — rank 기반 융합
- Cross-encoder **rerank** — retrieve→rerank 2단
- **CodeBERT / GraphCodeBERT / UniXcoder / CodeT5+** — 코드 임베딩

**Reliability / Agents**
- **Chain-of-Verification** (Dhuliawala+, 2023) — 검증 단계로 환각↓
- **ReAct** (Yao+, 2022) · **Reflexion** (Shinn+, 2023) — 추론·자가교정
- RAG faithfulness · LLM-as-judge (한계 인지)

> Clio = IRBL 베이스라인 위에 RAG·LLM·오케스트레이션을 얹고, 위 기법으로 SOTA 방향을 겨냥.

---

*참고: 인용한 연구의 저자·연도는 널리 알려진 공개 문헌 기준. 발표/제출 전 정확한 인용(연도·수치)은 한 번 확인 권장.*
