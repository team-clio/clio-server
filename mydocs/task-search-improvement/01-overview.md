# 버그 리포트 기반 코드 후보 탐색 개선

## 이 문서의 목적

이 문서는 Clio의 "버그 리포트 기반 코드 후보 탐색" 단계를 개선하기 위한 작업 개요다.
구현을 담당하는 AI가 이 문서를 읽고 구현 계획을 세우고 실행할 수 있도록, 현재 상태, 문제점, 개선 방향, 고려사항을 정리한다.

## 배경

Clio는 버그 리포트를 받아 로컬 코드베이스에서 관련 코드를 찾고, 이슈의 중요도/난이도/위험도를 판단하는 서비스다.

분석 흐름은 다음과 같다.

```
버그 리포트 입력
-> 검색 입력 정규화 (LLM 또는 원문)
-> 코드 검색
-> 관련 코드 후보 선정       ← 이 문서의 범위
-> 점수화
-> 분석 결과 생성
```

검색 입력 정규화(LLM 기반 구조화)까지는 구현이 완료됐다.
이제 검색 결과를 더 정확하게 고르고 정리하는 단계를 개선해야 한다.

## 현재 구현 상태

### 검색 흐름 (`AnalysisWorker.searchRelatedCode`)

```java
private List<CodeSearchResult> searchRelatedCode(Long projectId, List<ReportSearchInput> searchInputs) {
    return searchInputs.stream()
        .flatMap(input -> codeSearchService.search(projectId, input.query(), 10).stream())
        .collect(Collectors.toMap(
            result -> result.filePath() + ":" + result.lineNumber() + ":" + result.matchType(),
            result -> result,
            (left, right) -> left.score() >= right.score() ? left : right
        ))
        .values()
        .stream()
        .sorted((left, right) -> Integer.compare(right.score(), left.score()))
        .limit(20)
        .toList();
}
```

현재 동작:
1. 검색 입력 목록(RAW_REPORT, KEYWORD, CANDIDATE_DOMAIN)을 순회
2. 각 입력으로 `CodeSearchService.search()`를 호출 (입력당 최대 10개)
3. `filePath:lineNumber:matchType` 기준으로 중복 제거 (높은 점수 유지)
4. 점수 내림차순 정렬
5. 상위 20개 제한

### 검색 엔진 (`CodeSearchService.search`)

두 단계로 검색한다.

1. **Symbol Search** - DB에 인덱싱된 CodeSymbol을 조회
   - 심볼 이름, 어노테이션, 파일명에 대해 `contains` 매칭
   - 매치 타입별 기본 점수: CLASS_NAME(100), METHOD_NAME(90), ANNOTATION(80), FILE_NAME(70)
   - 테스트 파일: -10 감점

2. **File Body Search** - 실제 파일 내용을 읽어서 텍스트 매칭
   - 매치 점수: CODE_TEXT(50)
   - 테스트 파일: -10 감점

### 검색 입력 구조 (`ReportSearchInput`)

```java
public record ReportSearchInput(
    String query,
    ReportSearchInputType type  // RAW_REPORT, KEYWORD, CANDIDATE_DOMAIN
)
```

현재는 `type`이 저장만 되고 검색 로직에서 사용되지 않는다.
모든 입력이 동일한 방식으로 `CodeSearchService.search()`에 전달된다.

### 점수화 (`AnalysisWorker.buildDraft`)

검색 결과를 받아서 중요도/난이도/위험도를 계산한다.

- **중요도**: 증상 키워드 + 핵심 도메인 키워드 기반
- **난이도**: 관련 파일 수 + 도메인 수 + 테스트 부재
- **위험도**: 도메인 수 + Entity/Repository 접촉 + 테스트 부재

### 현재 데이터 모델

```
CodeFile: id, project, path, fileName, language, test, sizeBytes, lastModifiedAt
CodeSymbol: id, project, file, name, type, role, packageName, startLine, endLine, annotations, imports
CodeSearchResult: fileId, filePath, symbolName, symbolType, matchType, lineNumber, snippet, score, test
```

