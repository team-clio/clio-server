# 프로젝트 관리 API 구현 계획

## 목표

클라이언트가 프로젝트 식별자를 조회하고 프로젝트를 생성할 수 있도록 최소 공개 API를 구현한다.
작업 범위는 [01-overview.md](01-overview.md)를 따른다.

## 구현 단계

### 1. API 계약 정의

- 프로젝트 생성 요청 DTO를 추가한다.
- 프로젝트 응답 DTO를 추가한다.
- 목록 응답 형식과 정렬 순서를 결정한다.
- 프로젝트 이름 중복 처리 정책을 결정한다.

### 2. 조회·생성 로직 구현

- `ProjectService`에 목록 조회와 생성 로직을 추가한다.
- 엔티티를 API 응답 DTO로 변환한다.
- 결정한 정렬과 중복 정책을 repository 및 service에 반영한다.

### 3. HTTP API 구현

- `ProjectController`를 추가한다.
- `GET /api/v1/projects`를 구현한다.
- `POST /api/v1/projects`를 구현하고 성공 시 `201 Created`를 반환한다.
- Bean Validation을 사용해 프로젝트 이름을 검증한다.

### 4. 검증

- 생성 성공과 목록 조회를 통합 테스트로 검증한다.
- 빈 이름 요청의 공통 `400 Bad Request` 응답을 검증한다.
- 결정한 중복 이름 정책과 정렬 순서를 검증한다.
- 전체 Gradle 테스트를 실행한다.

## 결정 포인트

### D1. 프로젝트 목록 응답 형식

- 대안 A: `ListResponse<ProjectResponse>`로 전체 목록을 반환한다.
  - 현재 관리자 화면의 프로젝트 선택기에 바로 사용하기 쉽다.
  - 프로젝트가 매우 많아지면 별도 검색·페이지네이션 API가 필요하다.
- 대안 B: `PageResponse<ProjectResponse>`로 처음부터 페이지네이션한다.
  - 대규모 목록에 대응하지만 단순 선택기에서 모든 페이지를 합치는 추가 처리가 필요하다.
- 추천: **대안 A**. 현재 API는 프로젝트 선택을 위한 최소 계약이며 기존 `ListResponse`를 재사용할 수 있다.

### D2. 프로젝트 목록 정렬 순서

- 대안 A: 이름 오름차순으로 반환한다.
  - 프로젝트 선택기에서 위치가 안정적이고 찾기 쉽다.
- 대안 B: 최근 생성 순으로 반환한다.
  - 새 프로젝트가 먼저 보이지만 이름 기반 탐색성이 낮다.
- 추천: **대안 A**.

### D3. 프로젝트 이름 중복 정책

- 대안 A: 같은 이름을 허용한다.
  - 데이터 모델 변경이 없지만 UI에서 프로젝트를 구별하기 어렵다.
- 대안 B: 대소문자를 구분하지 않고 같은 이름을 금지한다.
  - 선택기에서 이름이 모호해지는 것을 막으며 DB 제약과 서비스 검증이 필요하다.
- 추천: **대안 B**.

## 예상 변경 위치

- `src/main/java/ax/clio/project/controller/`
- `src/main/java/ax/clio/project/service/`
- `src/main/java/ax/clio/project/dto/`
- `src/main/java/ax/clio/project/repository/ProjectRepository.java`
- `src/main/java/ax/clio/project/entity/Project.java` (D3에서 중복 금지를 선택한 경우)
- `src/test/java/ax/clio/project/controller/`

## 완료 조건

- 결정 포인트가 `03-decisions.md`에 기록되어 있다.
- 프로젝트 생성·목록 조회와 오류 계약이 테스트로 검증된다.
- 전체 테스트가 통과한다.
- 결과와 후속 작업이 `04-result.md`에 기록되어 있다.
