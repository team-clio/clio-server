# 02. Plan — 구현 단계 + 결정 포인트

## 구현 단계 (결정 확정 후 하나씩 커밋)

1. `Bug` 리포터/채널: 리포터 표현 추가 + 채널 표현(=D1, D2)
2. `BugStatus`에 `ANALYZING` 추가 (D3)
3. `Issue` 담당자 + AI 신뢰도 필드 추가 (D4, D5)
4. 이슈 재현 조건 표현 추가 (D6)
5. MCP API 키 엔티티 추가 (D7)
6. 관리자 계정 엔티티 추가 (D8)
7. 컴파일 확인 → 04-result

각 단계는 독립 커밋. 기능 관점 메시지(예: `feat: 버그 리포터/채널 필드 추가`).

## 결정 포인트

### D1. 리포터 표현 — 필드 vs 엔티티
- A. `Bug.reporterName`(String, nullable) — 디자인은 이름만 표시. 단순. **← 추천**
- B. 별도 `Reporter` 엔티티 + FK — 리포터 중복제거/재사용 가능하나 현재 과함.

### D2. 채널 표현 — BugSource 확장 vs 신규 enum
- A. `BugSource`에 `IN_APP_WIDGET`·`CUSTOMER_SUPPORT`·`EMAIL`·`SLACK` 추가(기존 값 유지).
     "리포트의 출처"를 하나로 통합. 필드 추가 없음. **← 추천**
- B. 신규 `ReportChannel` enum + `Bug.channel` 필드 별도.
     기술적 수집경로(source)와 사용자 채널(channel)을 분리하나 필드가 늘고 UI는 한 개만 표시.

### D3. BugStatus에 `ANALYZING` 추가 (분석 중)
- NEW → ANALYZING → TRIAGED → RESOLVED → IGNORED. 단순 추가. (대안 없음, 추천대로)

### D4. 이슈 담당자 — 필드 vs FK
- A. `Issue.assigneeName`(String, nullable) — "미지정"=null. 사용자 엔티티 없어 단순. **← 추천**
- B. 관리자/사용자 엔티티로 FK — 사용자 관리 미구현이라 과함.

### D5. AI 신뢰도 타입
- A. `BigDecimal aiConfidence`(0.00~1.00) — `IssueBug.confidence`와 일관. **← 추천**
- B. `Integer`(0~100 퍼센트).

### D6. 재현 조건(1·2·3 단계) 표현
- A. 자식 엔티티 `IssueReproductionStep`(issue_id, stepOrder, description) —
     관계형, 순서·개별 단계 명확. 디자인이 번호 매긴 목록. **← 추천**
- B. jsonb 컬럼(List<String>) — `BugOccurrence.rawPayload`처럼 간단하나 구조 약함.
- C. Lob 텍스트(줄바꿈 구분) — 가장 단순, 구조 없음.

### D7. MCP API 키 엔티티
- 위치: 신규 `ax.clio.mcp.entity.ApiKey` (화면명 "MCP 연동"). **← 추천** (대안: system/project 하위)
- 범위: `Project` FK로 프로젝트별 발급. **← 추천** (디자인이 프로젝트 컨텍스트)
- 필드: name(라벨), keyPrefix(표시용), keyHash(원문 미저장), revoked, lastUsedAt, createdAt.

### D8. 관리자 계정 엔티티
- 위치: `ax.clio.user.entity.AdminAccount`(빈 패키지 채움).
- 필드: username(unique), passwordHash, mustChangePassword(최초 랜덤 발급 후 변경 유도),
  createdAt, updatedAt.
- 결정: `mustChangePassword` 플래그 포함? **← 추천: 포함** (랜덤 발급 UX에 유용).
- username은 문자열 → "admin" / "admin@clio.dev" 둘 다 수용.
- 비밀번호 실제 랜덤 생성 Runner는 **후속 작업**(Repository/PasswordEncoder 필요).

## 컨펌 요청
D1~D8을 확정해 주세요. 추천대로 가도 되면 "추천대로"라고만 해주셔도 됩니다.
