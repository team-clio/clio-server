# 관련 코드 분석 흐름 추적

## 이 문서의 목적

버그 리포트로 찾은 코드 후보를, 실제 **호출/의존 흐름**(Controller → Service → Repository → Entity)으로
묶어서 "어떤 흐름이 영향받는지" 설명할 수 있게 한다. 구현 AI가 이 문서로 계획을 세운다.

## 배경

분석 흐름에서 이 문서의 범위는 다음 단계다.

```
버그 리포트 입력
-> 검색 입력 정규화 (완료)
-> 코드 검색 (완료)
-> 관련 코드 후보 선정 (완료, PR #3 / CodeCandidateRanker)
-> 관련 코드 흐름 확장       ← 이 문서의 범위
-> 점수화
-> 분석 결과 생성
```

`CodeCandidateRanker`는 파일 단위로 랭킹된 후보(`RankedCodeCandidate`) 목록을 준다.
하지만 후보들은 서로 **분리된 점**이다. 실제로는 Controller가 Service를 부르고 Service가
Repository를 부르는 **연결된 흐름**인데, 그 연결이 결과에 드러나지 않는다.

## 왜 필요한가

- **설명 가능성**: "이 리포트는 `PaymentController → PaymentService → PaymentRepository` 흐름에
  관련됩니다"라고 말할 수 있으면 후보 목록보다 훨씬 이해가 쉽다.
- **영향 범위**: 후보 하나만 봐서는 상류(호출자)/하류(피호출자) 영향이 안 보인다.
- **누락 보완**: 검색에는 안 걸렸지만 흐름상 반드시 관련되는 코드(예: 중간 Service)를 채운다.
- **미뤄둔 D9-B 흡수**: "테스트가 실제로 대상 클래스를 참조하는지"를 흐름/의존 그래프로 판단한다.

## 활용 가능한 기존 데이터 (새 인프라 불필요)

스캔 단계(`JavaCodeIndexer`)가 이미 심볼별로 아래를 저장한다.

- `CodeSymbol.role` = `CONTROLLER` / `SERVICE` / `REPOSITORY` / `ENTITY` / null
  (어노테이션 기반. `resolveRole`에서 이미 CONTROLLER까지 판별함)
- `CodeSymbol.imports` = 파일의 import 선언 전체 (개행 구분, FQN)
  예: `import ax.clio.payment.PaymentRepository;`
- `CodeSymbol.packageName`, `name`, `type`(CLASS/INTERFACE/...)
- `CodeFile.path`, `fileName`, `test`

→ 클래스 A의 FQN = `packageName + "." + name`.
→ A의 imports 안에 B의 FQN이 있으면 **A는 B에 의존한다**(A → B 엣지).
   이 그래프로 rule-based 흐름 추적이 가능하다. 벡터/RAG/바이트코드 분석 없이.

## 이 그래프의 한계 (설계에서 감수)

- **같은 패키지 내 참조는 import가 없다** → 엣지 누락. (초기엔 감수, 후속에서 필드 타입/동일
  패키지 휴리스틱으로 보강)
- **import는 클래스 단위**라 메서드 수준 호출 관계는 못 본다. (흐름은 "클래스 레이어" 수준)
- **와일드카드 import**(`import a.b.*;`)는 특정 클래스로 못 좁힌다. (패키지 단위 약한 엣지로만)
- 정적 import, 어노테이션만 쓰는 의존 등은 초기 범위 밖.

MVP 목표는 "정확한 콜그래프"가 아니라 **"레이어 흐름을 설명 가능한 수준"**이다.

## 개선 방향

### A. 의존 그래프 구축
프로젝트의 클래스 심볼로 노드를 만들고, import 매칭으로 방향 엣지를 만든다.

### B. 레이어 정렬
role로 레이어를 매긴다: `CONTROLLER(0) → SERVICE(1) → REPOSITORY(2) → ENTITY(3)`, null=UNKNOWN.

### C. 후보 기반 흐름 확장
랭킹된 후보를 시드로, 그래프를 따라 상/하류로 bounded 확장해 연결된 흐름(체인)을 만든다.

### D. 흐름 결과 구조화
흐름을 `CodeFlow`(정렬된 노드 리스트)로 만들어 분석 결과에 포함한다.
예: `PaymentController -> PaymentService -> PaymentRepository`.

### E. 점수화/테스트 반영
- 흐름이 여러 레이어에 걸치면 난이도/위험도에 반영.
- 흐름에 속한 프로덕션 클래스의 대응 테스트 존재 여부(D9-B)를 그래프로 판정.

## 범위

포함: 의존 그래프, 레이어 정렬, 후보 흐름 확장, 흐름 결과 구조화, 흐름 기반 점수 신호(가벼움).
제외: 메서드 수준 콜그래프, 바이트코드/타입 해석, 벡터 검색, 동일 패키지 엣지 보강(후속),
      LangGraph, LLM 요약.

## 관련 파일

```
src/main/java/ax/clio/code/CodeSymbol.java            - role, imports, packageName
src/main/java/ax/clio/code/CodeSymbolRepository.java  - findByProjectId
src/main/java/ax/clio/code/JavaCodeIndexer.java       - role/imports 생성 규칙
src/main/java/ax/clio/analysis/RankedCodeCandidate.java - 흐름 확장 시드
src/main/java/ax/clio/analysis/AnalysisWorker.java    - 흐름 확장 호출, 점수화
src/main/java/ax/clio/analysis/AnalysisResultDraft.java / AnalysisJob.java - 결과 저장
```

## 주의사항

1. LLM 없이도 동작(전 과정 rule-based, deterministic).
2. 기존 테스트 유지, 새 로직에 단위 테스트 추가.
3. ddl-auto: update 사용 → 엔티티 필드 추가로 저장 가능.
4. 그래프는 프로젝트 전체 심볼을 한 번 로드해 메모리에서 구성(초기 규모 가정).
