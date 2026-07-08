# 구현 계획: 코드 후보 탐색 개선

## 전제

- 이 문서는 `01-overview.md`에서 정리한 6가지 개선 방향(A~F)을 구현 가능한 단계로 나눈다.
- 각 단계는 독립적으로 커밋 가능한 단위다.
- 결정이 필요한 지점은 `03-decisions.md`에 기록하고 진행한다.
- 기존 테스트를 깨뜨리지 않는다. 변경된 로직에 대해 테스트를 추가한다.

## 단계 요약

```
Step 1. 검색 결과 중간 모델 도입
Step 2. 검색 입력 타입별 가중치 적용
Step 3. 다중 입력 반복 등장 가산점
Step 4. 파일 단위 그룹화 및 대표 결과 선정
Step 5. 관련 테스트 분리
Step 6. 점수화 근거 강화
Step 7. 결과 구조화 (AnalysisJob 저장 형태 개선)
```

---

## Step 1. 검색 결과 중간 모델 도입

### 목적

현재 `CodeSearchResult`는 검색 엔진이 반환하는 raw 결과다. 이후 단계(가중치, 가산점, 그룹화, 테스트 분리)를 적용하려면, 검색 결과를 가공하는 중간 모델이 필요하다.

### 할 일

`AnalysisWorker.searchRelatedCode()`가 `CodeSearchResult`를 직접 반환하는 대신, 가공된 중간 결과를 반환하도록 바꾼다.

새 record를 하나 만든다. 이름 후보: `RankedCodeCandidate` 또는 `ScoredCodeMatch`.

```java
public record RankedCodeCandidate(
    Long fileId,
    String filePath,
    String symbolName,
    CodeSymbolType symbolType,
    String symbolRole,          // ENTITY, SERVICE, REPOSITORY, null
    CodeSearchMatchType matchType,
    Integer lineNumber,
    String snippet,
    boolean test,
    int baseScore,              // CodeSearchService가 준 원점수
    int adjustedScore,          // 가중치, 가산점 적용 후 점수
    int hitCount,               // 몇 개 검색 입력에서 매칭됐는지
    List<ReportSearchInputType> matchedInputTypes  // 어떤 타입의 입력에서 매칭됐는지
)
```

### 결정 필요

- **D1. 중간 모델 이름**: `RankedCodeCandidate` vs `ScoredCodeMatch` vs 다른 이름
- **D2. symbolRole을 어디서 가져올 것인가**: 현재 `CodeSearchResult`에 role이 없다. `CodeSymbol.role`을 검색 결과에 포함시켜야 한다. `CodeSearchResult`에 role 필드를 추가할 것인가, 아니면 중간 모델에서 별도로 조회할 것인가.

### 변경 대상

- 새 파일: `src/main/java/ax/clio/analysis/RankedCodeCandidate.java` (또는 결정된 이름)
- 수정: `AnalysisWorker.searchRelatedCode()` 반환 타입 변경
- 수정: `AnalysisWorker.buildDraft()` 입력 타입 변경

### 기존 코드 참고

현재 `CodeSearchResult`에는 role 정보가 없다:

```java
// CodeSearchResult.java
public record CodeSearchResult(
    Long fileId, String filePath, String symbolName, CodeSymbolType symbolType,
    CodeSearchMatchType matchType, Integer lineNumber, String snippet,
    int score, boolean test
)
```

`CodeSymbol`에는 role이 있다:

```java
// CodeSymbol.java - role 필드
@Column(length = 80)
private String role;  // "ENTITY", "SERVICE", "REPOSITORY", 또는 null
```

---

## Step 2. 검색 입력 타입별 가중치 적용

### 목적

`ReportSearchInputType`(RAW_REPORT, KEYWORD, CANDIDATE_DOMAIN)에 따라 검색 결과의 점수를 조정한다.

### 할 일

검색을 수행할 때 어떤 타입의 입력으로 검색했는지 추적하고, 타입별 가중치를 적용한다.

현재 `searchRelatedCode()`에서 `input.query()`만 사용하고 `input.type()`은 무시된다. 이를 수정한다.

### 가중치 적용 방향

```
CANDIDATE_DOMAIN + CLASS_NAME 매치   → 높은 보너스 (도메인이 정확히 맞은 경우)
CANDIDATE_DOMAIN + METHOD_NAME 매치  → 보통 보너스
KEYWORD + 모든 매치 타입             → 기본 (가중치 없음, 기준선)
RAW_REPORT + 모든 매치 타입          → 약간의 보너스 (원문이 직접 매칭되면 의미 있음)
```

### 결정 필요

