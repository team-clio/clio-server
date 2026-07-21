# 01. Overview — 디자인 대비 부족한 엔티티/필드 보강

## 배경

관리자 콘솔 디자인 4개 화면(버그 리포트 / 이슈 / MCP 연동 / 시스템 설정)을 현재 엔티티와
대조한 결과, 화면에 표시되는 일부 정보가 엔티티에 존재하지 않는다. 디자인이 요구하는 데이터를
담을 수 있도록 엔티티/필드/enum을 보강한다.

## 현재 상태

- 엔티티 계층 + 스텁 컨트롤러만 존재. Repository/Service/Runner 계층은 아직 없다.
- 관련 엔티티: `Bug`, `BugSource`, `BugStatus`, `Severity`, `Issue`, `IssueStatus`, `Priority`,
  `IssueBug`, `LlmProvider`, `LlmModel` 등.
- `ax.clio.user.entity` 패키지는 비어 있음(과거 사용자 필드 제거, 커밋 418b8eb).

## 대조로 드러난 공백(=이번 작업 범위)

### ① 버그 리포트 화면
- **리포터 이름**(김민서·이준호 등): `Bug`에 필드 없음.
- **채널**(인앱 위젯·고객센터·이메일·Slack): `BugSource` enum(API/SENTRY/LOG/MANUAL)과 불일치.
- **상태 "분석 중"**: `BugStatus`(NEW/TRIAGED/RESOLVED/IGNORED)에 해당 값 없음.

### ② 이슈 화면
- **담당자**(미지정/김지훈 등): `Issue`에 필드 없음.
- **AI 신뢰도**(96%): `Issue`에 필드 없음(`IssueBug.confidence`는 버그별).
- **재현 조건**(1·2·3 단계 목록): `Issue`에 표현 수단 없음.

### ③ MCP 연동 화면
- **발급 API 키**: MCP 서버 인증용 API 키를 담을 엔티티 없음.

### ④ 시스템 설정 화면
- **관리자 계정**(로그인 ID/비밀번호): 관리자 계정 엔티티 없음.
  - 사용자 방침: 최초 실행 시 ID `admin`, 비밀번호는 랜덤 발급.

## 범위 / 비범위

- **범위**: 위 공백을 메우는 JPA 엔티티·필드·enum 추가(엔티티 계층).
- **비범위(이번 작업 아님)**: Repository/Service/Controller 구현, 최초 실행 시 admin 랜덤
  비밀번호를 실제로 생성·출력하는 Runner. (Repository/PasswordEncoder 등 인프라 도입이
  필요하며 현재 코드베이스에 아직 없음 → 후속 작업으로 분리, plan에서 결정 포인트로 다룸.)

## 확인 요청

이 overview 범위가 맞는지 컨펌해 주세요. 컨펌되면 02-plan에서 각 항목의 구현 방식과
**결정 포인트(대안 포함)**를 정리하겠습니다.
