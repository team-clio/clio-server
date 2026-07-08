# 구현 결과: 관련 코드 흐름 추적

## 신규 파일
- `analysis/CodeDependencyGraph.java` - 심볼 → 클래스 단위 의존 그래프(import 기반, 순수 로직)
- `analysis/FlowTracer.java` - 후보 시드 → 레이어 정렬된 CodeFlow 확장(@Component)
- `analysis/CodeFlow.java`, `analysis/FlowNode.java` - 흐름 결과 모델
- `test/.../CodeDependencyGraphTest.java` (6), `test/.../FlowTracerTest.java` (7)

## 수정 파일
- `analysis/AnalysisResultDraft.java` - `List<CodeFlow> flows` 추가
- `analysis/AnalysisJob.java` - `flows` JSON 컬럼 + 직렬화/역직렬화
- `analysis/AnalysisJobResponse.java` - flows 응답 추가
- `analysis/AnalysisWorker.java` - FlowTracer 호출, rationale에 영향 흐름, 난이도에 레이어 폭 반영

## 동작 요약
1. `CodeCandidateRanker`가 준 후보 파일들을 시드로 사용.
2. 프로젝트 심볼로 의존 그래프 구성: 노드=클래스 레벨 심볼(FQN), 엣지=import 정확 매칭(내부 노드끼리).
3. 시드에서 상/하류로 depth 2까지 bounded 확장. 노이즈를 줄이려 확장 노드는 role 있는 것만 포함.
4. 이웃이 겹치는 시드는 union-find로 하나의 흐름으로 병합.
5. 흐름 노드를 레이어 순(CONTROLLER→SERVICE→REPOSITORY→ENTITY→UNKNOWN) 정렬.
6. 결과: `PaymentController -> PaymentService -> PaymentRepository` 형태로 rationale/응답에 노출.

## 점수 반영 (가벼움)
- 난이도: 흐름이 걸친 distinct 레이어 수 × 5 가산(넓게 걸칠수록 수정 범위 큼).
- 위험도/중요도는 흐름으로 바꾸지 않음(기존 신호 유지).

## 한계 (설계에서 감수, 01-overview 참고)
- 같은 패키지 내 참조는 import가 없어 엣지 누락.
- 클래스 단위라 메서드 콜그래프는 아님.
- static/와일드카드 import는 엣지에서 제외.

## 남은 과제 (후속 PR)
- **D9-B**: 흐름 내 프로덕션 클래스의 대응 테스트 존재 여부를 그래프로 판정해 결과에 표시.
  (그래프의 dependents + test 노드로 판정 가능. D9 결정상 점수 반영은 하지 않고 표시용.)
- 동일 패키지 엣지 보강(필드 타입/생성자 파라미터 기반).
- 흐름 시각화 → Web UI 단계.
- 테스트: `EvalHarnessRunner`에 흐름 확장 on/off 비교(튜닝 백로그 연계).

## 테스트
`./gradlew test` 통과. 신규 13개(그래프 6 + 트레이서 7) 포함.
