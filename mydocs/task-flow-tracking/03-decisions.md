# 구현 결정 히스토리

## F1. 의존 관계 신호

### 선택: A — import 정확 매칭

`CodeSymbol.imports`(스캔 시 저장된 FQN import 선언)를 파싱해, 클래스 A의 import에
클래스 B의 FQN(`packageName.name`)이 정확히 등장하면 A→B 엣지로 본다.

대안(필드 타입/생성자 파라미터/메서드 호출)은 더 정확하지만 파싱 비용이 크고 스캔 데이터에
없다. MVP는 이미 저장된 import만으로 시작한다. 동일 패키지 참조(import 없음) 누락은 감수.

---

## F2. 노드 단위

### 선택: 클래스 레벨 심볼만

CLASS/INTERFACE/RECORD/ENUM만 노드로 만든다. METHOD/FIELD/CONSTRUCTOR는 제외.
흐름은 "레이어(클래스) 수준"이지 메서드 콜그래프가 아니다.

---

## F3. 레이어 순서

### 선택: role 기반 고정 순번

`CONTROLLER(0) → SERVICE(1) → REPOSITORY(2) → ENTITY(3) → UNKNOWN(맨 뒤)`.
role은 스캔 단계(`JavaCodeIndexer.resolveRole`)가 어노테이션으로 이미 판별해 둔 값을 쓴다.

---

## F4. 확장 방향과 깊이

### 선택: 상/하류 양방향, depth 2

시드에서 dependencies(하류)와 dependents(상류)를 모두 따라 depth 2까지 확장한다.
Controller가 시드면 하류로 Service/Repository를, Repository가 시드면 상류로 Service/Controller를
잡기 위함. depth는 허브 노드 폭발을 막는 상한(기본 2, 파라미터화).

---

## F5. 확장 노드 필터 (노이즈 제어)

### 선택: 시드는 무조건, 확장 노드는 role 있는 것만

확장 중 만난 노드는 role이 있는(레이어에 속한) 경우만 흐름에 포함한다. 공통 util 같은
role 없는 클래스가 흐름을 오염시키는 걸 막는다. 시드 노드는 role과 무관하게 포함.

주의: 이 필터로 인해 role 없는 중간 노드를 "거쳐" 도달하는 role 노드는 놓칠 수 있다.
초기엔 감수(대부분의 레이어 클래스는 role을 가진다).

---

## F6. 시드 병합 기준

### 선택: 이웃 집합이 겹치면 union-find로 병합

여러 후보(시드)의 확장 이웃 집합이 서로 겹치면 하나의 흐름으로 합친다.
예: Controller 시드와 Repository 시드가 같은 Service를 공유하면 한 흐름.

---

## F7. 흐름 결과 저장 방식

### 선택: PR #3(relatedCode)와 동일 — JSON 컬럼

`AnalysisJob.flows` 컬럼에 `List<CodeFlow>`를 JSON 직렬화. 별도 엔티티는 스키마 안정화 후.
`ddl-auto: update`라 컬럼 추가로 충분.

---

## F8. 점수 반영 범위

### 선택: 난이도에만 가볍게 (레이어 폭)

흐름이 걸친 서로 다른 레이어 수 × 5를 난이도에 가산. 넓게 걸칠수록 수정 범위가 크다는 신호.
위험도/중요도는 기존 신호를 유지(흐름으로 흔들지 않음). 값은 잠정 → 튜닝 백로그 대상.

---

## F9. 테스트 대응 판정 (D9-B)

### 선택: 이번 범위에서 제외 (후속)

"흐름 내 프로덕션 클래스에 대응 테스트가 있는가"는 그래프로 판정 가능하지만, 이번 PR에는
넣지 않는다. 근거 없는(소비처 없는) 코드가 남지 않게, 실제 결과 표시/활용과 함께 후속 PR에서
구현한다. 03-plan Step 5 참고. (D9 결정상 점수 반영은 하지 않고 표시용.)
