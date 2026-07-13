# 분석 파이프라인 포트化 + 레이어드 패키징

## 이 문서의 목적

분석 파이프라인의 **각 단계를 포트(인터페이스) 뒤로 독립**시켜, 한 단계의 구현/기술이 통째로 바뀌어도
**다른 단계·오케스트레이터 코드를 수정하지 않게** 만든다. 더불어 각 도메인 패키지 내부를 레이어드
(controller/service/repository/entity/dto)로 정리한다. **순수 구조 리팩터 — 기능 동작은 불변.**

### 이 작업의 핵심 요구(사용자 원칙)

> **분석 파이프라인 중 한 단계의 구현이 통째로 변하거나 기술이 바뀌어도 다른 단계에 영향이 가면 안 된다.
> 다른 단계의 코드를 수정하는 일이 없어야 한다.**

이 원칙이 완료의 **수용 기준**이다.

## 배경 / 현재 상태 진단

파이프라인은 `AnalysisGraph`(langgraph4j 6노드, #12)가 오케스트레이션한다. 그러나 단계 대부분이 **concrete
class에 직접 결합**돼 있어 위 원칙을 못 지킨다.

| 단계 | 현재 형태 | 독립 교체 가능? |
|------|-----------|----------------|
| prepare | `ReportSearchPreparer` **인터페이스**(rawOnly/LLM 2구현) | ✅ 유일하게 포트화됨 |
| search | `CodeCandidateRanker` concrete class | ❌ 인터페이스 없음 |
| flow | `FlowTracer` concrete class | ❌ |
| memory | `IssueMemoryService`/`DecisionMemoryService` concrete | ⚠️ 부분(내부 VectorSearch는 인터페이스) |
| scoring | **`AnalysisGraph`의 private 메서드**(`buildDraft`) | ❌ 클래스조차 없음 |
| report | `LlmReportWriter`+`ReportEvidenceVerifier` concrete | ❌ |

또한 `analysis`(32개)·`memory`(31개) 패키지가 평평하게 커져 단계·레이어 경계가 없다.

## 문제점

1. **그래프가 concrete class에 직접 의존** → 그 단계 기술을 바꾸면 클래스를 뜯어야 하고, 시그니처가 바뀌면
   그래프까지 수정된다(원칙 위반).
2. **점수화가 오케스트레이터 안에 박힘**(`buildDraft`가 `AnalysisGraph` private) → 점수화를 바꾸면
   오케스트레이터 파일을 수정해야 한다. 원칙의 **정면 위반**이며 이번 교정의 핵심.
3. **폴더 이동만으론 해결 불가** — 포트(인터페이스) 추출이라는 코드 변경이 본질이다.

## 개선 방향 (뼈대)

### A. 포트 + 계약 중심(`pipeline`)
- 오케스트레이터가 의존하는 **단계 인터페이스(포트)** 와 **단계 간 데이터 계약(record)** 을 중립 위치에 둔다.
- 각 단계 impl은 포트만 구현하고 계약 record만 주고받는다 — **다른 단계 패키지를 참조하지 않는다.**

### B. 단계별 패키지(구현 격리)
- prepare / search / flow / (memory) / scoring / report 를 각각 패키지로 분리. 그래프는 포트만 주입받는다.
- **scoring을 별도 컴포넌트로 추출**(현재 `buildDraft` → `Scorer` 구현).

### C. 도메인 내부 레이어드
- project·code·report·llm·memory 등 각 도메인 내부를 controller/service/repository/entity/dto로 정리.
- analysis의 잡 라이프사이클(AnalysisJob·Service·Controller·Repository·dto)도 레이어드.

### 목표 구조(초안 — plan에서 확정)

```
analysis/
├─ pipeline/                 오케스트레이션 + 계약(중심)
│   ├─ AnalysisGraph, AnalysisState        (포트에만 의존)
│   ├─ port/     ReportPreparer·CandidateSearcher·FlowTracer·MemoryRetriever·Scorer·ReportGenerator
│   └─ contract/ ReportSearchPreparation·RankedCodeCandidate·CodeFlow·AnalysisResultDraft·*Entry
├─ prepare/   RawReportPreparer, LlmReportPreparer
├─ search/    CodeCandidateRanker(→CandidateSearcher)
├─ flow/      FlowTracer 구현
├─ scoring/   RuleBasedScorer  ← buildDraft 추출(신규)
├─ report/    LlmReportGenerator (writer+verifier)
└─ job/       AnalysisJob*, Service, Controller, Repository, dto (레이어드)
```

## 범위 (초안 — plan에서 확정)

포함(예정): 단계 포트 인터페이스 추출, 점수화(Scorer) 추출, 그래프를 포트 의존으로 전환, 단계 간 계약 정리,
analysis를 단계별 패키지로 재편, 각 도메인 내부 레이어드, **전체 테스트 그린으로 동작 보존 검증.**
제외(초안): 기능/알고리즘 변경, 실제 기술 교체(rule-based 제거 등은 별도), 새 단계 추가, 성능 최적화, MCP·UI.

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **포트 경계·개수**: 6단계 각각 포트 / 일부 통합. 시그니처(입출력 계약) 정의.
- **계약 record 위치**: `pipeline.contract` 집중 / 각 단계가 자기 출력 소유(단, 상류 참조 금지 규칙).
- **memory 포트화 범위**: `MemoryRetriever` 포트로 감쌀지 / 기존 서비스 직접 사용 유지.
- **패키지 최상위 구성**: 도메인 유지(project·code·report·analysis·memory·llm) + 내부 레이어드 / flow 큰단계를 최상위로 승격.
- **레이어 이름**: controller/service/repository/entity/dto (+ config·client 필요시).
- **이동 범위**: analysis 우선 / 전 도메인 일괄.
- **커밋 분할**: 포트 추출 → 패키지 이동 → 도메인 레이어드 등 단계 커밋.
- **동작 보존 검증**: 기존 테스트 그린 + 필요한 계약 테스트.

## 관련 파일

```
src/main/java/ax/clio/analysis/AnalysisGraph.java          - 포트 의존으로 전환(+scoring 추출)
src/main/java/ax/clio/analysis/CodeCandidateRanker.java    - CandidateSearcher 포트 뒤로
src/main/java/ax/clio/analysis/FlowTracer.java             - FlowTracer 포트 뒤로
src/main/java/ax/clio/analysis/LlmReportWriter.java, ReportEvidenceVerifier.java - ReportGenerator 포트 뒤로
src/main/java/ax/clio/analysis/ReportSearchPreparer.java   - 이미 포트(정리·이동)
src/main/java/ax/clio/analysis/*                            - 단계별 패키지 재편
src/main/java/ax/clio/{project,code,report,llm,memory}/     - 도메인 내부 레이어드
```

## 주의사항

1. **동작 보존이 절대 1순위.** 순수 리팩터 — 이동·추출 후 분석 결과가 기존과 동일해야 한다. 전체 스위트 그린이 안전망.
2. **포트 추출이 본질**(폴더 이동만으론 원칙 미충족). 특히 scoring을 오케스트레이터에서 빼는 게 핵심.
3. **대규모 이동**이라 import·package 선언이 광범위하게 바뀐다 — 단계별 커밋으로 쪼개고 매 커밋 컴파일/테스트 통과.
4. 기술 실제 교체(예: rule-based scorer → LLM scorer)는 이번이 아니라 **이 구조가 그걸 무통증으로 받게 만드는 것**이 목표.
5. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```