- **D3. 가중치 방식**: 가산(additive) vs 곱연산(multiplier)
  - 가산: `adjustedScore = baseScore + typeBonus`
  - 곱연산: `adjustedScore = baseScore * typeMultiplier`
  - 가산이 더 예측 가능하고 디버깅이 쉽다. 곱연산은 기본 점수가 높은 결과에 더 큰 차이를 만든다.
- **D4. 구체적인 가중치 값**: 초기값을 정하고 나중에 벤치마크로 튜닝한다.

### 변경 대상

- 수정: `AnalysisWorker.searchRelatedCode()` - 입력 타입 정보를 결과에 전달
- 수정/신규: 가중치 계산 로직 (별도 클래스 또는 AnalysisWorker 내 메서드)

---

## Step 3. 다중 입력 반복 등장 가산점

### 목적

"payment"로도 찾이고 "order"로도 찾인 파일은, 하나의 키워드로만 찾인 파일보다 관련 가능성이 높다.

### 할 일

검색 결과를 합칠 때, 같은 파일(또는 같은 심볼)이 여러 검색 입력에서 등장한 횟수를 세고, 횟수에 따라 가산점을 준다.

### 구현 방향

```
1. 각 검색 입력별로 결과를 수집하면서 (입력 → 결과 목록) 매핑을 유지
2. 파일 단위로 등장 횟수를 센다
3. hitCount >= 2이면 가산점 적용
4. hitCount를 RankedCodeCandidate에 기록
```

### 결정 필요

- **D5. 반복 등장 기준**: 파일 단위 vs 심볼 단위
  - 파일 단위: 같은 파일이 여러 입력에서 나오면 가산. 더 넓은 매칭.
  - 심볼 단위: 같은 클래스/메서드가 여러 입력에서 나오면 가산. 더 정밀한 매칭.
  - 파일 단위가 초기 구현에 적합할 수 있다. 심볼 단위는 같은 파일의 다른 메서드가 각각 다른 키워드로 매칭되는 경우를 놓칠 수 있다.
- **D6. 가산점 크기와 상한**: hitCount당 몇 점을 줄 것인가. 무한히 늘어나지 않도록 상한이 필요한가.

### 변경 대상

- 수정: `AnalysisWorker.searchRelatedCode()` - 결과 수집 로직 전면 변경

### 현재 코드 참고

현재 중복 제거 로직:

```java
// AnalysisWorker.java:67-71
.collect(Collectors.toMap(
    result -> result.filePath() + ":" + result.lineNumber() + ":" + result.matchType(),
    result -> result,
    (left, right) -> left.score() >= right.score() ? left : right
))
```

이 로직은 중복을 "제거"만 하고 "몇 번 등장했는지"는 기록하지 않는다.

---

## Step 4. 파일 단위 그룹화 및 대표 결과 선정

### 목적

같은 파일에서 10개의 CODE_TEXT 매칭이 나오면 결과 상위 20개를 한 파일이 독점할 수 있다. 파일 단위로 묶어서 다양한 파일이 노출되게 한다.

### 할 일

```
1. 검색 결과를 파일 단위로 그룹화
2. 파일 내 최고 점수 결과를 대표로 선택
3. 파일 내 매칭 수를 파일 관련도 신호로 활용
4. 파일별 대표 결과를 점수순으로 정렬
5. 필요하면 파일 내 세부 매칭 정보도 보존
```

### 결정 필요

- **D7. 최종 결과 형태**: 
  - A: 파일별 대표 결과 1개만 남기고, 파일 내 매칭 수는 메타데이터로 보존
  - B: 파일별 대표 결과 + 파일 내 상위 N개 세부 매칭을 함께 보존
  - A가 단순하고 결과 목록이 깔끔하다. B는 정보량이 많지만 복잡도가 올라간다.
- **D8. 파일 내 매칭 수가 점수에 영향을 주는가**: 매칭이 많은 파일에 가산점을 줄 것인가, 아니면 hitCount(다중 입력 반복)와 분리할 것인가.

### 변경 대상

- 수정: `AnalysisWorker.searchRelatedCode()` - 그룹화 로직 추가
- 중간 모델에 파일 내 매칭 수 필드 추가 가능

---

## Step 5. 관련 테스트 분리

### 목적

테스트 파일을 관련 코드와 분리해서 관리한다. "이 프로덕션 코드에 테스트가 있는가"를 판단할 수 있게 한다.

### 할 일

```
1. 검색 결과에서 test=true인 결과를 별도 목록으로 분리
2. 프로덕션 코드 후보에 대해 대응하는 테스트가 존재하는지 판단
3. 테스트 존재 여부를 점수화에 반영
4. 분석 결과에 테스트 정보를 포함
```

