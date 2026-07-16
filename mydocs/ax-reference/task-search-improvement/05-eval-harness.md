# 평가 harness 설계: 랭킹 품질 측정

## 왜 이게 필요한가

우리 서비스의 핵심은 "버그 리포트 → 관련 코드 파일 랭킹"이다.
이 랭킹이 좋은지 나쁜지를 **숫자로 측정**할 수 없으면, 점수 계산의 weight는
영원히 감(感)으로 미는 매직 넘버로 남는다.

핵심 기술로 키우려면 순서가 이렇다:

```
점수 함수를 feature × weight로 분리   (튜닝 가능한 형태)
        +
랭킹 품질을 재는 평가 harness         (튜닝의 나침반)  ← 이 문서
        →
weight를 감이 아니라 측정으로 결정
        →
(장기) feature 벡터 로깅 → learning-to-rank 학습
```

## 정답셋을 git log에서 얻는 이유

랭킹을 채점하려면 각 리포트의 "정답 파일"이 필요하다. 정답 소스는 둘뿐:

1. **사람이 직접 라벨링** — 정확하지만 코드베이스를 깊이 아는 사람이 리포트당
   수십 분. 수십 개 모으기도 부담.
2. **이미 있는 정답 재활용 (git log)** ← 채택

통찰: **버그는 결국 고쳐지고, 고칠 때 개발자는 특정 파일을 수정한다.
그 수정된 파일이 곧 그 버그의 "관련 코드"다.** git은 어떤 커밋이 어떤 파일을
바꿨는지 전부 기록한다. 즉 버그픽스 커밋 하나 = 우리 문제의 정답 딸린 인스턴스.

```
과거 커밋:
  메시지  = "Fix NPE in payment cancellation when order already refunded"
  변경파일 = PaymentService.java, RefundValidator.java

평가:
  1. 메시지를 가짜 리포트로 파이프라인에 투입
  2. 파이프라인이 랭킹된 파일 목록을 반환
  3. 변경파일이 몇 위에 나왔나 측정 (1~2위=좋음, 47위=weight 틀림)
```

버그픽스 커밋 N개에 반복 → Recall@10, MRR 같은 집계 숫자.
weight를 바꿔 이 숫자가 오르면 개선, 내리면 개악. **감이 측정으로 바뀐다.**

라벨링 비용 0으로 수백 개 평가·학습 데이터가 생기고, 나중에 learning-to-rank로
갈 때 그대로 학습셋이 된다.

## 아키텍처

```
GroundTruthMiner   ── Project.rootPath의 git log → (리포트텍스트, 정답파일들)
     │
     ▼
EvalCase[]         ── record(id, reportTitle, reportBody, Set<relevantFilePaths>)
     │
     ▼
RankingEvaluator   ── 각 케이스: 검색입력 조립 → CodeSearchService
     │                 → CodeCandidateRanker.rank() → 랭킹된 파일 목록
     │                 → 정답 파일의 랭킹 위치 기록
     ▼
CaseOutcome[]      ── 케이스별 reciprocalRank, recall@k, averagePrecision
     │
     ▼
EvalScorecard      ── 집계: MRR, Recall@1/5/10, MAP
     │
     ▼
Runner             ── 프로젝트 지정 → 점수판 출력. weight 바꿔가며 재실행
```

## 정답셋 마이닝 규칙

- `git log --no-merges --numstat`로 `커밋 → 변경파일` 추출
- 버그픽스 커밋 식별: 메시지가 `fix|bug|issue|close #...` 등 매칭 (초기 휴리스틱)
- 리포트 텍스트 프록시 = 커밋 subject + body
- 정답 파일 = 그 커밋이 바꾼 `.java` 소스 중 **현재 스캔에 존재하는 것만**
- 경로 정규화: git 경로(repo root 기준) ↔ CodeFile.path(rootPath 기준) 일치시킴
  (rootPath가 repo 하위 디렉터리면 prefix 제거)

