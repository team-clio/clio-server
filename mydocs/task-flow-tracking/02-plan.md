# 구현 계획: 관련 코드 흐름 추적

## 단계 요약

```
Step 1. 의존 그래프 코어      CodeDependencyGraph (심볼 → 노드/엣지, 순수 로직)
Step 2. 흐름 확장            FlowTracer (후보 시드 → 레이어 정렬된 CodeFlow 목록)
Step 3. 분석 파이프라인 연결   AnalysisWorker에서 흐름 추적 호출
Step 4. 결과 구조화          AnalysisResultDraft/Job/Response에 flows 저장
Step 5. 점수/테스트 신호      흐름 레이어 폭·테스트 대응(D9-B)을 점수화에 반영
```

Step 1~2가 핵심(순수·테스트 용이). 3~5는 얇게 연결.

---

## Step 1. 의존 그래프 코어 — `CodeDependencyGraph`

순수 로직(스프링 의존 없음), 생성자에 `List<CodeSymbol>`를 받아 그래프를 만든다.

- **노드**: 클래스 레벨 심볼(CLASS/INTERFACE/RECORD/ENUM)만. key = FQN(`packageName.name`).
  - filePath, className, role, test 보관.
  - 같은 FQN 중복 시 첫 클래스 노드 유지.
- **엣지 A→B**: A의 `imports` 문자열에 B의 FQN이 등장하면 추가. (프로젝트 내부 노드끼리만)
  - import 파싱: 개행 분리 → `import` / `static` / `;` 제거 → FQN 토큰. 와일드카드(`.*`)는
    패키지 prefix로만 취급(초기엔 정확 매칭만, 와일드카드는 후속).
- **조회 API**: `nodeByFilePath(path)`, `dependenciesOf(fqn)`(A→B), `dependentsOf(fqn)`(B←A),
  `layerOf(role)`.

테스트: FQN 매칭 엣지 생성, 내부 노드끼리만 연결, role→layer, 역방향 조회.

---

## Step 2. 흐름 확장 — `FlowTracer` (`@Component`)

의존: `CodeSymbolRepository`.

```
List<CodeFlow> trace(Long projectId, List<String> candidateFilePaths)
```

1. `findByProjectId`로 심볼 로드 → `CodeDependencyGraph` 구성.
2. 후보 filePath → 노드로 매핑(매칭 안 되면 skip).
3. 각 시드에서 bounded 확장:
   - 하류(dependenciesOf)와 상류(dependentsOf) 모두 depth 제한(기본 2)까지 방문.
   - 방문 노드를 하나의 연결요소(흐름)로 모음(서로 연결된 시드는 같은 흐름으로 병합).
4. 각 흐름 노드를 layer 오름차순 정렬(CONTROLLER→…→ENTITY, UNKNOWN 마지막) →
   `CodeFlow(List<FlowNode>)`.
5. 흐름을 크기·시드 점수 순으로 정렬, 상한(기본 5개) 적용.

레코드:
```
FlowNode(String filePath, String className, String role, boolean test)
CodeFlow(List<FlowNode> nodes)   // layer 정렬됨
```

테스트: C→S→R 체인이 하나의 흐름으로 layer 순 정렬됨, 무관 노드 미포함,
depth 상한, 후보 매칭 실패 시 빈 결과, 시드 2개가 연결되면 병합.

---

## Step 3. 파이프라인 연결

`AnalysisWorker.run()`에서 `candidates` 확보 후:
```
List<CodeFlow> flows = flowTracer.trace(projectId, candidatePaths);
```
`buildDraft(...)`에 flows 전달.

---

## Step 4. 결과 구조화

- `AnalysisResultDraft`에 `List<CodeFlow> flows` 추가.
- `AnalysisJob`에 `flowsJson`(직렬화) 컬럼 추가 — PR #3의 relatedCodeJson과 동일 패턴.
- `AnalysisJobResponse`에 flows 파싱해 반환.
- rationale에 흐름 요약 라인 추가(예: `- 영향 흐름: PaymentController -> PaymentService -> PaymentRepository`).

---

## Step 5. 점수/테스트 신호 (가벼움)

- **난이도**: 흐름이 걸친 distinct layer 수가 많을수록 +. (레이어 전반 수정 필요)
- **위험도**: 흐름이 REPOSITORY/ENTITY 레이어까지 닿으면 +.
- **테스트 대응(D9-B)**: 흐름 내 프로덕션 클래스에 대해, 그래프에서 그 클래스를 import 하는
  test=true 노드가 있으면 "대응 테스트 있음". `hasRelatedTest`를 이 판정으로 교체.
  - 주의: 03-decisions D9에서 "테스트 존재를 점수에 반영하지 않기로" 했음 → 점수 반영은 하지
    않고, **결과 표시용 신호로만** 노출(설명 가능성). 점수 반영 여부는 튜닝 백로그로.

---

## 의존/순서

```
Step 1 → Step 2 → Step 3 → Step 4 → Step 5
```
Step 1~2는 순수 단위 테스트로 검증 후 연결. Step 5는 결과 표시 우선, 점수 반영은 보류.

## 범위 밖
- 메서드 콜그래프, 동일 패키지 엣지 보강, 와일드카드 정밀 해석 → 후속.
- 흐름 시각화 UI → Web UI 단계.
