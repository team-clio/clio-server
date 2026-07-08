# 튜닝 백로그 (MVP 이후)

## 이 문서의 목적

랭킹 점수 계산에 들어간 **매직 넘버**와, 벤치마크 데이터가 있어야 결정할 수 있어서
**미뤄둔 결정들**을 한곳에 모은다. MVP가 끝난 뒤 이 문서 하나만 보고 튜닝을 시작할 수 있게 한다.

원칙: **근거(벤치마크 숫자) 없이 weight를 감으로 바꾸지 않는다.** 지금 코드에 박힌 값은
전부 "출발점(prior)"일 뿐 확정값이 아니다. 튜닝은 `05-eval-harness.md`의 harness로 측정하며 진행한다.

---

## 0. 선행 조건: 첫 점수판(baseline) 확보

튜닝의 나침반. 이게 없으면 아래 항목 전부 손대면 안 된다.

- harness: `src/test/java/ax/clio/analysis/eval/EvalHarnessRunnerIT.java`
- 실행: git 히스토리 풍부한 Java 레포를 clio에 스캔한 뒤
  ```
  CLIO_EVAL_REPO=/path/to/repo \
  CLIO_EVAL_PROJECT_ID=1 \
  CLIO_EVAL_LIMIT=300 \
  CLIO_EVAL_LLM_CONFIG_ID=1 \   # 생략 시 결정적 RAW_ONLY
  ./gradlew test --tests '*EvalHarnessRunnerIT'
  ```
- 산출: Recall@1/5/10, MRR, MAP 점수판 (현재 D3 곱연산 ranker 기준선)

---

## 1. 현재 코드에 박힌 매직 넘버 (전부 잠정값)

위치: `src/main/java/ax/clio/analysis/CodeCandidateRanker.java`

| 상수 | 현재 값 | 근거 결정 | 상태 |
|------|--------|----------|------|
| `DOMAIN_CLASS_MULTIPLIER` | 1.15 | D3/D4 | 잠정 |
| `DOMAIN_OTHER_MULTIPLIER` | 1.05 | D3/D4 | 잠정 |
| `RAW_REPORT_MULTIPLIER`   | 1.10 | D3/D4 | 잠정 |
| `KEYWORD_MULTIPLIER`      | 1.0 (기준선) | D3/D4 | 잠정 |
| `HIT_COUNT_BONUS`         | 10 | D4/D6 | 잠정 |
| `HIT_COUNT_BONUS_CAP`     | 30 | D4/D6 | 잠정 |
| `FILE_MATCH_THRESHOLD`    | 3 | D8 | 잠정 |
| `FILE_MATCH_BONUS`        | 5 | D8 | 잠정 |

→ 이 값들은 03-decisions.md에서 "사용자가 아직 직접 결정하지 않았다. 벤치마크 후 튜닝 필요"로 표기됨.

---

## 2. D11 — 점수 함수 구조 리팩터 (미뤄둠)

**상태: 방향만 결정, 코드 미반영. 현재 ranker는 여전히 D3(곱연산).**
전체 MVP 완료 후, baseline 점수판을 뽑고 나서 진행한다.

### 2-1. 구조: 곱연산 → 전부 가산 선형 (D11-1, 대안 B 선택)

```
adjustedScore = baseScore + typeBonus + hitBonus + fileMatchBonus
```

이유: 혼합(곱연산+가산) 구조에서는 곱연산 효과가 압축된 base 범위(50~100)에 갇혀
구조적으로 hitBonus에 밀린다(스케일 불일치, 값 튜닝으로 못 고침). 전부 가산은
learning-to-rank의 가장 단순한 형태 → feature × weight 분리로 설명·튜닝·학습 가능성이 따라온다.

### 2-2. 우선순위 prior: 정밀도 우선 (D11-2, 대안 A 선택)

```
typeBonus: DOMAIN+CLASS=+40, DOMAIN+기타=+15, RAW_REPORT=+20, KEYWORD=0
hitBonus:  (hitCount-1)*7,  cap +21
fileBonus: 파일 내 매칭 >= 3 이면 +5
→ 정확한 도메인 매치가 반복매치를 확실히 이긴다
```

대안 B(균형)/C(코로보레이션 우선)는 03-decisions.md D11-2 참고. **최종 weight는 harness 측정으로 결정.**

### 2-3. 적용 시 함께 할 것

- D3(곱연산) 상수 제거, 위 가산 구조로 교체
- `CodeCandidateRankerTest`의 곱연산 기대값 테스트 갱신
- `04-result.md`의 "검색 입력 타입별 가중치" 표 서술 갱신

---

## 3. weight 외부화 → grid search

- 현재 weight는 `CodeCandidateRanker`에 하드코딩된 상수.
- feature × weight로 분리해 밖에서 주입 가능하게(설정/파라미터화) 만든다.
- harness를 weight 조합별로 반복 실행 → grid search로 최적 조합 탐색.

---

## 4. 평가 harness 정밀도 개선 (열린 이슈)

`05-eval-harness.md`에서 옮겨옴. baseline 신뢰도를 높이려면 필요.

- **버그픽스 커밋 식별 휴리스틱 정밀도**: 현재 `fix|bug|issue|close #...` 매칭.
  false positive 커밋(실제 버그픽스 아님)이 섞인다.
- **리팩터링/멀티파일 커밋 노이즈**: 정답 파일이 너무 많은 케이스 제외 임계값 튜닝
  (`CLIO_EVAL_MAX_FILES`, 현재 기본 10).
- **leakage 마스킹**: 커밋 메시지가 클래스명을 직접 언급하면("Fix PaymentService …")
  검색이 실제 유저 리포트보다 후해진다. v1은 leakage 감수. 이후 정답 파일/클래스명
  토큰을 쿼리에서 마스킹하는 옵션 도입.
- **기준 프로젝트 선정**: git 히스토리 풍부한 Java 레포 확보.

---

## 5. 장기 (튜닝 그 다음)

- feature 벡터 로깅 → learning-to-rank 학습셋 구축
- (참고) 이건 튜닝이 아니라 별도 과제. 여기서는 방향만 기록.

---

## 튜닝 착수 순서 요약

```
0. baseline 점수판 확보                     ← 반드시 먼저
1. D11 구조 리팩터 (곱연산→전부 가산)        → 재측정, 개선 확인
2. weight 외부화 → grid search               → 최적 조합
3. harness 정밀도 개선 (leakage 마스킹 등)   → 숫자 신뢰도 상승
4. (장기) feature 로깅 → learning-to-rank
```
