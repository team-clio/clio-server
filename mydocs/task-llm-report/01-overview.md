# LLM 기반 분석 리포트 생성 (roadmap #10 / 4.3, +4.2 후보)

## 이 문서의 목적

rule-based 점수·근거·검색 결과를 **LLM에 넘겨 사람이 읽기 좋은 분석 리포트**(요약·추천 수정 방향·테스트
전략)를 생성한다. roadmap "추천 개발 순서" #10 (4.3 분석 리포트 생성). 4.2(관련 코드 요약) 포함 여부는 결정 포인트.

핵심 원칙(roadmap 4 도입부):

> LLM은 처음부터 판단을 전부 맡기지 않는다. **검색과 점수화는 기존 로직**이 담당하고, LLM은 **설명과 요약**을 담당한다.

목표(roadmap 4.3):

> - rule-based 점수와 근거를 LLM에 전달한다.
> - 사람이 읽기 좋은 분석 요약을 생성한다.
> - 추천 수정 방향과 테스트 전략을 생성한다.

> **#11(근거 검증)과의 경계**: "LLM이 검색 결과에 없는 파일·클래스를 언급 못 하게" 하는 **형식 검증(4.4)은
> #11의 몫**이다. 이번 #10은 (a) LLM에 **검색된 근거만 입력**으로 주고 (b) 프롬프트로 "주어진 근거 밖은
> 지어내지 말라"고 지시하는 **예방적 grounding**까지. 사후 검증·필터는 #11.

## 배경 / 현재 구현 상태

### 이미 있는 것 (LLM 인프라 — 재사용)

| 요소 | 위치 | 비고 |
|------|------|------|
| LLM 호출 seam | `llm.LlmClient#completeJson(config, model, system, user)` | JSON 문자열 반환 |
| OpenAI 호환 클라이언트 | `llm.OpenAiCompatibleLlmClient` | `/chat/completions`, `response_format=json_object`, temp 0. OPENAI·DEEPSEEK·OPENAI_COMPATIBLE 지원(ANTHROPIC·GEMINI는 enum만) |
| LLM 설정 | `llm.LlmConfig`/`LlmConfigService` | provider·baseUrl·apiKey·model |
| **4.1 리포트 구조화(완료)** | `analysis.LlmReportSearchPreparer` | 리포트→검색입력 JSON, 스키마 검증·파싱. **프롬프트/파싱 패턴 미러링 대상** |
| mode 기반 LLM/raw 선택 | `AnalysisWorker#prepare` | `RAW_ONLY`거나 llmConfigId 없으면 rule-based(`rawOnly()`), 아니면 LLM |
| rule-based draft 조립 | `AnalysisWorker#buildDraft` | 점수·후보·flows·rationale 생성 |

### 지금 리포트가 만들어지는 방식 (문제 지점)

`AnalysisWorker#buildDraft`에서 사람이 읽는 3개 필드가 **하드코딩/템플릿**이다:

- `summary` = `"리포트 '<title>'는 <type> 입력으로 해석되며, N개 파일 후보와 연결되었습니다."` (템플릿)
- `recommendedFix` = `"상위 점수 관련 코드부터 재현 경로를 확인하고, Controller-Service-Repository 흐름에서…"` (고정 문자열)
- `recommendedTests` = `"서비스 단위 테스트와 주요 흐름 통합 테스트를 추가하세요."` (고정 문자열)

→ **어떤 리포트든 거의 같은 문장**이 나온다. 점수·후보·flows는 리포트별로 다른데 설명은 안 변한다.

### 없는 것 (이번에 만들어야 하는 것)

1. **LLM 리포트 작성기** — rule-based draft(점수·후보·flows·rationale·도메인)를 입력으로 받아
   summary·recommendedFix·recommendedTests를 생성.
2. **입력 구성(grounding)** — LLM에 넘길 근거를 "검색된 것만"으로 한정해 조립.
3. **fallback** — LLM 미설정/실패 시 현재 rule-based 문자열을 그대로 사용(분석이 깨지면 안 됨).
4. **연동 지점** — `buildDraft` 이후 LLM 보강 단계.

## 문제점 / 필요

