# 코드 컨벤션 (clio-server)

이 문서는 Java/Spring API 서버의 패키지 배치와 의존 규칙을 정한다.
작업 절차는 [`workflow-rules.md`](workflow-rules.md)를 따른다.

## 패키지 규칙

> **컨트롤러(HTTP 표면)를 가진 도메인은 레이어드. 내부 컴포넌트 패키지는 평평하게 둔다.**

새 클래스를 놓을 자리를 정할 때 이 한 줄로 판단한다.

### 레이어드 도메인 (`@RestController` 를 가진 것)

`project` · `report` · `code` · `llm` · `analysis/job` · `memory/decision`

```
<도메인>/
├─ controller/   @RestController
├─ service/      비즈니스 로직 (worker·indexer·scanner 등 실행 컴포넌트 포함)
├─ repository/   Spring Data 리포지토리
├─ entity/       JPA 엔티티 + 그 enum
└─ dto/          요청·응답 record
```

레이어가 아닌 **성격상 별개인 것**만 추가 하위 패키지를 만든다. 현재 쓰는 것: `config`(`llm`·`analysis/job`),
`client`(`llm`), `vectorsearch`(`memory/decision`). 레이어 이름을 늘리지 말고, 정말 성격이 다를 때만 만든다.

### 평평하게 두는 패키지 (HTTP 표면 없는 내부 컴포넌트)

- `memory/code` · `memory/issue` · `memory/embedding` — 내부 RAG 인프라.
- `analysis/prepare` · `search` · `flow` · `memory` · `scoring` · `report` — 파이프라인 단계 impl.
- `common` — 횡단 관심사(`@RestControllerAdvice` 인 `GlobalExceptionHandler` 포함. 도메인이 아니므로 예외 아님).

**왜 평평한가**: 이들을 레이어드하면 `entity`·`repository`·`service`·`dto` 가 각각 **파일 1개**를 담는
디렉터리가 된다(`memory/issue` 기준). `memory/embedding` 은 인터페이스1+구현2라 나눌 레이어 자체가 없다.
일관성이 아니라 의식(ceremony)이 된다.

### 파이프라인(`analysis/pipeline`) 특칙

```
analysis/pipeline/
├─ AnalysisGraph · AnalysisState   오케스트레이터 (포트에만 의존)
├─ port/       단계 인터페이스 6개 (= 그래프 노드 = 교체 단위)
└─ contract/   단계 간 오가는 데이터 record·enum
```

**의존 규칙 (위배 금지)**

```
pipeline(port + contract)  ◀── prepare / search / flow / memory / scoring / report
```

- 단계 impl 은 `pipeline`(port·contract)에만 의존한다. **단계끼리 서로 참조하지 않는다.**
- `contract` 는 `port` 를 모른다(중립).
- 단계 간에 오가는 타입은 반드시 `contract` 에 둔다. 한 단계 패키지에 두면 하류가 그 단계를 import 하게 되고,
  그게 곧 "한 단계를 바꾸면 다른 단계가 깨지는" 결합이다.

## 테스트 배치

테스트는 **대상 클래스와 같은 패키지**에 둔다. 레이어를 가로지르는 통합 테스트만 도메인 루트에 둔다
(예: `memory/decision/DecisionMemoryIntegrationTest`).
