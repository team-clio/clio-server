# 패키지 컨벤션 정리 결과

결정(P1~P8) 확정 후 구현 완료. 순수 구조 리팩터 — **동작 불변, 매 커밋 전체 스위트 그린.**

## 무엇을 했나

### 1. 단계 간 결합 해소 (S1·S2) — 이번 작업의 실제 수확

발단은 "패키지 컨벤션이 안 맞는다"였는데, 조사 중 **직전 작업의 수용 기준이 실제로 깨져 있는 것**을 발견했다.
`task-pipeline-ports/04-result.md` 는 "단계 impl은 pipeline에만 의존 → 단계 간 결합 없음"이라 서술했으나:

| 발견 | 실체 | 처리 |
|------|------|------|
| `scoring → flow` | `RuleBasedScorer` 가 `CodeDependencyGraph.layerOf` 직접 호출 — **진짜 결합** | `layerOf`/`UNKNOWN_LAYER` 를 계약(`FlowNode`)으로 이동 |
| `report → prepare` | javadoc `{@link}` 전용 import — 장식 | import 제거 |

`layerOf(String role)` 는 그래프 상태와 무관한 순수 함수이고 role 어휘의 소유자는 이미 `FlowNode.role()`
(계약)이었다 — 애초에 자리가 틀렸던 것. 이제 **flow 구현을 갈아끼워도 scoring 이 안 깨진다.**

### 2. 파이프라인 port/contract 분리 (S3)

`analysis/pipeline` 21개 평면 → `port/`(6 인터페이스) + `contract/`(13 record·enum) + 루트(`AnalysisGraph`·
`AnalysisState`). 직전 결정 P1·P2 가 확정했으나 구현되지 않았던 구조를 복원.

### 3. 단계 패키지 완성 (S4)

`DefaultMemoryRetriever` 가 단계 패키지 없이 `analysis` 루트에 떠 있던 것을 `analysis/memory/` 로 이동.
→ **6단계 = 6포트 = 6패키지** 대응이 완성되고, `analysis` 루트에 떠 있는 클래스가 0이 됨.

### 4. 레이어드 (S5·S6)

규칙("컨트롤러를 가진 도메인은 레이어드")에 따라 **`analysis/job`** 과 **`memory/decision`** 을 레이어드.
이 둘이 컨트롤러를 가지고도 혼자 평평했던 진짜 예외였다.

### 5. 정렬·문서화 (S7)

- 테스트 2개를 대상 패키지로 정렬(`ReportSearchInputBuilderTest` → `analysis/search`,
  `AnalysisResultDraftTest` → `pipeline/contract`).
- **규칙을 `mydocs/workflow-rules.md` "패키지 규칙" 절로 문서화**(재발 방지).
- `task-pipeline-ports/04-result.md` 의 사실과 다른 서술 3곳 정정.

## 수렴한 규칙

> **컨트롤러(HTTP 표면)를 가진 도메인은 레이어드. 내부 컴포넌트 패키지는 평평하게 둔다.**

**검증**: `@RestController` 7개가 **전부** `*/controller/` 에 있다(예외 0).
`common/GlobalExceptionHandler` 는 `@RestControllerAdvice` 로 횡단 관심사 — 도메인이 아니므로 규칙 밖.

**memory 를 전량 레이어드하지 않은 근거**(P4 를 뒤집은 게 아니라 빠져 있던 근거를 채운 것):
`memory/issue` 를 레이어드하면 `entity`·`repository`·`service`·`dto` 가 각각 **파일 1개**를 담는 디렉터리가
되고, `memory/embedding`(인터페이스1+구현2)은 나눌 레이어 자체가 없어 **여전히 예외로 남는다** — 목표였던
균일함도 미달성. 일관성이 아니라 의식(ceremony)이 된다.

## 검증

- **매 커밋 전체 스위트 그린.** `AnalysisGraphTest`(포트 조합·rule-based 점수 보존)가 동작 보존 안전망.
- `@SpringBootTest contextLoads` 가 재편된 전 패키지의 빈 부팅 확인.
- S2 는 유일하게 코드가 움직인 단계 → `FlowNodeTest` 신설로 `layerOf` 매핑·경계(null·미상 role) 고정.
- 이동·import 는 컴파일러를 안전망으로 반복 수정.
- **의존 규칙 기계적 확인**: 단계 impl 이 다른 단계 impl 을 import 하는 건수 = 0. `contract` 가 `port` 를
  import 하는 건수 = 0.

## 최종 구조

```
ax.clio/
├─ common/                      횡단(ApiResponse·BusinessException·GlobalExceptionHandler)
├─ project/  report/  code/  llm/     controller·service·repository·entity·dto (+llm: client·config)
├─ memory/
│   ├─ decision/                controller·service·repository·entity·dto·vectorsearch  ← 레이어드
│   └─ code/ · issue/ · embedding/     평평 (내부 RAG 인프라)
└─ analysis/
    ├─ pipeline/                AnalysisGraph·AnalysisState + port/(6) + contract/(13)
    ├─ prepare/ search/ flow/ memory/ scoring/ report/   단계 impl (평평)
    └─ job/                     controller·service·repository·entity·dto·config  ← 레이어드
```

## 정직한 한계 / 남은 것

1. **`pipeline ↔ job` 순환은 그대로다**(P4로 범위 제외). 원인은 규명했다 — 포트 6개 중 5개가 시그니처에
   JPA 엔티티(`AnalysisJob`·`BugReport`)를 노출해, 중립이어야 할 `pipeline` 이 `analysis.job` 을 의존한다.
   **해소는 후속 태스크**: 계약 record(예: `AnalysisRequest`) 도입으로 포트에서 엔티티를 걷어내는 설계 변경.
   `task-pipeline-ports/04-result.md` 한계 1번을 이 원인으로 갱신해뒀다.
2. `mcp/` 빈 패키지(`package-info.java` 뿐)는 사용자 결정으로 유지.
3. `analysis/report`(리포트 생성 단계) ↔ 최상위 `report`(버그리포트 도메인) 이름 충돌은 범위 밖 — 읽을 때
   혼동 요인으로 남아 있다.
4. 순수 리팩터라 로드맵 항목 진행도는 불변(기능 추가 없음).