### 테스트 대응 판단 방법

현재 사용 가능한 정보:
- `CodeFile.test` 필드로 테스트 파일 여부 판별 가능
- `CodeFile.fileName`으로 파일명 패턴 매칭 가능 (예: `PaymentService.java` ↔ `PaymentServiceTest.java`)
- `CodeSymbol.imports`로 테스트 파일이 대상 클래스를 import하는지 확인 가능

초기 구현:
- 파일명 패턴 매칭 (`XxxTest.java` → `Xxx.java`)
- 이후 고도화: import 기반 참조 확인

### 결정 필요

- **D9. 테스트 대응 판단 범위**: 
  - A: 검색 결과에 포함된 테스트만 확인 (현재 범위 내)
  - B: 검색 결과에 없더라도 DB에서 관련 프로덕션 코드의 테스트 파일을 추가 조회
  - A는 단순하지만 검색에 걸리지 않은 테스트를 놓칠 수 있다. B는 더 정확하지만 추가 쿼리가 필요하다.

### 변경 대상

- 수정: `AnalysisWorker.searchRelatedCode()` - 테스트 분리 로직
- 수정: `AnalysisWorker.buildDraft()` - 테스트 정보 활용
- 수정 가능: `AnalysisResultDraft` - 테스트 관련 필드 추가

### 현재 코드 참고

현재 테스트 판단 방식:

```java
// AnalysisWorker.java:82
boolean hasRelatedTest = relatedCode.stream().anyMatch(CodeSearchResult::test);
```

검색에 걸린 테스트 파일이 하나라도 있으면 true다. 이 테스트가 실제로 관련 프로덕션 코드에 대한 테스트인지는 확인하지 않는다.

---

## Step 6. 점수화 근거 강화

### 목적

중요도/난이도/위험도 점수 계산에 실제 검색 결과의 특성을 반영한다.

### 할 일

현재 점수화는 주로 `ReportSearchPreparation`(LLM 구조화 결과)에 의존한다. 검색 결과에서 얻은 정보를 점수 계산에 추가한다.

### 추가 반영할 요소

**난이도에 추가:**
- 매칭된 심볼의 역할 다양성 (SERVICE + REPOSITORY + ENTITY가 모두 매칭되면 높은 난이도)
- 매칭된 파일이 속한 패키지 수 (여러 패키지에 걸치면 높은 난이도)

**위험도에 추가:**
- 매칭된 심볼 중 ENTITY 역할 존재 여부 (현재는 snippet에서 "entity" 문자열 검색으로 판단 → role 필드 기반으로 변경)
- 매칭된 심볼 중 REPOSITORY 역할 존재 여부 (현재는 filePath에서 "repository" 문자열 검색 → role 필드 기반으로 변경)
- 관련 테스트 부재의 가중치 세분화

**중요도에 추가:**
- 검색 hitCount가 높은 결과가 많으면 여러 관점에서 관련되는 이슈 → 중요도 상승

### 변경 대상

- 수정: `AnalysisWorker.buildDraft()` - 점수 계산 로직
- 입력 타입: `List<CodeSearchResult>` → `List<RankedCodeCandidate>`로 변경 (Step 1 이후)

### 현재 코드 참고

현재 Entity/Repository 판단 방식 (문자열 기반, 부정확):

```java
// AnalysisWorker.java:83-86
boolean touchesEntity = relatedCode.stream().anyMatch(result ->
    result.symbolType() == CodeSymbolType.CLASS
    && result.snippet() != null
    && result.snippet().toLowerCase().contains("entity"));
boolean touchesRepository = relatedCode.stream().anyMatch(result ->
    result.filePath().toLowerCase().contains("repository"));
```

이를 `RankedCodeCandidate.symbolRole` 기반으로 변경하면 더 정확해진다.

---

## Step 7. 결과 구조화 (AnalysisJob 저장 형태 개선)

### 목적

`AnalysisJob.relatedCode`가 현재 포맷팅된 문자열이다. 이를 구조화된 형태로 바꿔서 UI나 후속 분석에서 개별 항목을 다룰 수 있게 한다.

### 할 일

```
1. 관련 코드 목록을 JSON 형태로 저장하거나 별도 엔티티로 분리
2. 관련 테스트 목록을 별도로 저장
3. 점수화 근거를 항목별로 저장
4. API 응답에서 구조화된 형태로 반환
```

### 결정 필요

