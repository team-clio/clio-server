# 구현 결과

## 변경된 파일 목록

### 신규 파일
- `src/main/java/ax/clio/analysis/RankedCodeCandidate.java` - 검색 결과 중간 모델
- `src/main/java/ax/clio/analysis/CodeCandidateRanker.java` - 검색 결과 가공 로직 (가중치, 가산점, 그룹화)
- `src/main/java/ax/clio/analysis/RelatedCodeEntry.java` - 구조화된 관련 코드 항목
- `src/test/java/ax/clio/analysis/CodeCandidateRankerTest.java` - 단위 테스트 (12개)

### 수정 파일
- `src/main/java/ax/clio/code/CodeSearchResult.java` - symbolRole 필드 추가
- `src/main/java/ax/clio/code/CodeSearchService.java` - 검색 결과에 symbolRole 전달
- `src/main/java/ax/clio/analysis/AnalysisWorker.java` - CodeCandidateRanker 사용, 테스트 분리, 점수화 강화
- `src/main/java/ax/clio/analysis/AnalysisResultDraft.java` - relatedCodeEntries 필드 추가
- `src/main/java/ax/clio/analysis/AnalysisJob.java` - relatedCodeJson 컬럼 추가, JSON 직렬화
- `src/main/java/ax/clio/analysis/AnalysisJobResponse.java` - relatedCodeEntries 응답 추가, JSON 역직렬화

## 주요 변경 사항

### 1. 검색 결과 파이프라인 도입 (CodeCandidateRanker)

기존 AnalysisWorker의 `searchRelatedCode()` 로직을 `CodeCandidateRanker`로 추출했다.

핵심 로직:
- 검색 입력별로 CodeSearchService를 호출하고, 파일 단위로 결과를 그룹화
- 파일별 최고 점수 결과를 대표로 선택
- 서로 다른 query에서 매칭된 횟수(hitCount)를 기록
- 검색 입력 타입별 가중치(typeBonus)와 hitCount 가산점(hitBonus)을 적용
- 최종 adjustedScore 기준으로 정렬, 상위 20개 반환

### 2. 검색 입력 타입별 가중치

| 입력 타입 | 매치 타입 | 보너스 |
|-----------|----------|--------|
| CANDIDATE_DOMAIN | CLASS_NAME | +15 |
| CANDIDATE_DOMAIN | 그 외 | +5 |
| RAW_REPORT | 모든 타입 | +10 |
| KEYWORD | 모든 타입 | +0 (기준선) |

### 3. 다중 입력 반복 등장 가산점

- 기준: 파일 단위, 서로 다른 query에서 매칭된 횟수
- 가산: (hitCount - 1) * 10, 상한 30
- 예: 2개 query 매칭 → +10, 3개 → +20, 4개 이상 → +30

### 4. 파일 단위 그룹화

- CandidateAccumulator가 파일(filePath)별로 결과를 모음
- 파일 내 최고 점수 결과를 대표로 선택
- 같은 파일이 여러 결과로 상위 목록을 독점하는 문제 해결

### 5. 관련 테스트 분리

- candidates를 productionCode(test=false)와 testCode(test=true)로 분리
- 테스트 존재 여부를 점수화에 반영
- 분석 결과에 테스트 정보를 별도로 표시

### 6. 점수화 근거 강화

기존 (문자열 기반 판단):
```java
touchesEntity = snippet.contains("entity")
touchesRepository = filePath.contains("repository")
```

변경 후 (role 필드 기반):
```java
touchesEntity = "ENTITY".equals(symbolRole)
touchesRepository = "REPOSITORY".equals(symbolRole)
```

추가 반영 요소:
- 관련 역할 다양성 (distinctRoleCount) → 난이도에 반영
- 관련 패키지 수 (distinctPackageCount) → 난이도에 반영
- 높은 hitCount 존재 여부 → 중요도에 반영

### 7. 결과 구조화

- AnalysisJob에 relatedCodeJson 컬럼 추가 (JSON 문자열)
- 기존 relatedCode (포맷팅 텍스트)는 호환성을 위해 유지
- API 응답(AnalysisJobResponse)에서 JSON을 파싱해 List<RelatedCodeEntry>로 반환
- RelatedCodeEntry: filePath, symbolName, symbolRole, matchType, lineNumber, snippet, score, hitCount, test

## 테스트 결과

모든 테스트 통과 (31개 + 12개 신규 = 43개)

CodeCandidateRankerTest 테스트 항목:
- singleInputProducesBasicResult
- candidateDomainClassMatchGetsBonus
- candidateDomainNonClassMatchGetsSmallBonus
- rawReportMatchGetsBonus
- multipleInputHitsGiveHitCountBonus
- hitCountBonusIsCappedAt30
- fileGroupingKeepsBestScorePerFile
- resultsAreSortedByAdjustedScoreDescending
- maxResultsLimitedTo20
- testFilesAreIncludedInResults
- symbolRoleIsPreserved
- emptySearchInputsReturnEmptyResults

## 남은 과제

- 벤치마크: 실제 리포트로 검색 품질을 측정하고 가중치 값을 튜닝
- D9(B): DB에서 프로덕션 코드의 대응 테스트를 추가 조회하는 로직 (코드 흐름 추적 단계에서 구현)
- D10(B): relatedCodeJson을 별도 엔티티로 분리 (DB 스키마 안정화 후)
- 코드 흐름 추적 (Controller → Service → Repository)
- Vector Search / RAG
