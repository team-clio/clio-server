# 01. Overview — 컨트롤러 DTO를 dto 패키지로 분리

## 배경
세 컨트롤러(`BugReportController`·`IssueController`·`LlmSettingsController`)가 요청/응답 record를
컨트롤러 클래스 안에 중첩해 두고 있었다. `mydocs/workflow-rules.md`의 패키지 규칙은
요청·응답 record를 도메인별 `dto/` 패키지에 두라고 명시한다. 스텁 단계의 임시 형태를 규칙에 맞춘다.

## 문제
- `dto/` 패키지 부재, record가 컨트롤러에 중첩.
- `PageResponse<T>`가 bug·issue 컨트롤러에 **중복 정의**, `ListResponse<T>`는 system에만.

## 범위
- 중첩 record 19개를 도메인별 `dto/` 패키지로 이동(record 1개=파일 1개).
- 공통 래퍼(`PageResponse<T>`·`ListResponse<T>`)는 `ax.clio.common.dto`로 통합해 중복 제거.
- 컨트롤러는 import만 바꾸고 엔드포인트 시그니처/동작은 그대로(스텁 유지).

## 비범위
- Service/Repository 구현, Bean Validation 실제 부착, 엔티티↔DTO 매핑 로직(구현 단계에서).