CodeSymbol의 `role`은 ENTITY, SERVICE, REPOSITORY 중 하나이거나 null이다.
CodeSymbol의 `type`은 CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, METHOD, CONSTRUCTOR, FIELD 중 하나다.

## 현재 구현의 문제점

### 1. 검색 입력 타입이 검색 전략에 반영되지 않는다

`ReportSearchInputType`이 RAW_REPORT, KEYWORD, CANDIDATE_DOMAIN으로 구분되지만, 실제 검색에서는 모두 같은 방식으로 처리된다.

예를 들어:
- `CANDIDATE_DOMAIN: "Payment"` → 이건 클래스명/패키지명 매칭에 집중해야 한다
- `RAW_REPORT: "결제는 됐는데 주문 내역에 안 떠요"` → 이건 한글이라 코드 텍스트 검색에서 거의 매칭되지 않는다
- `KEYWORD: "payment"` → 이건 심볼 + 코드 텍스트 모두에서 넓게 검색해야 한다

### 2. 여러 입력에서 반복 등장하는 파일/심볼에 가산점이 없다

"payment"로도 찾이고 "order"로도 찾인 파일은, 하나의 키워드로만 찾인 파일보다 관련 가능성이 높다.
현재는 중복 제거만 하고 가산점은 주지 않는다.

### 3. 중복 제거 기준이 너무 세밀하다

`filePath:lineNumber:matchType`이 중복 키다.
같은 파일의 다른 라인이 각각 별개 결과로 남으므로, 하나의 파일이 결과 목록을 독점할 수 있다.
파일 단위로 대표 결과를 골라야 하는지 고려해야 한다.

### 4. 관련 코드와 관련 테스트가 분리되지 않는다

테스트 파일은 -10 감점만 받을 뿐 별도로 분리되지 않는다.
"이 코드에 관련 테스트가 있는가"는 위험도/난이도 판단에 중요한 신호인데, 현재는 이 정보가 명확하게 추출되지 않는다.

### 5. 점수 계산에 검색 근거가 충분히 반영되지 않는다

현재 점수화는 대부분 LLM 구조화 결과(symptoms, candidateDomains)에 의존한다.
실제 검색 결과의 특성(어떤 역할의 코드가 매칭됐는지, 몇 개 검색 입력에서 매칭됐는지)이 점수에 반영되지 않는다.

### 6. 결과 포맷이 문자열이다

`AnalysisJob.relatedCode`가 String 필드에 포맷팅된 텍스트로 저장된다.
UI나 후속 분석에서 개별 항목을 다루기 어렵다.

## 개선 방향

### A. 검색 입력 타입별 가중치 적용

검색 입력 타입에 따라 결과 점수에 가중치를 적용한다.

고려할 점:
- CANDIDATE_DOMAIN은 도메인 단위 매칭이므로 CLASS_NAME 매치에 높은 가중치
- KEYWORD는 범용 검색이므로 기본 가중치
- RAW_REPORT는 원문이므로 매칭되면 의미 있지만 기본적으로 기대치가 낮다
- 가중치는 곱연산(multiplier)인지 가산(additive)인지 결정해야 한다

### B. 다중 입력 반복 등장 가산점

같은 파일 또는 같은 심볼이 여러 검색 입력에서 반복 등장하면 가산점을 준다.

고려할 점:
- "같은 결과"의 기준을 어떻게 잡을 것인가 (파일 단위? 심볼 단위?)
- 가산점 크기와 상한
- 반복 등장 횟수 자체를 결과에 포함할 것인가

### C. 파일 단위 그룹화 및 대표 결과 선정

같은 파일에서 여러 매칭이 나온 경우 파일 단위로 묶고 대표 결과를 고른다.

고려할 점:
- 파일 내 최고 점수 결과를 대표로 할 것인가
- 파일 내 매칭 수 자체가 파일의 관련도를 높이는 신호인가
- 파일 단위로 묶되 개별 매칭 정보는 보존해야 하는가

### D. 관련 테스트 분리

검색 결과에서 테스트 파일을 별도로 분리해서 관리한다.