1. 분석 결과의 설명·추천이 **리포트와 무관하게 획일적**이라 실사용 가치가 낮다.
2. rule-based가 만든 풍부한 신호(점수 근거·flows·관련 코드 역할)가 **자연어 설명으로 연결되지 않는다.**
3. LLM을 붙이되 **판단(검색·점수)은 넘기지 않고** 설명만 맡기는 경계를 명확히 세워야 한다(원칙).

## 개선 방향 (뼈대)

### A. LLM 리포트 작성기
`LlmReportWriter`(가칭): (report + rule-based draft 요약) → JSON {summary, recommendedFix, recommendedTests
(+ codeSummaries?)}. `LlmReportSearchPreparer`의 프롬프트·스키마검증·파싱 패턴 재사용.

### B. 입력 grounding
LLM 입력엔 **검색된 관련 코드(파일·클래스·역할·라인)·점수·근거·flows·도메인**만 넣는다. 프롬프트로
"입력에 없는 파일/클래스/메서드를 지어내지 말 것"을 지시. (사후 검증은 #11.)

### C. Trigger / Fallback
LLM 리포트는 llmConfigId가 있을 때만. **실패·미설정 시 rule-based 문자열로 자동 폴백**(job 실패 아님).
어떤 mode/플래그로 켤지는 결정 포인트.

### D. 4.2(관련 코드 요약) 포함 여부 (범위 결정)
"검색된 코드가 어떤 역할을 하는지 요약 + 파일·라인 근거 유지." #10에 넣을지, 별도로 뺄지 결정 포인트.

## 범위 (초안 — plan에서 확정)

포함(예정): LLM 리포트 작성기(summary·fix·tests), grounding 입력 구성, 자동 폴백, 워커 연동.
제외(초안): 사후 근거 검증·환각 필터(#11), ANTHROPIC/GEMINI 클라이언트 확장, 스트리밍, LLM 기반 점수화(원칙 위배),
프롬프트 A/B·평가 하네스.

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- **범위**: 4.3만(summary·fix·tests) / +4.2(관련 코드 요약).
- **LLM 생성 필드**: 정확히 어떤 필드를 LLM이 채우고 어떤 걸 rule-based로 남길지(점수·rationale은 rule-based 유지).
- **Trigger/폴백**: 어떤 조건에서 LLM 리포트 on, 실패 시 폴백 형태(부분 폴백 허용?).
- **입력 grounding 범위**: draft의 무엇을 LLM에 넘길지(관련 코드 top-N·flows·점수·유사이슈·관련결정?).
- **연동 지점**: `AnalysisWorker#buildDraft` 내부 보강 / 별도 후처리 단계 / 새 컴포넌트.
- **프롬프트·스키마**: JSON 스키마, 언어(한국어 출력?), 길이 제한.
- **테스트**: `LlmReportSearchPreparerTest`처럼 `LlmClient` mock + 폴백 경로 검증.

## 관련 파일

```
src/main/java/ax/clio/llm/LlmClient.java                       - 재사용(completeJson)
src/main/java/ax/clio/analysis/LlmReportSearchPreparer.java    - 프롬프트·파싱·검증 패턴 미러링
src/main/java/ax/clio/analysis/AnalysisWorker.java             - buildDraft 연동 지점(하드코딩 3필드)
src/main/java/ax/clio/analysis/AnalysisResultDraft.java        - 생성 필드 담는 곳
src/main/java/ax/clio/analysis/                                - (신규) LlmReportWriter 등
src/test/java/ax/clio/analysis/LlmReportSearchPreparerTest.java - 테스트 패턴(mock LlmClient)
```

## 주의사항

1. **원칙 사수**: 검색·점수화는 rule-based가 계속 담당. LLM은 설명·요약만. 점수를 LLM이 바꾸지 않는다.
2. **LLM 실패가 분석을 깨면 안 된다** — 자동 폴백으로 항상 결과가 나오게(4.1은 mode 선택식이나, 여기선
   보강 실패 시 rule-based 유지가 자연스러움 → 결정에서 확정).
3. **근거 밖 환각 방지는 예방(입력 한정+지시)까지만**, 사후 검증은 #11. 경계를 흐리지 말 것.
4. 외부 LLM 호출은 테스트에서 **항상 mock**(실호출·키 필요 없음). #7 D3-1(embedding API)와는 별개 축.
5. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```