# LLM 리포트 생성 결정 히스토리

각 항목: **후보 → 선택 → 이유**. plan의 결정 포인트 L1~L7.
전제: `LlmClient.completeJson` + `LlmReportSearchPreparer` 프롬프트·파싱·검증 패턴 재사용. 검색·점수는 rule-based 유지(원칙).

---

## L1. 범위 — 4.3만 vs +4.2(관련 코드 요약)

**후보**
- (a) 4.3만: summary·recommendedFix·recommendedTests 생성.
- (b) +4.2: 위 + 관련 코드 각각의 역할 요약(파일·라인 근거 유지).

**선택: (a) 4.3만. 4.2(관련 코드 요약)는 이번 범위에서 제외 — 별도 후속 스텝으로 명시적으로 미룬다.**

**이유**
- #10 진행항목이 "LLM 기반 리포트 생성"(4.3)이다. 4.2는 후보별 요약이라 스키마·프롬프트·환각 면적을
  키운다(사후 근거검증 #11 전이라 더 위험). 작게 쪼갠다.
- 4.2는 **같은 writer에 필드(codeSummaries) 추가로 쉽게 확장** 가능 → 뼈대(4.3) 후 별도 스텝.

> **⚠️ 미룬 작업 명시(4.2 관련 코드 요약)**: roadmap 4.2 "검색된 코드가 어떤 역할을 하는지 요약 + 파일·라인
> 근거 유지"는 이번 #10에 **넣지 않는다.** #10 완료 후 별도 스텝에서 이 writer에 codeSummaries 필드를
> 추가하는 방식으로 진행한다. result·roadmap에도 이 미룸을 남긴다.

---

## L2. LLM이 채우는 필드

**선택: LLM = {summary, recommendedFix, recommendedTests} 3개만.** 나머지(importance·difficulty·risk 점수,
rationale, relatedCode, flows, similarIssues, relatedDecisions)는 **전부 rule-based 유지**.

**이유**: 원칙 — 판단·점수는 기존 로직, LLM은 설명. rationale("점수 근거")은 rule-based로 남겨 LLM 요약과
분리(교차검증 여지, #11에서 정합). 3필드는 현재 하드코딩/템플릿이라 교체 대상이 정확히 이것.

---

## L3. Trigger / 폴백 — 언제 켜고 실패하면 어떻게

**선택: (a) llmConfigId 게이트 + (i) 실패·미설정 시 rule-based 3필드 자동 유지 + (x) all-or-nothing. 폴백은 명시한다.**

**이유**
- llmConfigId 게이트는 `AnalysisWorker#prepare`와 동일해 일관.
- 리포트 텍스트는 완성된 rule-based 결과 **위의 치장**이라 실패해도 자동 폴백이 자연스럽다.
  **4.1(검색입력 구조화)과의 비대칭**: 4.1은 틀리면 downstream(검색·점수)이 전부 쓰레기라 job 실패가 맞지만,
  #10 설명문은 없어도 점수·코드·flows가 있는 유효한 결과다 → 통짜로 날리지 않고 degrade.
- all-or-nothing: 파싱/검증 실패 시 3필드 모두 rule-based. 단순·예측가능(필드별 부분교체는 일관성 흔들려 과함).
- **"명시"**: 폴백이 일어나면 (1) 이 결정 문서·result에 분명히 남기고, (2) 코드에서 **폴백 시 로그**(info/debug)를
  남겨 "LLM 실패 → rule-based 사용"이 추적되게 한다. (분석 결과 스키마에 새 플래그는 추가하지 않음 — 최소 변경.)

---

## L4. Grounding 입력 범위

**선택: (a) report(title·desc·type) + 점수(3개) + candidateDomains + top-N 관련코드(path·class·role·line) + flow 설명 + rationale.**
similarIssues·relatedDecisions는 입력에서 제외.

**이유**: LLM이 설명할 재료는 "이 분석이 무엇을 근거로 나왔나"면 충분. 유사이슈·관련결정은 이미 결과에
**별도 참고 섹션**으로 붙어 있어 요약문에 또 녹이면 중복·환각면적↑. top-N(관련코드 상위 N)로 프롬프트 크기
제한. "입력 밖 파일/클래스 지어내지 말라" 지시 동반(예방적 grounding; 사후검증은 #11).

---

## L5. 연동 지점

**선택: (b) buildDraft는 rule-based 순수 유지 + run에서 enrich 단계.**

**이유**: buildDraft가 **폴백 원본**이자 순수 rule-based로 남아 LLM 없이도 테스트·동작. writer는 독립 단위테스트.
enrich는 "draft + generated → 3필드 교체한 새 draft"(record with-er). 관심사 분리로 테스트·책임이 깔끔.

---

## L6. 프롬프트 · 스키마 · 출력 언어

**선택**: JSON 스키마 `{ "summary": string, "recommendedFix": string, "recommendedTests": string }`. **출력 한국어**.
각 필드 길이 상한(방어). 시스템 프롬프트: "분석 결과를 **설명**한다. 점수·판단을 새로 만들지 말고, 입력에 없는
파일·클래스·메서드를 지어내지 마라. JSON만 반환." `LlmReportSearchPreparer`의 검증(필수필드·타입·길이) 재사용.

**이유**: 리포트·사용자가 한국어. temp 0·`json_object`는 클라이언트가 이미 강제. 길이 상한으로 폭주 방어.

---

## L7. 테스트 전략

**선택**: `LlmReportSearchPreparerTest`처럼 **`LlmClient` mock**. 케이스: (1) 정상 JSON→3필드 채워짐,
(2) 무효/누락 JSON→빈 결과(폴백 신호), (3) 클라이언트 예외→빈 결과. + 워커 레벨: llmConfigId 없으면 rule-based,
있고 성공이면 교체, 실패면 rule-based 유지. 외부 실호출·키 불필요.

---

## 결정 완료 — 다음 단계

L1~L7 전부 확정. 구현(S1~S5, `02-plan.md`) 착수 가능. `04-result.md`는 구현 진행에 따라 작성.
