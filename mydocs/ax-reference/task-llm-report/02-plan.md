# LLM 리포트 생성 구현 계획

overview 컨펌 완료. 이 문서는 **구현 단계**와 **결정 포인트(L1~L7)**를 담는다.
결정은 3단계에서 하나씩 확정하고 `03-decisions.md`에 기록한다.

> **재사용 전제**: `LlmClient.completeJson`(OpenAI 호환, `json_object`, temp 0)과 `LlmReportSearchPreparer`의
> 프롬프트·스키마검증·파싱 패턴을 미러링. 검색·점수는 rule-based가 계속 담당(원칙).

## 전체 그림

```
AnalysisWorker#run
  ├ buildDraft(...) ──→ rule-based draft (점수·후보·flows·rationale + 하드코딩 summary/fix/tests)
  └ enrich: llmConfigId 있으면
        LlmReportWriter.write(report, draft 근거) → {summary, fix, tests}
        ├ 성공 → 그 3필드만 교체
        └ 실패/미설정 → rule-based 3필드 그대로 (job 안 깨짐)
```

## 구현 단계 (결정에 종속)

각 단계는 관련 결정 확정 뒤 착수. 단계마다 컴파일/테스트 통과 상태로 커밋.

- **S1. 생성 결과 record + writer seam** — `GeneratedReport`(summary·fix·tests) + `LlmReportWriter`.
  - 관련 결정: **L2(생성 필드), L6(스키마·언어)**
- **S2. 프롬프트·파싱·검증** — 시스템/유저 프롬프트, JSON 파싱, 스키마 검증(`LlmReportSearchPreparer` 미러).
  실패 시 **예외 대신 빈 결과**(폴백 신호) 반환.
  - 관련 결정: **L3(폴백), L4(grounding 입력), L6**
- **S3. 워커 연동(enrich)** — `buildDraft`는 rule-based 그대로 두고, `run`에서 draft 생성 후 LLM 보강 단계.
  - 관련 결정: **L3(trigger), L5(연동 지점)**
- **S4. 테스트** — writer mock 테스트(성공→3필드, 무효 JSON→폴백) + 폴백 경로.
  - 관련 결정: **L7**
- **S5. Result** — `04-result.md` + roadmap #10 상태 갱신.

*(4.2 관련 코드 요약을 포함(L1-b)하면 S1~S2에 codeSummaries 필드/프롬프트가 추가된다.)*

---

## 결정 포인트 (하나씩 확정)

### L1. 범위 — 4.3만 vs +4.2(관련 코드 요약)
- (a) **4.3만**: summary·recommendedFix·recommendedTests 생성.
- (b) +4.2: 위 + 관련 코드 각각이 무슨 역할인지 요약(파일·라인 근거 유지).
- **AI 추천: (a) 4.3만.** #10 진행항목이 "리포트 생성"이다. 4.2는 스키마·grounding 표면을 키우고(후보별
  요약→환각 위험↑, #11 전이라 더) 프롬프트가 커진다. 4.3로 뼈대를 세우고 4.2는 **같은 writer에 필드 추가로
  쉽게 확장**할 수 있으니 별도 스텝으로. (작게 쪼개기.)

### L2. LLM이 채우는 필드 — 무엇을 생성하고 무엇을 rule-based로 남길까
- **AI 추천: LLM = {summary, recommendedFix, recommendedTests} 3개만.** 나머지(importance·difficulty·risk 점수,
  rationale, relatedCode, flows, similarIssues, relatedDecisions)는 **전부 rule-based 유지**. 원칙: 판단·점수는
  기존 로직, LLM은 설명. rationale은 "점수 근거"라 rule-based로 남겨 LLM 설명과 분리(교차검증 여지).

### L3. Trigger / 폴백 — 언제 켜고 실패하면 어떻게
- Trigger 후보: (a) **llmConfigId 있으면**(prepare와 동일 게이트) / (b) 새 플래그·mode.
- 폴백 후보: (i) **실패·미설정 시 rule-based 3필드 자동 유지(job 안 깨짐)** / (ii) 4.1처럼 job 실패.
- 부분 폴백: (x) **all-or-nothing**(파싱/검증 실패 시 3필드 모두 rule-based) / (y) 필드별 부분 교체.
- **AI 추천: (a) + (i) + (x).** llmConfigId 게이트가 기존과 일관. 리포트 텍스트는 완성된 rule-based 결과
  **위의 치장**이라 실패해도 자동 폴백이 자연스럽다(≠ 4.1 검색입력: 그건 틀리면 downstream이 쓰레기라 실패가 맞음
  — 이 비대칭을 decisions에 명시). all-or-nothing은 단순·예측가능. 필드별 부분교체는 일관성 흔들려 지금 과함.

