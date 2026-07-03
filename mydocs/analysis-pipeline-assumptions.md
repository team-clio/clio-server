# 버그 리포트 분석 파이프라인 전제

## 1. 기본 전제

버그 리포트는 항상 개발자가 작성한다고 가정하지 않는다.

실제 입력은 다음처럼 들어올 수 있다.

- 일반 사용자가 현상을 설명한 리포트
- CS/운영자가 사용자 문의를 옮긴 리포트
- 개발자가 원인을 모르는 상태에서 작성한 리포트
- 개발자가 특정 코드나 모듈을 지목한 리포트

따라서 리포트 원문을 그대로 코드 검색에 사용하는 방식은 부족하다.

예시:

```text
결제는 됐는데 주문 내역에는 안 떠요.
로그인은 되는데 다시 첫 화면으로 돌아가요.
알림을 껐는데도 계속 와요.
어제 배포 이후 갑자기 느려졌어요.
```

이런 문장에는 코드에 존재하는 클래스명, 메서드명, 상태값이 직접 등장하지 않을 수 있다.

반대로 개발자 리포트는 다음처럼 들어올 수도 있다.

```text
PaymentWebhookHandler에서 OrderStatus 업데이트가 누락되는 것 같습니다.
JWT refresh 이후 SecurityContext가 유지되지 않는 것 같습니다.
Portfolio 수익률 계산이 Redis 캐시와 불일치합니다.
```

이 경우에는 의미 검색보다 정확한 symbol search가 더 중요하다.

결론:

```text
Clio는 하나의 검색 방식에 의존하지 않는다.
일반 사용자 언어와 개발자 언어를 모두 처리하기 위해 하이브리드 분석 파이프라인을 사용한다.
```

## 2. RAG와 Agentic Search 역할 분리

코드 분석에서 RAG와 Agentic Search는 같은 역할이 아니다.

### 2.1 Agentic Search가 적합한 영역

Agentic Search는 코드 구조를 따라가야 하는 작업에 적합하다.

적용 영역:

- 파일명 검색
- 클래스명 검색
- 메서드명 검색
- 어노테이션 검색
- import/package 기반 탐색
- Controller -> Service -> Repository 흐름 추적
- 관련 Entity 탐색
- 관련 테스트 코드 탐색
- 후보 코드 주변으로 영향 범위 확장

이유:

코드는 자연어 문서보다 구조가 명확하다. 클래스명, 메서드명, 패키지, import, 호출 관계 같은 구조 정보는 벡터 검색보다 정적 분석과 명시적 탐색이 더 정확하다.

### 2.2 RAG가 적합한 영역

RAG는 의미적 연결과 장기 기억 검색에 적합하다.

적용 영역:

- 일반 사용자 언어와 코드 용어 사이의 의미 연결
- 이름이 직접 일치하지 않는 관련 코드 후보 검색
- 과거 유사 버그 리포트 검색
- 과거 분석 결과 검색
- 설계 결정 메모 검색
- 운영/장애 대응 메모 검색

예시:

```text
사용자 표현:
돈은 빠져나갔는데 주문이 안 보여요.

코드 표현:
PaymentApprovedEvent
OrderStatus.PAID
PaymentWebhookHandler
OrderHistoryService
```

이 경우 keyword search만으로는 실패할 수 있으므로 의미 기반 검색이 필요하다.

### 2.3 결론

Clio는 다음 구조를 목표로 한다.

```text
코드 구조 탐색        -> Agentic Search 중심
정확한 파일/심볼 검색 -> Keyword / Symbol Search 중심
의미 기반 후보 검색   -> RAG 보조
과거 이슈/결정 기억   -> RAG 중심
분석 절차 조립        -> Agentic Workflow
최종 설명 생성        -> LLM
```

## 3. 버그 리포트 분석 파이프라인

전체 흐름은 다음과 같다.