- **D10. 저장 방식**:
  - A: `AnalysisJob.relatedCode`를 JSON 문자열로 저장 (엔티티 변경 최소화)
  - B: 별도 엔티티 `AnalysisRelatedCode`를 만들어 1:N 관계로 저장
  - A는 빠르게 구현 가능하고 ddl-auto와 잘 맞는다. B는 쿼리/인덱싱에 유리하지만 스키마 복잡도가 올라간다.
  - MVP 단계이므로 A가 적합할 수 있다. 나중에 B로 전환하는 것은 어렵지 않다.

### 변경 대상

- 수정: `AnalysisResultDraft` - relatedCode 타입 변경
- 수정: `AnalysisJob.complete()` - 저장 방식 변경
- 수정: `AnalysisJobResponse` - 응답 형태 변경
- 수정: `AnalysisWorker.buildDraft()` - 결과 조립 방식 변경

### 현재 코드 참고

현재 relatedCode 포맷팅:

```java
// AnalysisWorker.java:94-98
String related = relatedCode.stream()
    .map(result -> "- " + result.filePath() + ":" + result.lineNumber()
        + " [" + result.matchType() + ", score=" + result.score() + "] "
        + nullToEmpty(result.snippet()))
    .collect(Collectors.joining("\n"));
```

현재 AnalysisJob 저장:

```java
// AnalysisJob.java:71
@Column(length = 10000)
private String relatedCode;
```

---

## 구현 순서와 의존 관계

```
Step 1 (중간 모델)
  ├→ Step 2 (타입별 가중치) ─── Step 1이 있어야 adjustedScore를 기록할 수 있다
  ├→ Step 3 (반복 등장)    ─── Step 1이 있어야 hitCount를 기록할 수 있다
  └→ Step 5 (테스트 분리)  ─── Step 1이 있어야 test 정보를 분리할 수 있다

Step 2 + Step 3
  └→ Step 4 (그룹화)       ─── 가중치와 가산점이 적용된 후 그룹화해야 한다

Step 1 + Step 5
  └→ Step 6 (점수화 강화)  ─── 중간 모델과 테스트 정보가 있어야 점수를 개선할 수 있다

Step 4 + Step 6
  └→ Step 7 (결과 구조화)  ─── 최종 결과 형태가 결정된 후 저장 방식을 바꿔야 한다
```

권장 구현 순서:

```
1. Step 1 → Step 2 → Step 3 → Step 4  (검색 결과 파이프라인)
2. Step 5 → Step 6                      (테스트 분리 + 점수화)
3. Step 7                                (저장 형태 개선)
```

Step 2, 3, 5는 Step 1만 있으면 병렬 진행 가능하지만, 순차 진행이 더 안전하다.

---

## 테스트 전략

### 단위 테스트 추가 대상

| Step | 테스트 대상 | 검증 내용 |
|------|------------|----------|
| 1 | RankedCodeCandidate 생성 | CodeSearchResult → RankedCodeCandidate 변환 |
| 2 | 타입별 가중치 | CANDIDATE_DOMAIN + CLASS_NAME이 KEYWORD + CLASS_NAME보다 높은 점수 |
| 3 | 반복 등장 가산점 | 2개 입력에서 등장한 파일이 1개 입력에서만 등장한 파일보다 높은 점수 |
| 4 | 파일 그룹화 | 같은 파일의 결과가 1개로 합쳐지고, 다양한 파일이 노출됨 |
| 5 | 테스트 분리 | 테스트 파일이 별도 목록으로 분리되고, 프로덕션 코드와 대응됨 |
| 6 | 점수화 | Entity/Repository 역할이 role 필드 기반으로 정확히 반영됨 |
| 7 | 결과 구조화 | JSON 직렬화/역직렬화가 정상 동작함 |

### 기존 테스트 영향

- `ReportSearchInputBuilderTest` → 영향 없음 (검색 입력 조립은 변경하지 않음)
- `LlmReportSearchPreparerTest` → 영향 없음 (LLM 구조화는 변경하지 않음)
- `CodebaseDomainCandidateProviderTest` → 영향 없음
- `ClioApplicationTests` → Step 7에서 엔티티 변경 시 컨텍스트 로딩 확인 필요

---

## 범위 밖 (하지 않는 것)

- `CodeSearchService` 검색 알고리즘 자체 변경 (symbol search, file body search 로직)
- `ReportSearchInputBuilder` 변경
- `ReportSearchPreparer` / LLM 관련 변경
- Vector Search / RAG 추가
- 새로운 DB 테이블 추가 (Step 7에서 JSON 컬럼 방식 선택 시)
- API 엔드포인트 추가/변경 (응답 형태만 변경)
