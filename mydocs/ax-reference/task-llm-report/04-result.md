# LLM 리포트 생성 결과 (roadmap #10 / 4.3)

결정(L1~L7) 확정 후 S1~S4 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.

## 무엇을 만들었나 (S1~S4)

rule-based 분석 draft(점수·후보·flows·rationale)를 근거로 **LLM이 사람이 읽는 3필드
(summary·recommendedFix·recommendedTests)를 한국어로 생성**한다. 실패·미설정 시 기존 rule-based 문자열로
**자동 폴백**(job 안 깨짐).

| 산출물 | 위치 |
|--------|------|
| `GeneratedReport`(summary·fix·tests record) | `analysis/` |
| `LlmReportWriter`(프롬프트·파싱·검증, 실패 시 `Optional.empty` 폴백 + 로그) | `analysis/LlmReportWriter.java` |
| `AnalysisResultDraft.withGeneratedReport`(3필드만 교체) | `analysis/AnalysisResultDraft.java` |
| `AnalysisWorker.enrichWithLlmReport`(buildDraft 후 보강, llmConfigId 게이트·폴백) | `analysis/AnalysisWorker.java` |
| 테스트(writer mock 4케이스 + draft with-er 불변식) | `test/.../analysis/` |

**적용된 결정**: L1(4.3만·4.2 미룸) · L2(3필드만, 점수·rationale은 rule-based) · L3(llmConfigId 게이트 +
자동 폴백 + all-or-nothing + 로그) · L4(근거 한정 grounding, 유사이슈·결정 제외) · L5(buildDraft 순수 +
enrich 단계) · L6(3필드 JSON·한국어·길이상한) · L7(LlmClient mock).

## 동작 방식 요약

- `AnalysisWorker#run`: `buildDraft`(순수 rule-based) → `enrichWithLlmReport`.
- **enrich**: `llmConfigId == null`이면 rule-based 그대로. 있으면 `LlmReportWriter.write`(report + draft 근거) →
  성공 시 3필드만 `withGeneratedReport`로 교체, 실패 시 rule-based 유지.
- **writer**: `LlmClient.completeJson`(OpenAI 호환, `json_object`, temp 0) 호출 → `choices[0].message.content`의
  JSON 파싱·검증 → summary/fix/tests. 예외·무효·누락은 전부 `Optional.empty`(폴백) + info 로그.
- **grounding 입력(L4)**: 리포트 title·desc·type, 점수 3개, 후보 도메인, 관련코드 top-5(path·class·role·line),
  flow 설명, rationale. 프롬프트로 "점수 새로 만들지 말 것, 입력 밖 파일/클래스 지어내지 말 것" 지시.

## 원칙 준수 (판단은 rule-based, LLM은 설명)

- 점수(importance·difficulty·risk)·rationale·relatedCode·flows·similarIssues·relatedDecisions는 **전부 rule-based
  유지**. LLM은 summary·fix·tests 텍스트만 생성.
- rationale(rule-based 점수 근거) vs summary(LLM 서술)를 **의도적으로 이중화** — 교차검증 여지(#11에서 정합).

## 4.1(검색입력 구조화)과의 폴백 비대칭 (의도)

- 4.1 `LlmReportSearchPreparer`는 실패 시 **job 실패**(틀린 검색입력은 downstream을 전부 오염 → 실패가 맞음).
- #10 리포트 텍스트는 완성된 rule-based 결과 **위의 치장** → 실패해도 **degrade(자동 폴백)**, 결과는 유효.

## 테스트 / 검증 상태

- `LlmReportWriterTest`(LlmClient mock): 정상 JSON→3필드, 필수필드 누락→폴백, content 빈문자열→폴백,
  클라이언트 예외→폴백. `AnalysisResultDraftTest`: with-er가 3필드만 교체·나머지 유지.
- 전체 스위트 그린(외부 LLM 실호출·키 불필요). `@SpringBootTest contextLoads`가 새 빈(`LlmReportWriter`) 포함 부팅.

## 정직한 한계 / 미완 (backlog)

1. **⚠️ 4.2(관련 코드 요약) 미구현 — 명시적으로 미룸(L1).** "검색된 코드가 어떤 역할을 하는지 요약 +
   파일·라인 근거 유지"는 이번 #10에 넣지 않았다. **후속 스텝에서 같은 `LlmReportWriter`에 codeSummaries
   필드를 추가**하는 방식으로 진행한다.
2. **환각 방지는 예방까지만(#11 경계).** 입력 한정 + "지어내지 말라" 지시까지만. 사후 근거 검증·환각 필터는
   #11(4.4)의 몫. LLM이 규칙을 어기고 없는 파일을 언급할 여지는 남아 있다.
3. **출력 품질은 provider·model·프롬프트에 좌우.** 이번은 배선·폴백·경계가 목적(품질 튜닝·평가 하네스는 별도).
4. **ANTHROPIC·GEMINI provider 미지원.** `OpenAiCompatibleLlmClient`는 OPENAI·DEEPSEEK·OPENAI_COMPATIBLE만.
   Claude/Gemini로 리포트 생성하려면 클라이언트 확장 필요(범위 밖).
5. **폴백은 스키마에 안 드러남.** LLM 폴백 여부는 로그로만 추적(응답에 flag 미추가 — 최소 변경, L3).
6. **UI 미노출.** summary·fix·tests는 이미 `AnalysisJobResponse`에 있던 필드라 응답 형태는 불변(값만 LLM으로).

## 다음(범위 밖)

- #11 근거 검증(4.4): LLM이 검색결과에 없는 파일·클래스를 언급 못 하게 사후 검증·필터.
- 4.2 관련 코드 요약(위 backlog 1): 같은 writer에 codeSummaries 추가.
- ANTHROPIC/GEMINI 클라이언트 확장.
