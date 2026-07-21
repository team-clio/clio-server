# 03. Decisions

## D1. DTO 파일 단위
### 결정: A — record 1개 = 파일 1개
- 대안 A: record 1개=파일 1개 ← 선택
- 대안 B: 도메인당 묶음 파일(예: `IssueDtos` 안 중첩 record)
선택 이유: 검색·diff·리뷰에 유리, 표준 관행. B는 '컨트롤러 안 중첩'과 비슷한 묶음으로 회귀.

## D2. 공통 래퍼 위치
### 결정: A — `ax.clio.common.dto`로 통합
- 대안 A: common/dto로 통합, PageResponse/ListResponse 단일화 ← 선택
- 대안 B: 각 도메인 dto에 개별 유지(PageResponse 중복 잔존)
선택 이유: 페이지네이션 응답 스펙을 한 곳에서 관리, 중복 제거가 핵심 유지보수 이득.

## 관리 전략(적용 규칙)
- 위치: `<도메인>/dto/`, 공통은 `common/dto`.
- 네이밍: 요청 `<동사><대상>Request`, 응답 `<대상>Response`.
- 경계: 엔티티를 컨트롤러/JSON에 직접 노출 금지. 매핑은 응답 record 정적 팩토리 `from(entity)`(구현 단계).
- 검증: request record에 Bean Validation(구현 단계).
- 불변성: record 유지.
