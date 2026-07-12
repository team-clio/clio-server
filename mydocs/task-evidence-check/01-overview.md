# LLM 근거 검증 (roadmap #11 / 4.4)

## 이 문서의 목적

#10에서 LLM이 생성한 리포트(summary·recommendedFix·recommendedTests)가 **검색된 코드 근거에 없는
파일·클래스를 언급**하면 제거하거나 경고한다. roadmap "추천 개발 순서" #11 (4.4 근거 검증).

목표(roadmap 4.4):

> - LLM이 검색 결과에 없는 파일이나 클래스를 언급하지 못하게 한다.
> - 근거가 부족한 내용은 제거하거나 경고한다.
>
> 완료 기준: **LLM 결과가 항상 검색된 코드 근거에 묶여 있어야 한다.**

> **#10과의 관계**: #10은 **예방**("지어내지 말라" 프롬프트 지시 + 입력 근거 한정)까지만 했다. 이번 #11은
> LLM이 그 지시를 **실제로 지켰는지 사후 검증**한다. 예방(사전) + 검증(사후) 2단 방어.

## 배경 / 현재 구현 상태

### 이미 있는 것 (#11이 얹힐 기반)

| 요소 | 위치 | 비고 |
|------|------|------|
| LLM 리포트 생성 | `analysis.LlmReportWriter` | summary·fix·tests 3필드(#10) |
| 생성 결과 record | `analysis.GeneratedReport` | **검증 대상 텍스트** |
| 근거(관련 코드) | `analysis.RelatedCodeEntry` | filePath·symbolName·symbolRole·lineNumber… **허용 근거 소스** |
| 흐름 근거 | `analysis.CodeFlow`/`FlowNode` | 흐름에 등장하는 클래스도 근거 후보 |
| enrich 연동 | `analysis.AnalysisWorker#enrichWithLlmReport` | write→검증→적용 지점 |
| draft with-er | `AnalysisResultDraft#withGeneratedReport` | 3필드 교체 |

### 지금의 한계 (문제 지점)

- #10은 프롬프트로 "입력 밖 파일/클래스 지어내지 말라"고 **지시만** 한다. LLM이 이를 어기고 없는 클래스
  (`OrderValidator` 등)를 언급해도 **아무도 잡지 않는다.** 그대로 사용자에게 노출된다.

### 없는 것 (이번에 만들어야 하는 것)

1. **근거 검증기** — GeneratedReport 텍스트에서 파일/클래스 언급을 추출해 허용 근거 집합과 대조.
2. **허용 근거 집합** — relatedCode(+flows?)에서 파일명·클래스명을 모아 "언급해도 되는 것" 집합 구성.
3. **위반 시 액션** — 제거(redact)/경고(warn)/폴백 중 무엇을 할지.
4. **경고 노출** — 위반을 결과 어디에 어떻게 드러낼지.
5. **연동** — enrich 단계에서 검증을 끼우기.

## 문제점 / 필요

1. LLM 환각(없는 클래스·파일 언급)이 **검증 없이 사용자에게 전달**되면 신뢰가 깨진다.
2. #10의 "예방적 grounding"은 지시일 뿐 강제가 아니다 — **사후 확인**이 있어야 "항상 근거에 묶임"(완료 기준)이 성립.
3. 단, **한글 자연어에서 클래스/파일 언급을 추출하는 것은 본질적으로 휴리스틱**이라 오탐(정상 표현을 위반으로
   오인)이 신뢰를 거꾸로 깎을 수 있다 — 이 리스크가 이번 설계의 핵심 난점.

## 개선 방향 (뼈대)

### A. 허용 근거 집합
relatedCode의 filePath(파일명)·symbolName(클래스명)을 모은다. flows의 노드 클래스도 포함할지는 결정 포인트.

### B. 언급 추출
텍스트에서 "코드 식별자처럼 보이는 토큰"(`*.java`, 경로, CamelCase 클래스명)을 뽑는다. 무엇을 추출 대상으로
볼지(오탐 최소화)가 결정 포인트.

### C. 대조 + 액션
추출된 언급 중 허용 집합에 없는 것 = **미근거 언급**. 이때:
(a) 경고만(결과에 표시) / (b) 해당 문장·필드 제거 / (c) 전체 rule-based 폴백. — 중대 결정 포인트.

### D. 경고 노출
위반 목록을 결과에 표시(새 필드 / rationale append / 로그). 결정 포인트.

### E. 연동
`enrichWithLlmReport`에서 write 성공 후 검증 → 액션. 검증도 실패에 안전(검증 자체가 분석을 깨지 않게).

## 범위 (초안 — plan에서 확정)

포함(예정): 근거 검증기, 허용 집합 구성, 위반 탐지 + 액션(경고/제거/폴백 중 결정), enrich 연동, 테스트.
제외(초안): LLM 재호출(재생성 루프), 의미 수준 근거 검증(문장이 코드와 정말 맞는지 — 그건 LLM/임베딩 영역),
문법 파싱 기반 정밀 추출(휴리스틱으로 시작), 다국어.

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **허용 근거 집합**: relatedCode의 파일명·클래스명만 / +flows 노드 / +도메인.
- **언급 추출 규칙**: 어떤 토큰을 "코드 언급"으로 볼지(`*.java`·경로·CamelCase). 오탐 최소화 기준.
- **위반 시 액션(중대)**: 경고만 / 필드·문장 제거 / 전체 폴백.
- **경고 노출**: 새 응답 필드 / rationale append / 로그만.
- **검증 대상 필드**: 3필드 전부 / 일부.
- **연동 지점**: enrich 내부 / 별도 verifier 컴포넌트.
- **테스트**: 근거 있는 언급 통과 / 없는 언급 탐지 / 오탐 회피 케이스.

## 관련 파일

```
src/main/java/ax/clio/analysis/GeneratedReport.java       - 검증 대상
src/main/java/ax/clio/analysis/RelatedCodeEntry.java      - 허용 근거 소스
src/main/java/ax/clio/analysis/CodeFlow.java, FlowNode.java - 흐름 근거(후보)
src/main/java/ax/clio/analysis/LlmReportWriter.java       - #10 생성기(검증 전 단계)
src/main/java/ax/clio/analysis/AnalysisWorker.java        - enrich 연동 지점
src/main/java/ax/clio/analysis/                           - (신규) ReportEvidenceVerifier 등
```

## 주의사항

1. **오탐이 최대 리스크.** 한글 텍스트에서 코드 언급 추출은 휴리스틱 — 정상 표현을 위반으로 오인하면 오히려
   신뢰를 깎는다. 추출 규칙을 **보수적으로**(확실한 코드 식별자만) 잡는 게 안전.
2. **검증이 분석을 깨면 안 된다** — 검증 로직 오류·엣지케이스에도 결과는 나와야 함(안전한 기본값).
3. **의미 검증이 아니라 참조 검증.** "문장이 코드와 의미적으로 맞나"가 아니라 "언급한 파일/클래스가 근거에
   존재하나"만 본다. 의미 검증은 범위 밖.
4. rule-based 폴백 텍스트(#10)는 애초에 근거 안에서만 말하므로 검증 대상은 **LLM 생성분**에 한정.
5. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```