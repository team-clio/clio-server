# 04. Result

## 변경 요약 (엔티티 계층)

| # | 변경 | 파일 | 결정 |
|---|---|---|---|
| 1 | 리포터 이름 필드 추가 | `Bug.reporterName` | D1 |
| 2 | 채널 값 추가 | `BugSource` +IN_APP_WIDGET·CUSTOMER_SUPPORT·EMAIL·SLACK | D2 |
| 3 | 분석 중 상태 추가 | `BugStatus.ANALYZING` | D3 |
| 4 | 담당자 필드 추가 | `Issue.assigneeName` | D4 |
| 5 | AI 신뢰도 필드 추가 | `Issue.aiConfidence`(BigDecimal 5,4) | D5 |
| 6 | MCP API 키 엔티티 | `ax.clio.mcp.entity.ApiKey` (Project FK, keyHash) | D7 |
| 7 | 관리자 계정 엔티티 | `ax.clio.user.entity.AdminAccount` (mustChangePassword) | D8 |

## 검증
- `./gradlew compileJava` 통과.

## 남은 과제
- **D6 재현 조건(보류)**: 이슈 재현 단계 목록 저장 방식 미결정. 후속에서 결정 후 구현
  (추천: 자식 엔티티 `IssueReproductionStep`).
- **관리자 최초 실행 랜덤 비밀번호 Runner(후속)**: `AdminAccount` 엔티티만 준비됨.
  실제 생성·출력에는 Repository + PasswordEncoder(보안 의존성) 도입 필요.
- 전반적으로 Repository/Service/Controller 구현은 이 작업 범위 밖(엔티티 계층만).
