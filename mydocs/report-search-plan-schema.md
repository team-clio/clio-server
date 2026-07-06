# 리포트 기반 검색 입력 정규화

## 목적

이 문서는 버그 리포트를 하이브리드 검색에 넣기 전, 어떤 검색 입력으로 정규화할지 정리한다.

중요한 방향:

- 이 서비스는 버그 리포트 NLP 서비스가 아니다.
- 목표는 리포트를 보고 등록된 코드베이스에서 관련 코드 후보를 찾는 것이다.
- 정규화 결과는 최종 판단이 아니라 검색 입력이다.
- 원문 검색도 비교 대상에 포함한다.
- 필드명과 enum은 영어를 사용한다.
- 설명은 한글로 작성한다.
- LLM을 검색어 확장 방식으로 고려하되, 결과는 코드베이스 인덱스와 대조해야 한다.
- 리포트 구조화는 LLM으로 수행한다. rule-based 구조화는 사용하지 않는다.

## 핵심 변경점

### candidateDomains는 코드베이스 기반이어야 한다

`candidateDomains`는 LLM이 자유롭게 만들어내는 도메인 목록이면 안 된다.

먼저 등록된 프로젝트의 코드베이스에서 도메인 후보를 만든다.

도메인 후보의 근거:

- Entity 클래스명
- 주요 도메인 패키지명
- Service/Repository 클래스명
- enum 이름
- 프로젝트에서 자주 등장하는 비즈니스 용어

그 다음 리포트의 표현과 코드베이스 도메인 후보를 매칭한다.

예시:

```text
코드베이스에서 발견한 후보:
- Order
- Payment
- Portfolio
- Profit
- Member

리포트:
결제는 됐는데 주문 내역에는 안 떠요.

candidateDomains:
- Payment
- Order
```

이렇게 해야 분석이 실제 코드베이스에 묶인다.

## 초기 검색 입력

초기에는 과하게 많은 필드를 두지 않는다.
하이브리드 검색 품질을 높이는 데 직접 필요한 값만 남긴다.

```json
{
  "reportType": "USER_REPORT",
  "businessTerms": ["결제", "주문 내역"],
  "candidateDomains": ["Payment", "Order"],
  "symptoms": ["NOT_VISIBLE"],
  "codeSearchTerms": ["payment", "order", "history", "status", "webhook"],
  "confidence": "MEDIUM"
}
```

비교 가능한 검색 모드:

```text
RAW_ONLY      : 리포트 원문만 검색
PREPARED_ONLY : 정규화된 검색 입력만 검색
HYBRID        : 원문 + 정규화 입력을 함께 검색
```

## 필드 설명

### reportType

리포트의 작성 관점 또는 문장 성격.

후보 값:

```text
USER_REPORT
OPERATION_REPORT
DEVELOPER_REPORT
UNKNOWN
```

사용처:

- 일반 사용자 표현이면 의미 확장과 RAG 비중을 높인다.
- 개발자 표현이면 symbol search 비중을 높인다.

좋은 예:

```text
결제는 완료됐지만 주문 내역에 주문이 표시되지 않음
```

나쁜 예:

```text
PaymentWebhookHandler에서 OrderStatus 업데이트가 누락됨
```

위 나쁜 예는 코드 검색으로 확인되기 전에는 단정하면 안 된다.

### businessTerms

리포트에 등장한 사용자/비즈니스 언어.

예시:

```text
결제
주문 내역
로그인
알림
수익률
포트폴리오
```

사용처:

- 원문 의미 보존
- 한글 keyword search
- LLM/RAG query 생성

### candidateDomains

코드베이스에서 발견한 도메인 후보 중, 리포트와 관련 있어 보이는 후보.

중요:

- 이 값은 고정 enum만으로 만들지 않는다.
- 프로젝트 코드베이스에서 발견한 도메인 후보와 리포트를 매칭해서 만든다.
- 값은 코드베이스의 실제 명명과 가깝게 유지한다.

예시:

```text
Payment
Order
Portfolio
Profit
Member
Notification
```

사용처:

- symbol search 입력
- file/package search 입력
- agentic expansion 시작점
- 점수화 후보

### symptoms

문제 현상의 유형.

후보 값:

```text
NOT_VISIBLE
NOT_FOUND
STATE_NOT_UPDATED
WRONG_VALUE
DUPLICATED
DELAYED
FAILED
UNAUTHORIZED
TIMEOUT
ERROR
INCONSISTENT
UNKNOWN
```

사용처:

- 검색어 확장
- 점수화
- 벤치마크 분류

### codeSearchTerms

코드 검색에 사용할 키워드 후보.