```text
BugReport
-> Report Structuring
-> Multi Search
-> Agentic Expansion
-> Evidence Merge
-> Hybrid Rerank
-> Rule-based Scoring
-> Report Generation
-> Grounding Check
```

## 4. Report Structuring

리포트 원문은 바로 검색하지 않고 먼저 구조화한다.

목표:

- 일반 사용자 언어를 코드 검색 가능한 형태로 바꾼다.
- 개발자 리포트에 포함된 기술 단서를 보존한다.
- 검색 query를 여러 종류로 확장한다.

추출할 정보:

- 사용자 행동
- 기대 결과
- 실제 결과
- 증상
- 도메인 후보
- 비즈니스 키워드
- 기술 키워드
- 상태값 후보
- 에러 메시지 후보
- 재현 조건
- 심각도 힌트

예시:

```text
입력:
결제는 됐는데 주문 내역에는 안 떠요.

구조화 결과:
- 도메인 후보: 결제, 주문
- 사용자 행동: 결제 완료
- 기대 결과: 주문 내역 표시
- 실제 결과: 주문 내역 미표시
- 비즈니스 키워드: 결제, 주문, 주문 내역
- 기술 키워드 후보: payment, order, history, status
- 검색 후보: Payment, Order, OrderHistory, PaymentWebhook
```

초기 구현:

- rule-based
- 도메인 사전 기반
- 키워드 매핑 기반

고도화:

- LLM 기반 구조화
- strict JSON schema 사용
- 실패 시 rule-based fallback

## 5. Multi Search

하나의 검색 방식만 사용하지 않는다.

동시에 또는 단계적으로 여러 검색을 수행한다.

### 5.1 Keyword Search

대상:

- 리포트 원문 키워드
- 구조화된 키워드
- 도메인 사전 키워드
- 상태값
- 에러 메시지

역할:

- 빠른 baseline 검색
- 정확한 문자열 매칭
- 코드 본문, 로그 메시지, enum 값 탐색

### 5.2 Symbol Search

대상:

- 클래스명
- 메서드명
- 필드명
- 어노테이션
- 패키지명
- import

역할:

- 코드 구조 기반 검색
- 개발자 리포트에 강함
- 정확한 관련 코드 후보 확보

### 5.3 Vector Search

대상:

- 코드 chunk
- 과거 이슈
- 설계 결정 메모

역할:

- 리포트 표현과 코드 표현이 다를 때 보완
- 유사한 의미의 코드 후보 검색
- 과거 유사 사례 검색

### 5.4 Code Text Search

대상:

- Java 파일 본문
- 로그 메시지
- 예외 메시지
- enum 값
- SQL 문자열
- 주석

역할:

- symbol로 잡히지 않는 단서 탐색
- 실제 라인 번호와 스니펫 제공

## 6. Agentic Expansion

검색으로 찾은 후보 코드 주변을 구조적으로 확장한다.

목표:

- 단순히 매칭된 코드만 보는 것이 아니라 실제 영향 범위를 추정한다.

확장 대상:

- Controller
- Service
- Repository
- Entity
- Event Handler
- Scheduler
- 외부 API Client
- 관련 테스트 코드

예시:

```text
PaymentWebhookHandler 발견
-> PaymentService 탐색
-> OrderService 호출 여부 확인
-> OrderRepository 탐색
-> OrderStatus 탐색
-> 관련 테스트 탐색
```

초기 구현:

- 클래스명 규칙
- 패키지 규칙
- import 기반 탐색
- 생성자 주입/필드 주입 기반 탐색

고도화:

- JavaParser 기반 method call 추적
- 간단한 call graph 구축
- 테스트 커버리지 정보 반영

## 7. Evidence Merge

검색과 확장 결과를 하나의 evidence 목록으로 합친다.

Evidence는 다음 출처를 가질 수 있다.

- keyword
- symbol
- code_text
- vector
- issue_memory
- decision_memory
- agentic_flow
- related_test

각 evidence는 최소한 다음 정보를 가져야 한다.

