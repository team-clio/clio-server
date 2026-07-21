# 03. Decisions

## D1. 리포터 표현
### 결정: A — `Bug.reporterName`(String) 필드
- 대안 A: `Bug.reporterName` String 필드 ← 선택
- 대안 B: 별도 `Reporter` 엔티티 + FK
선택 이유: 디자인은 리포터 이름만 표시. 사용자 관리/중복제거 요구가 없어 문자열 필드로 충분하고, 엔티티는 과함.

## D2. 채널 표현
### 결정: A — `BugSource` enum 확장
- 대안 A: 기존 BugSource에 IN_APP_WIDGET·CUSTOMER_SUPPORT·EMAIL·SLACK 추가 ← 선택
- 대안 B: 신규 `ReportChannel` enum + `Bug.channel` 별도 필드
선택 이유: UI는 리포트당 채널 한 개만 표시. "리포트의 출처"를 source 하나로 통합하는 편이 단순하고 필드가 늘지 않음.

## D3. BugStatus에 ANALYZING 추가
### 결정: 추가 (대안 없음)
NEW → ANALYZING → TRIAGED → RESOLVED → IGNORED. 디자인의 "분석 중" 상태 표현.

## D4. 이슈 담당자 표현
### 결정: A — `Issue.assigneeName`(String) 필드
- 대안 A: `Issue.assigneeName` String, "미지정"=null ← 선택
- 대안 B: 관리자/사용자 엔티티로 FK
선택 이유: 사용자 관리 미구현. 문자열로 단순 표현, null=미지정.

## D5. AI 신뢰도 타입
### 결정: A — `BigDecimal aiConfidence`(0.00~1.00)
- 대안 A: BigDecimal 0~1 ← 선택
- 대안 B: Integer 0~100 퍼센트
선택 이유: `IssueBug.confidence`(BigDecimal)와 일관.

## D6. 재현 조건 표현
### 결정: 보류 (사용자 확인 대기)
- 대안 A: 자식 엔티티 `IssueReproductionStep`
- 대안 B: jsonb 컬럼(List<String>)
- 대안 C: Lob 텍스트

## D7. MCP API 키 엔티티
### 결정: 추천대로 — `ax.clio.mcp.entity.ApiKey`, Project FK
- 위치: 신규 `ax.clio.mcp.entity.ApiKey` (화면명 "MCP 연동")
- 범위: `Project` FK로 프로젝트별 발급
- 필드: name, keyPrefix(표시용), keyHash(원문 미저장), revoked, lastUsedAt, createdAt
선택 이유: 디자인이 MCP/프로젝트 컨텍스트. 원문 키 대신 해시 저장이 표준.

## D8. 관리자 계정 엔티티
### 결정: 추천대로 — `ax.clio.user.entity.AdminAccount`, mustChangePassword 포함
- 필드: username(unique), passwordHash, mustChangePassword, createdAt, updatedAt
- 랜덤 비밀번호 생성 Runner는 후속 작업(Repository/PasswordEncoder 필요)
선택 이유: 최초 랜덤 발급 → 변경 유도 UX에 mustChangePassword가 유용. username 문자열로 admin/이메일 모두 수용.