예시:

```text
payment
order
history
status
webhook
token
cache
profit
portfolio
```

주의:

- 이 값은 실제 코드에 존재한다고 단정하지 않는다.
- 검색 후보일 뿐이다.
- 이후 symbol index, file index, code text search 결과와 대조해야 한다.

### confidence

검색 입력 정규화 결과의 신뢰도.

후보 값:

```text
LOW
MEDIUM
HIGH
```

사용처:

- 신뢰도가 낮으면 검색 범위를 넓힌다.
- 신뢰도가 낮으면 분석 리포트에서 단정 표현을 줄인다.

## 이번 스키마에서 제외한 것

### entryPointHints

제외 이유:

- 지금 단계에서는 과하다.
- Controller/EventHandler/Scheduler 같은 진입점은 코드베이스 검색 이후 판단해도 된다.

### flowHints

제외 이유:

- `Payment -> Order` 같은 흐름은 리포트만 보고 추정하면 위험하다.
- 실제 흐름은 코드 탐색 결과로 확인해야 한다.

### stateHints

제외 이유:

- 상태값 후보는 유용할 수 있지만 초기 스키마에 넣기에는 불확실하다.
- 일단 `codeSearchTerms`에 포함하고, 나중에 enum/state 탐색 기능이 생기면 분리한다.

### testSearchHints

제외 이유:

- 테스트 탐색은 `candidateDomains`와 `codeSearchTerms`를 기반으로 수행해도 된다.
- 초기 스키마에서 별도 필드로 분리하지 않는다.

### rootCauseGuess

제외 이유:

- 원인 추측은 hallucination 위험이 크다.
- 원인은 코드 evidence 수집 이후에만 말해야 한다.

### exactRelatedClasses

제외 이유:

- 관련 클래스는 구조화 단계에서 단정하면 안 된다.
- 실제 symbol search 결과로 확인해야 한다.

## 샘플 1: 일반 사용자 결제/주문 리포트

입력:

```text
결제는 됐는데 주문 내역에는 안 떠요.
```

코드베이스 도메인 후보:

```text
Payment
Order
Member
Portfolio
Profit
```

기대 검색 입력:

```json
{
  "reportType": "USER_REPORT",
  "businessTerms": ["결제", "주문 내역"],
  "candidateDomains": ["Payment", "Order"],
  "symptoms": ["NOT_VISIBLE"],
  "codeSearchTerms": ["payment", "order", "history", "status", "webhook"],
  "confidence": "MEDIUM"
}
```

## 샘플 2: 개발자 리포트

입력:

```text
PaymentWebhookHandler에서 OrderStatus 업데이트가 누락되는 것 같습니다.
```

코드베이스 도메인 후보:

```text
Payment
Order
Member
Notification
```

기대 검색 입력:

```json
{
  "reportType": "DEVELOPER_REPORT",
  "businessTerms": ["결제", "주문 상태"],
  "candidateDomains": ["Payment", "Order"],
  "symptoms": ["STATE_NOT_UPDATED"],
  "codeSearchTerms": ["PaymentWebhookHandler", "OrderStatus", "payment", "webhook", "order", "status"],
  "confidence": "HIGH"
}
```

## 샘플 3: 원인 불명 수익률 리포트

입력:

```text
특정 유저만 포트폴리오 수익률이 이상하게 나와요. 캐시 문제인지 계산 문제인지는 모르겠습니다.
```

코드베이스 도메인 후보:

```text
Portfolio
Profit
Cache
Member
Stock
```

기대 검색 입력:

```json
{
  "reportType": "DEVELOPER_REPORT",
  "businessTerms": ["특정 유저", "포트폴리오", "수익률", "캐시", "계산"],
  "candidateDomains": ["Portfolio", "Profit", "Cache", "Member"],
  "symptoms": ["WRONG_VALUE", "INCONSISTENT"],
  "codeSearchTerms": ["portfolio", "profit", "return", "valuation", "cache", "redis", "member"],
  "confidence": "MEDIUM"
}
```

## 다음 결정 필요

이 문서 기준으로 결정할 것:

1. 초기 검색 입력을 이 6개 필드로 유지할지
2. `candidateDomains`를 코드베이스 도메인 후보와 매칭해서 생성하는 방향을 유지할지
3. `RAW_ONLY`, `PREPARED_ONLY`, `HYBRID` 모드로 검색 품질을 비교할지

결정 후 다음 작업:

```text
코드베이스 도메인 후보 추출
-> 검색 입력 DTO/record 추가
-> LLM 검색어 확장 인터페이스 설계
-> 하이브리드 검색 입력으로 연결
```