### L4. Grounding 입력 범위 — draft의 무엇을 LLM에 넘길까
- (a) **report(title·desc·type) + 점수(3개) + candidateDomains + top-N 관련코드(path·class·role·line) + flow 설명 + rationale.**
- (b) 위 + similarIssues·relatedDecisions.
- **AI 추천: (a).** LLM이 설명할 재료는 "이 분석이 무엇을 근거로 나왔나"면 충분. similarIssues·relatedDecisions는
  이미 **결과에 별도 참고 섹션**으로 붙어 있어 요약문에 또 녹이면 중복·환각면적↑. top-N(예: 관련코드 상위 5)로
  프롬프트 크기 제한. "입력 밖 파일/클래스 지어내지 말라" 지시 동반(예방적 grounding, 사후검증은 #11).

### L5. 연동 지점 — 어디서 보강할까
- (a) `buildDraft` 내부에서 writer 호출.
- (b) **`buildDraft`는 rule-based 순수 유지, `run`에서 draft 생성 후 별도 enrich 단계**로 3필드 교체.
- **AI 추천: (b).** buildDraft가 **폴백 원본**이자 순수 rule-based로 남아 LLM 없이도 테스트·동작. writer는 독립
  단위테스트. enrich는 "draft + generated → 새 draft"(record with-er 또는 run에서 새 draft 조립). 관심사 분리.

### L6. 프롬프트 · 스키마 · 출력 언어
- **AI 추천**: JSON 스키마 `{ "summary": string, "recommendedFix": string, "recommendedTests": string }`.
  **출력 한국어**(리포트·사용자가 한국어). 각 필드 길이 상한(예: summary ~600자, fix/tests ~800자)으로 방어.
  시스템 프롬프트: "너는 분석 결과를 **설명**한다. 점수·판단을 새로 만들지 말고, 입력에 없는 파일·클래스·메서드를
  지어내지 마라. JSON만 반환." `LlmReportSearchPreparer`의 검증(필수필드·타입·길이) 재사용.

### L7. 테스트 전략
- **AI 추천**: `LlmReportSearchPreparerTest`처럼 **`LlmClient` mock**. 케이스: (1) 정상 JSON→3필드 채워짐,
  (2) 무효 JSON/누락→빈 결과(폴백 신호), (3) 클라이언트 예외→빈 결과. + 워커 레벨: llmConfigId 없으면 rule-based,
  있고 성공이면 교체, 실패면 rule-based 유지. 외부 실호출·키 불필요.

---

## 결정 포인트 요약표

| ID | 주제 | AI 추천 |
|----|------|---------|
| L1 | 범위 | 4.3만(summary·fix·tests), 4.2는 나중 |
| L2 | LLM 생성 필드 | 3개 텍스트만, 점수·rationale은 rule-based |
| L3 | Trigger/폴백 | llmConfigId 게이트 + 실패 시 자동 rule-based + all-or-nothing |
| L4 | grounding 입력 | report+점수+도메인+top-N 관련코드+flows+rationale (유사이슈·결정 제외) |
| L5 | 연동 지점 | buildDraft는 순수 유지 + run에서 enrich 단계 |
| L6 | 프롬프트·스키마 | 3필드 JSON, 한국어, 길이상한, "지어내지 말라" |
| L7 | 테스트 | LlmClient mock, 성공/무효/예외 + 워커 폴백 |

## 완료 기준(이 작업)

- llmConfigId 있는 분석은 summary·recommendedFix·recommendedTests가 **LLM으로 리포트별 다르게** 생성된다.
- LLM 미설정/실패 시 기존 rule-based 문자열로 **자동 폴백**(job 안 깨짐).
- 점수·rationale·검색결과는 rule-based 그대로(원칙 유지).
- 테스트는 mock으로 성공·폴백 모두 검증. 전체 스위트 그린(외부 키 불필요).

## 열린 질문 / 리스크

- 환각: 예방적 grounding(입력 한정+지시)까지만 — 사후 검증은 #11. "완전 방지"로 오해 말 것.
- LLM 출력 품질은 provider·model·프롬프트에 좌우 — 이번은 **배선·폴백·경계**가 목적(품질 튜닝은 별도).
- rationale(rule-based) vs summary(LLM)가 상충하는 서술을 낼 수 있음 — 의도적 이중화(교차검증). #11에서 정합.
