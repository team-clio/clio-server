# PCM inspect 미가용 오류 처리와 화면 오류 표시

## 1. 작업 배경

PCM inspect 기능이 세 저장소 main에 머지됐지만, standalone inspect 서버(`:2025`)가
꺼져 있으면 clio-admin의 PCM 메모리 화면이 “저장된 지식 문서가 없습니다”라고 표시한다.
실제로는 요청이 실패했으므로 데이터 유무와 오류를 구분할 수 있어야 한다.

## 2. 현재 상태와 문제

### 증상

- inspect 서버가 꺼진 상태에서 Spring 중계 API
  `GET /external-api/v1/pcm/projects/{id}/knowledge`가 500을 반환한다.
- admin 화면은 React Query error를 오류로 표시하지 않고 `documents = []`로 처리해
  “문서 없음” 빈 상태를 보여준다.

### 원인

- clio-server `PcmInspectClient`는 404만 `ResourceNotFoundException`으로 바꾸고,
  5xx·연결 실패는 변환하지 않아 전역 500으로 나간다.
- clio-admin `PcmInspectPage`는 `knowledgeQuery.isError`를 전혀 사용하지 않는다.

## 3. 개선 방향

- clio-server: inspect 서버 미가용을 503 `PCM_INSPECT_UNAVAILABLE`과 명확한 메시지로
  반환한다.
- clio-admin: 요청 실패 시 오류 배너와 재시도 버튼을 보여주고, 오류일 때는 빈 상태
  문구를 숨긴다.
- clio-agent-graph: inspect 서버와 `langgraph dev`를 한 번에 실행하는 편의 스크립트와
  Makefile을 추가해 실행 부담을 줄인다.

## 4. 범위

### 포함

- clio-server: `PcmInspectUnavailableException` + `GlobalExceptionHandler` 503 매핑
- clio-server: `PcmInspectClient` 5xx·연결 실패 변환 + 테스트
- clio-admin: PCM 페이지 오류 배너·재시도·빈 상태 구분
- clio-agent-graph: 실행 편의(Makefile + dev script + README)

### 제외

- PCM 조회 성공 경로 변경
- 프로젝트 4번처럼 실제로 Knowledge가 없는 프로젝트의 데이터 생성
- inspect 서버 자동 데몬화(현재는 로컬 개발 편의만)

## 5. 완료 기준 초안

- inspect 서버가 꺼져 있을 때 Spring 중계 API가 503과 명확한 코드·메시지를 반환한다.
- admin PCM 화면이 오류를 배너로 보여주고 “문서 없음”과 구분하며 재시도할 수 있다.
- clio-agent-graph에서 한 명령으로 inspect 서버와 `langgraph dev`를 함께 실행할 수
  있다.
- 관련 테스트·lint·build가 통과한다.

## 6. Plan에서 결정할 핵심 항목

1. 서버 오류 응답 형태(503 + `PCM_INSPECT_UNAVAILABLE` 추천)
2. admin 오류 UI 형태(배너 + 재시도 추천)
3. clio-agent-graph 편의 실행 방식(Makefile + dev script 추천)
4. 검증 범위(각 저장소 전체 검증 추천)