고려할 점:
- 테스트 파일은 `CodeFile.test` 필드로 판별 가능
- "관련 코드 X에 대한 테스트가 존재하는가"를 판단하려면, 테스트 파일이 대상 클래스를 import하거나 참조하는지 확인해야 한다
- 단순히 검색에 걸린 테스트가 아니라, 관련 프로덕션 코드에 대응하는 테스트를 찾아야 한다
- 테스트 존재 여부를 점수화에 반영해야 한다

### E. 점수화 근거 강화

검색 결과의 특성을 점수화에 반영한다.

고려할 점:
- 매칭된 심볼의 역할(ENTITY, SERVICE, REPOSITORY)별 가중치
- 매칭된 심볼의 타입(CLASS, METHOD, FIELD)별 가중치
- 여러 검색 입력에서 반복 등장한 결과에 대한 가중치
- Entity/Repository 매칭이 위험도에 미치는 영향
- 테스트 존재 여부가 난이도/위험도에 미치는 영향

### F. 결과 구조화

관련 코드 목록을 문자열이 아닌 구조화된 형태로 저장한다.

고려할 점:
- DB 스키마 변경이 필요한가 (별도 테이블? JSON 컬럼?)
- 현재 MVP는 `ddl-auto: update`를 사용하므로 엔티티 변경으로 충분할 수 있다
- 기존 `relatedCode` 문자열 필드와의 호환성
- UI에서 개별 항목을 다루기 위한 최소 구조

## 범위 제한

이번 작업에 포함하는 것:
- 검색 입력 타입별 가중치 적용
- 다중 입력 반복 등장 가산점
- 중복 제거 기준 개선
- 관련 테스트 분리
- 점수화 근거 강화
- 결과 구조화

이번 작업에 포함하지 않는 것:
- Vector Search / RAG 추가
- LangGraph 적용
- LLM 리포트 생성
- Web UI
- DB evidence 테이블 설계
- 벤치마크

## 관련 파일

구현 시 읽어야 할 핵심 파일:

```
src/main/java/ax/clio/analysis/AnalysisWorker.java          - 분석 실행, 검색 호출, 점수화
src/main/java/ax/clio/analysis/AnalysisJob.java             - 분석 결과 엔티티
src/main/java/ax/clio/analysis/AnalysisResultDraft.java     - 분석 결과 draft record
src/main/java/ax/clio/analysis/ReportSearchInput.java       - 검색 입력 record
src/main/java/ax/clio/analysis/ReportSearchInputBuilder.java - 검색 입력 조립
src/main/java/ax/clio/analysis/ReportSearchPreparation.java - LLM 구조화 결과
src/main/java/ax/clio/code/CodeSearchService.java           - 코드 검색 엔진
src/main/java/ax/clio/code/CodeSearchResult.java            - 검색 결과 record
src/main/java/ax/clio/code/CodeSymbol.java                  - 코드 심볼 엔티티
src/main/java/ax/clio/code/CodeFile.java                    - 코드 파일 엔티티
```

참고할 문서:

```
mydocs/analysis-pipeline-assumptions.md   - 분석 파이프라인 전제
mydocs/report-search-plan-schema.md       - 검색 입력 스키마 설계
mydocs/remaining-roadmap.md               - 개발 로드맵
mydocs/remaining-report.md                - 진행 상태 추적
```

## 구현 시 주의사항

1. **기존 테스트를 깨뜨리지 않는다.** 기존 테스트가 있으므로 먼저 확인하고 필요시 함께 수정한다.
2. **MVP 단계다.** 과도하게 복잡한 설계보다 명확하고 테스트 가능한 구현을 우선한다.
3. **ddl-auto: update를 사용 중이다.** 엔티티 변경이 자동으로 DB에 반영된다. Flyway migration은 나중에 도입한다.
4. **LLM 없이도 동작해야 한다.** RAW_ONLY 모드에서도 개선된 검색이 동작해야 한다.
5. **점수화는 deterministic이어야 한다.** 같은 입력이면 같은 점수가 나와야 한다.
6. **검색 결과의 근거를 추적 가능하게 유지한다.** 왜 이 코드가 관련 후보로 선정됐는지 설명할 수 있어야 한다.
