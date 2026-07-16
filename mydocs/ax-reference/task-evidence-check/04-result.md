# LLM 근거 검증 결과 (roadmap #11 / 4.4)

결정(E1~E7) 확정 후 S1~S3 구현 완료. 이 문서는 **실제 코드 상태**를 정직하게 반영한다.

## 무엇을 만들었나 (S1~S3)

#10에서 LLM이 생성한 리포트가 **검색된 근거에 없는 파일·클래스를 언급**하면 사후 검증해 경고로 노출한다.
#10의 예방적 grounding("지어내지 말라" 지시)에 이은 **사후 검증** — 예방+검증 2단 방어.

| 산출물 | 위치 |
|--------|------|
| `ReportEvidenceVerifier`(참조 검증, 보수적 추출) | `analysis/ReportEvidenceVerifier.java` |
| `evidenceWarnings` 필드 (draft·Job·Response) | `analysis/` |
| `AnalysisResultDraft.withEvidenceWarnings` | `analysis/AnalysisResultDraft.java` |
| `AnalysisWorker.verifyEvidence`(enrich에서 검증→경고 부착) | `analysis/AnalysisWorker.java` |
| 테스트(검증기 6케이스) | `test/.../ReportEvidenceVerifierTest.java` |

**적용된 결정**: E1(relatedCode+flows) · E2(보수적 추출·관대 매칭) · E3(경고만·텍스트 유지) ·
E4(evidenceWarnings 필드) · E5(3필드 전부) · E6(별도 verifier + enrich 연동) · E7(검증기 단위테스트).

## 동작 방식 요약

- `AnalysisWorker#enrichWithLlmReport`: LLM write 성공 → `withGeneratedReport`로 텍스트 적용 →
  `verifyEvidence`로 근거 밖 언급 검출 → `withEvidenceWarnings`로 경고 부착(텍스트는 유지).
- **허용 근거(E1)**: relatedCode의 filePath·symbolName + flows의 className·filePath. 정규화(소문자·경로제거·
  `.java` 제거)해 파일명과 클래스명을 같은 키로 취급.
- **추출(E2)**: `*.java` 파일명 + multi-hump CamelCase(`PaymentService`)만. 단일 대문자 단어(`Payment`)·
  소문자·한글·전대문자 약어(`JSON`)는 추출 안 함 → 오탐 최소.
- **위반 시(E3)**: LLM 텍스트 유지 + `evidenceWarnings`에 `"근거 없는 코드 언급: OrderValidator"` 추가.
  rule-based 폴백 경로엔 경고 없음(애초에 근거 안에서만 말함).
- 응답 `AnalysisJobResponse.evidenceWarnings`로 노출(E4).

## 참조 검증이지 의미 검증이 아니다

- "언급한 파일/클래스가 근거에 **존재하나**"만 본다. "문장이 코드와 의미적으로 맞나"는 범위 밖.
- roadmap 4.4 "제거하거나 **경고**한다"의 **경고** 쪽을 택함(E3). 완료 기준("근거에 묶임")은 근거 밖 언급을
  가시화하는 것으로 충족.

## 테스트 / 검증 상태

- `ReportEvidenceVerifierTest`: (1) 근거 내 클래스·`.java` 언급→통과, (2) 근거 밖 클래스→경고,
  (3) 근거 밖 `.java`→경고(클래스명 형태로 통일 표시), (4) 한글·단일 대문자 단어→오탐 없음,
  (5) 빈 근거·빈 텍스트·null→안전 통과, (6) 중복 언급 dedup.
- 전체 스위트 그린(외부 호출 없음). `contextLoads`가 새 빈(`ReportEvidenceVerifier`) 포함 부팅.

## 정직한 한계 / 미완 (backlog)

1. **오탐 vs 미탐 트레이드오프(E2 보수성).** 명백한 코드 식별자만 잡으므로 **교묘한 환각은 놓칠 수 있다**
   (미탐). "완벽한 환각 차단이 아니라 명백한 환각만 잡는 1차 방어"다.
2. **JDK/외부 클래스도 근거 밖으로 경고될 수 있다.** LLM이 `NullPointerException` 같은 실제 JDK 클래스를
   언급하면 검색 근거엔 없으니 경고가 붙는다. 경고만(E3)이라 리포트를 버리진 않아 감내 가능하지만 노이즈 여지.
3. **경고만·텍스트 유지(E3).** 환각 텍스트가 경고와 함께 사용자에게 그대로 보인다(제거·폴백 아님). 자동
   제거를 원하면 별도 결정 필요.
4. **참조 검증만.** 의미 수준(문장이 코드와 정말 맞나) 검증·LLM 재생성 루프는 범위 밖.
5. **정밀 추출(AST·NER) 아님.** 정규식 휴리스틱이라 특이 표기(백틱·한글 조사 결합 등)는 놓칠 수 있다.

## 다음(범위 밖)

- 4.2 관련 코드 요약(#10에서 미룬 것): 같은 `LlmReportWriter`에 codeSummaries 추가 — 추가 시 이 검증기의
  대상 필드에 포함하면 요약의 근거도 함께 검증됨.
- 로드맵 5. LangGraph: 분석 단계 분리(리포트 구조화→검색→흐름→Memory→점수화→리포트생성→근거검증).