- 출처
- 파일 경로
- 라인 번호
- 스니펫
- 매칭 이유
- 점수
- 테스트 코드 여부
- 코드 역할

이 단계에서는 아직 최종 판단을 하지 않는다.

목표:

- LLM이나 점수화 단계가 사용할 근거 후보를 모은다.

## 8. Hybrid Rerank

여러 출처에서 모인 evidence를 다시 정렬한다.

점수에 반영할 요소:

- symbol exact match
- keyword match
- vector similarity
- 도메인 일치
- 코드 역할
- 호출 흐름 포함 여부
- 테스트 코드 여부
- Entity/Repository 관련 여부
- 과거 이슈 유사도
- 설계 결정 관련성

목표:

- 최종 분석에 사용할 관련 코드 후보를 고른다.
- LLM context에 넣을 근거를 제한한다.
- 노이즈를 줄인다.

## 9. Rule-based Scoring

중요도, 난이도, 위험도 점수는 LLM이 직접 결정하지 않는다.

점수는 deterministic rule로 계산한다.

LLM은 점수를 설명하는 역할로 제한한다.

### 9.1 중요도

반영 요소:

- 이슈 타입
- 사용자 영향도
- 핵심 도메인 여부
- 장애성 키워드
- 과거 유사 이슈 빈도

핵심 도메인 예시:

- 결제
- 인증
- 주문
- 자산
- 수익률

### 9.2 난이도

반영 요소:

- 관련 파일 수
- 관련 도메인 수
- 영향 흐름 깊이
- 관련 테스트 존재 여부
- 외부 API 관련 여부
- 비동기/Event 처리 관련 여부
- 캐시/DB 동시 변경 가능성

### 9.3 위험도

반영 요소:

- Entity 변경 가능성
- Repository 변경 가능성
- 트랜잭션 관련 여부
- 공통 모듈 관련 여부
- 테스트 부족 여부
- 호출 관계 깊이
- 핵심 도메인 여부

## 10. Report Generation

분석 리포트는 근거 기반으로 생성한다.

생성할 내용:

- 요약
- 관련 코드 목록
- 중요도/난이도/위험도 점수
- 점수 근거
- 추천 수정 방향
- 추천 테스트 전략
- 근거가 부족한 항목

초기 구현:

- rule-based 텍스트 생성

고도화:

- LLM 기반 설명 생성
- 검색된 evidence만 context로 제공
- structured output 사용

## 11. Grounding Check

LLM을 사용하는 경우 반드시 근거 검증을 수행한다.

검증 규칙:

- 검색 결과에 없는 파일명은 언급하지 않는다.
- 검색 결과에 없는 클래스명은 언급하지 않는다.
- 근거 코드 없이 단정하지 않는다.
- 근거가 약하면 “추정” 또는 “근거 부족”으로 표시한다.

목표:

- hallucination 방지
- 분석 결과 신뢰도 확보

## 12. 벤치마크 전제

이 프로젝트는 RAG/LLM 적용 후 벤치마크를 수행한다.

비교할 방식:

```text
keyword only
structured query + keyword
symbol search
vector search
hybrid search
hybrid + agentic expansion
hybrid + memory
hybrid + LLM report
```

측정할 항목:

- 정답 파일이 Top-K 안에 있는가
- 정답 메서드가 Top-K 안에 있는가
- 관련 없는 코드가 얼마나 섞이는가
- 설명이 근거 코드와 일치하는가
- hallucination이 있는가
- 점수 판단이 납득 가능한가
- 응답 시간이 어느 정도인가

## 13. 최종 방향

Clio는 단순 RAG 서비스가 아니라 다음 구조를 목표로 한다.

```text
Agentic Code Analysis
+ RAG Memory
+ Hybrid Retrieval
+ Rule-based Scoring
+ Grounded LLM Report
```

한 줄 요약:

```text
코드 자체는 agentic search로 찾고,
의미와 과거 기억은 RAG로 보강하고,
최종 설명은 LLM이 근거 기반으로 생성한다.
```
