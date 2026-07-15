# 패키지 컨벤션 정리

## 이 문서의 목적

지금 코드베이스에 **패키지 컨벤션이 두 가지 섞여** 있고, 일부는 직전 작업(`task-pipeline-ports`)의
`03-decisions.md`가 확정한 구조와도 **어긋나** 있다. 이를 **하나의 설명 가능한 규칙**으로 수렴시킨다.
**순수 구조 리팩터 — 기능 동작은 불변.**

## 배경

직전 작업에서 "전 도메인 레이어드 패키징"을 했으나, 확정 구조와 실제 구현 사이에 누락이 남았고
`04-result.md`는 일부를 실제와 다르게 서술하고 있다. 이번 작업은 그 **미완·불일치의 마감**이다.

## 진단 — 발견한 불일치 5건

### 1. 레이어드 vs 플랫 혼재

`project`·`report`·`code`·`llm` 은 `controller/service/repository/entity/dto` 로 나뉘어 있으나
`memory/*` 와 `analysis/job` 은 평평하다. 그래서 **같은 성격의 클래스가 다른 깊이**에 있다.

| 클래스 | 현재 위치 |
|--------|-----------|
| `BugReportController` | `report/controller/` |
| `DecisionMemoryController` | `memory/decision/` (엔티티·리포지토리와 한 폴더) |
| `AnalysisJobController` | `analysis/job/` (엔티티·리포지토리와 한 폴더) |

`analysis/job` 은 직전 결정문의 확정 구조에 `job/ controller·service·repository·entity·dto·config` 로
**명시돼 있는데 플랫으로 구현**됐다 — 의도가 아니라 누락.

### 2. `pipeline/port`·`pipeline/contract` 부재

직전 결정 P1·P2가 확정한 구조는 `pipeline/ + port/(6) + contract/(record)` 였으나, 실제로는
**인터페이스 6 + record 11 + enum 2 + 오케스트레이터 2 = 21개 파일이 `analysis/pipeline/` 한 폴더**에 섞여 있다.
`04-result.md` 는 "port·contract" 로 나뉜 것처럼 서술 — **문서와 코드가 불일치**.

### 3. `DefaultMemoryRetriever` 가 단계 패키지 없이 루트에

포트 구현 6개 중 5개는 각자 단계 패키지(`prepare`/`search`/`flow`/`scoring`/`report`)에 있는데
`DefaultMemoryRetriever` 만 `ax.clio.analysis` 바로 아래 떠 있다. `analysis/memory/` 가 있어야 할 자리.

### 4. 테스트 패키지가 대상과 불일치

`ReportSearchInputBuilderTest` 는 `ax.clio.analysis` 에 있으나 대상은 `ax.clio.analysis.search.ReportSearchInputBuilder`.
대상이 public 이라 컴파일만 되고 있다. `scoring` 테스트 디렉터리도 없다.

### 5. 곁가지

- `mcp/` 는 `package-info.java` 하나뿐인 빈 패키지. → **이번 범위 제외(사용자 결정: 그대로 둠)**
- `analysis/report`(리포트 생성 단계) 와 최상위 `report`(버그리포트 도메인) 는 이름이 같아 혼동을 준다.
  → 이번 범위 제외(개명은 영향 범위가 크고 별건).

## 개선 방향 — 하나의 규칙

> **컨트롤러(HTTP 표면)를 가진 도메인은 레이어드. 내부 컴포넌트 패키지는 평평하게 둔다.**

이 규칙이 **예외 없이 전체를 설명**한다.

- 현재 레이어드된 넷(`project`·`report`·`code`·`llm`)의 공통점 = 전부 HTTP CRUD 도메인.
- `memory/decision`(컨트롤러+dto+엔티티+리포지토리+서비스 완비) 과 `analysis/job`(컨트롤러 보유) 도
  같은 성격인데 혼자 평평 → **이들이 진짜 예외였다. 레이어드 대상.**
- `memory/code`·`memory/issue`·`memory/embedding` 은 HTTP 표면이 없는 **내부 RAG 인프라**.
  동료는 `report` 가 아니라 `analysis/prepare`·`search`·`flow`·`scoring` 같은 단계 패키지이고, 그것들도 평평하다.
  → **평평하게 유지가 맞다.**

### 왜 memory 전체 레이어드를 하지 않는가 (기록)

`memory/issue`(7파일)를 레이어드하면 `entity`·`repository`·`service`·`dto` 가 **각각 파일 1개**를 담는 디렉터리가 된다.
`memory/embedding`(인터페이스1+구현2)은 나눌 레이어 자체가 없어 **여전히 예외로 남는다** — 목표였던 균일함도 미달성.
일관성이 아니라 의식(ceremony)이 된다. 직전 P4를 뒤집는 게 아니라, **P4에 빠져 있던 근거를 채우는** 것.

## 범위

**포함**
1. `analysis/pipeline` → `port/` + `contract/` 분리 (결정문 P1·P2 복원)
2. `analysis/job` 레이어드 (결정문 확정 구조 복원)
3. `memory/decision` 레이어드 (규칙 적용)
4. `DefaultMemoryRetriever` → `analysis/memory/` 이동
5. 테스트 패키지를 대상에 정렬
6. 규칙 문서화 (재발 방지)
7. `task-pipeline-ports/04-result.md` 의 사실과 다른 서술 교정

**제외**
- `mcp/` 빈 패키지 (사용자 결정: 유지)
- `analysis/report` ↔ `report` 개명
- `memory/code`·`memory/issue`·`memory/embedding` 레이어드 (위 근거)
- 기능·알고리즘 변경, 성능 최적화

## 이 작업에서 정할 것 (plan에서 결정 포인트로 상세화)

- `contract` 안에서 record 를 더 나눌지(단계별 그룹핑) / 평평하게 둘지.
- `AnalysisState`·`AnalysisGraph` 의 위치(`pipeline` 루트 유지 여부).
- `memory/decision` 의 vectorsearch 3종을 `vectorsearch/` 로 뺄지 / `service/` 에 둘지.
- `analysis/job` 의 `AnalysisWorker`·`AnalysisTaskExecutorConfig` 배치.
- 규칙을 어디에 문서화할지(`mydocs/` vs `CLAUDE.md` vs `package-info.java`).
- 커밋 분할 단위.

## 주의사항

1. **동작 보존이 절대 1순위.** 순수 리팩터 — 전체 스위트 그린이 안전망.
2. 직전 작업이 남긴 **`pipeline ↔ job` 패키지 순환**(04-result 한계 1번)이 있다. port/contract 분리 시
   이 순환의 모양이 바뀔 수 있으니 확인 필요.
3. 대규모 import 변경 — 단계별 커밋 + 매 커밋 컴파일/테스트 통과.
4. 워크플로우: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