### Leakage 함정

커밋 메시지가 클래스명을 직접 말하면("Fix **PaymentService** rounding") 검색이
실제 유저의 애매한 리포트보다 후해진다. 보정 옵션: 정답 파일/클래스명 토큰을
쿼리에서 마스킹. **v1은 leakage 감수하고 첫 숫자 확보, 이후 보강.**

## 지표

버그 로컬라이제이션 분야 표준:

- **Recall@k** (k=1,5,10): 정답 파일이 상위 k 안에 든 비율
- **MRR** (Mean Reciprocal Rank): 첫 정답의 순위 역수 평균
- **MAP** (Mean Average Precision): 정밀도 곡선 아래 면적 평균

## 구동 범위: 전체 파이프라인 (LLM 포함)

유저가 실제 겪는 end-to-end를 그대로 측정 (D13).
`HYBRID` 모드 → `LlmReportSearchPreparer`가 candidateDomains/keywords 생성.

문제: LLM은 비결정적·유료 → 매번 다르면 weight 튜닝이 흔들림.
**해결: 케이스별 LLM 출력(`ReportSearchPreparation`)을 첫 실행 때 스냅샷 저장,
재실행 땐 replay.** "현실성"과 "반복 가능성"을 둘 다 확보.

## 구현 단계 (완료)

모든 코드는 `src/test/java/ax/clio/analysis/eval/`. 프로덕션 아티팩트에 안 섞임.

```
Step A. 순수 코어      EvalCase, RankingMetrics(RR·Recall@k·AP),        ✅ 8 테스트
                       CaseOutcome, EvalScorecard
Step B. git 마이너     GroundTruthMiner (git log 파싱, 버그픽스 휴리스틱, ✅ 6 테스트
                       노이즈 임계값; parse()는 git 없이 단위 테스트)
Step C. 파이프라인 연결 RankingEvaluator (real ReportSearchInputBuilder    ✅ 3 테스트
                       + CodeCandidateRanker 구동, PreparationProvider seam)
Step D. LLM 스냅샷     SnapshotPreparationProvider (케이스당 1회 호출 후   ✅ 3 테스트
                       디스크 replay)
Step E. 러너           EvalHarnessRunnerIT (@SpringBootTest, 실제 빈 배선) ✅ (opt-in)
        + PathReconciler (repo-root ↔ project-root 경로 정합)            ✅ 6 테스트
```

전체 60 테스트 green.

### 러너 실행 방법

`EvalHarnessRunnerIT`는 `CLIO_EVAL_REPO`가 없으면 skip되어 일반 CI를 방해하지 않는다.
실제 벤치마크:

```
CLIO_EVAL_REPO=/path/to/repo \
CLIO_EVAL_PROJECT_ID=1 \
CLIO_EVAL_LIMIT=300 \
CLIO_EVAL_LLM_CONFIG_ID=1 \   # 생략 시 결정적 RAW_ONLY
./gradlew test --tests '*EvalHarnessRunnerIT'
```

전제: 대상 프로젝트가 clio에 스캔되어 CodeFile/CodeSymbol이 채워져 있어야 함.

## 다음: 이 harness로 무엇을 하나

1. 기준 프로젝트로 첫 점수판 확보 (baseline: 현재 곱연산 ranker)
2. `CodeCandidateRanker`를 D11(전부 가산, 정밀도 우선)로 리팩터 → 재측정, 개선 확인
3. weight를 feature × weight로 외부화 → grid search 튜닝
4. (장기) feature 벡터 로깅 → learning-to-rank

## 열린 이슈

- 버그픽스 커밋 식별 휴리스틱의 정밀도 (false positive 커밋 섞임)
- 리팩터링/멀티파일 커밋의 노이즈 (정답 파일이 너무 많은 케이스 제외 임계값)
- leakage 마스킹을 언제 도입할지
- 평가에 쓸 기준 프로젝트 선정 (git 히스토리 풍부한 Java 레포)
