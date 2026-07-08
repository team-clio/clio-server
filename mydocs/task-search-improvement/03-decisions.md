# 구현 결정 히스토리

## D1. 중간 모델 이름

### 선택: A — `RankedCodeCandidate`

---

## D2. symbolRole 전달 방식

### 선택: A — `CodeSearchResult`에 role 필드 추가

---

## D3. 가중치 방식

### 선택: B — 곱연산 (multiplier)

---

## D4. 가중치 구체적인 값

### 선택: B — 중간 값 (곱연산으로 변환)

현재 적용된 값:
```
DOMAIN_CLASS_MULTIPLIER = 1.15
DOMAIN_OTHER_MULTIPLIER = 1.05
RAW_REPORT_MULTIPLIER   = 1.10
KEYWORD_MULTIPLIER      = 1.0  (기준선)
HIT_COUNT_BONUS         = 10   (가산)
HIT_COUNT_BONUS_CAP     = 30   (상한)
```

**이 값은 사용자가 아직 직접 결정하지 않았다. 벤치마크 후 튜닝 필요.**

---

## D5. 반복 등장 기준

### 선택: B — 심볼 단위

같은 파일 + 같은 심볼이름 + 같은 타입이 여러 query에서 매칭된 횟수를 hitCount로 기록한다.
같은 파일의 다른 심볼은 별개로 카운트된다.

---

## D6. 가산점 크기와 상한

### 선택: A — D4에 따름

hitCount당 +10, 상한 +30.

---

## D7. 파일 그룹화 후 결과 형태

### 선택: A — 파일별 대표 1개만

파일 내 여러 심볼이 매칭되면 adjustedScore가 가장 높은 심볼을 대표로 선택한다.

---

## D8. 파일 내 매칭 수의 점수 반영

### 선택: B — 약한 가산점

같은 파일에서 3개 이상 매칭이 나오면 +5점.

현재 적용된 값:
```
FILE_MATCH_THRESHOLD = 3
FILE_MATCH_BONUS     = 5
```

**이 값은 사용자가 아직 직접 결정하지 않았다. 벤치마크 후 튜닝 필요.**

---

## D9. 테스트 대응 판단

### 선택: 제거

테스트 코드 존재 여부를 점수화에 반영하지 않는다.
테스트 코드가 있어야 하는 건 당연한 것이고, 버그 리포트 분석 우선순위와는 별개의 문제다.

기존에 있던 항목 제거:
- 난이도: `hasRelatedTest ? 0 : 10` 제거
- 위험도: `hasRelatedTest ? 0 : 15` 제거
- rationale에서 "관련 테스트 코드 발견" 항목 제거

---

## D10. 관련 코드 저장 방식

### 선택: C — 기존 relatedCode를 JSON으로 교체

`AnalysisJob.relatedCode` 컬럼에 `List<RelatedCodeEntry>`를 JSON 직렬화해서 저장한다.
`AnalysisJobResponse.relatedCode`는 `List<RelatedCodeEntry>`로 반환한다.
기존 포맷팅된 문자열은 더 이상 사용하지 않는다.
현재 Web UI가 없으므로 하위 호환 문제 없음.

---

## D11. 점수 함수 구조와 우선순위

**상태: 방향(제안)일 뿐 아직 코드에 반영하지 않음. 현재 `CodeCandidateRanker`는 여전히 D3(곱연산).**
**→ MVP 이후 튜닝으로 미룸. 착수 시 `06-tuning-backlog.md` 참고.**
벤치마크를 MVP 완성 후 체계적으로 수행하기로 했으므로(→ 05-eval-harness.md),
근거 데이터 없이 지금 ranker를 바꾸지 않는다. 아래는 그때 harness로 검증하며 적용할 후보.

### 결정 11-1: 점수 결합 구조

- 대안 A: 현행 유지 — 곱연산(typeMultiplier) + 가산(hitBonus, fileBonus) 혼합
- **대안 B: 전부 가산 선형 통일 ← 선택**
  ```
  adjustedScore = baseScore + typeBonus + hitBonus + fileMatchBonus
  ```

선택 이유: 혼합 구조에서는 곱연산 효과가 압축된 base 범위(50~100)에 갇혀 구조적으로
hitBonus에 밀린다(스케일 불일치라 값 튜닝으로 못 고침). 전부 가산은 learning-to-rank의
가장 단순한 형태 → feature × weight 분리로 설명 가능성·튜닝 가능성·학습 데이터화가 따라온다.

### 결정 11-2: 우선순위 prior (전부 가산 전제)

"정밀도(도메인 클래스 정확 매치) vs 코로보레이션(여러 query 반복 등장)" 중 무엇을 세게 볼지.
세 대안 모두 구조는 전부 가산으로 동일하고 typeBonus/hitBonus 예산만 다르다.

- **대안 A: 정밀도 우선 ← 선택**
  ```
  typeBonus: DOMAIN+CLASS=+40, DOMAIN+기타=+15, RAW_REPORT=+20, KEYWORD=0
  hitBonus:  (hitCount-1)*7,  cap +21    fileBonus: 파일 내 매칭 >= 3 이면 +5
  → 정확한 도메인 매치가 반복매치를 확실히 이긴다
  ```
- 대안 B: 균형
  ```
  typeBonus: DOMAIN+CLASS=+25, DOMAIN+기타=+10, RAW_REPORT=+15, KEYWORD=0
  hitBonus:  (hitCount-1)*10, cap +30
  → 둘이 매우 가깝고, 극단에서만 반복이 근소하게 이김
  ```
- 대안 C: 코로보레이션 우선
  ```
  typeBonus: DOMAIN+CLASS=+15, DOMAIN+기타=+5,  RAW_REPORT=+10, KEYWORD=0
  hitBonus:  (hitCount-1)*15, cap +45
  → 반복 등장이 정밀도를 압도 (현재 곱연산 동작에 가깝고 더 강화된 방향)
  ```

주의: 위 수치는 검증 전 출발점(prior)일 뿐. 최종 weight는 harness 측정으로 결정한다.
적용 시 D3(곱연산)를 대체할 예정(**아직 대체 안 함**), 그때 04-result.md 서술도 갱신.

---

## D12. 랭킹 품질 평가 방식

### 선택: git-mined 정답셋 + 표준 지표

정답셋 = 대상 프로젝트 git log의 버그픽스 커밋이 건드린 파일.
지표 = Recall@1/5/10, MRR, MAP. 상세는 `05-eval-harness.md`.

수작업 gold set은 정확하지만 라벨링 비용이 커서 v1에서 제외.
git-mined의 leakage(커밋 메시지가 클래스명 언급)는 감수하고, 이후 토큰 마스킹으로 보강.

---

## D13. harness 구동 범위

### 선택: 전체 파이프라인 (LLM 포함)

유저가 실제 겪는 end-to-end(HYBRID 모드, LLM 구조화 포함)를 측정한다.
LLM 비결정성/비용 문제는 케이스별 `ReportSearchPreparation` 스냅샷/replay로 해소.
harness 코드는 `src/test` 소스셋에 둔다.
