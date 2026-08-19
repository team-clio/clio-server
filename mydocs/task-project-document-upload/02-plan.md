# 프로젝트 문서 업로드 계획

## 구현 순서

1. D2~D5를 확정해 문서 저장·변환·동기화 계약을 정한다.
2. Spring 문서 도메인(entity/repository/service/dto/controller)을 구현하고 API 테스트를 작성한다.
3. PDF 텍스트 추출과 업로드 검증을 구현한다.
4. 업로드·삭제 후 Agent `document_added`/`document_deleted`를 AFTER_COMMIT으로 발행한다.
5. Admin API 클라이언트·hooks와 프로젝트 설정의 문서 관리 UI를 구현한다.
6. server 테스트와 Admin typecheck·lint를 실행하고 결과 문서를 작성한다.

## 결정 포인트

### D2. 원본 파일 보관과 다운로드

- A. DB에 원본 바이너리 저장, 다운로드 API 제공
- B. 서버 파일시스템에 저장, 경로만 DB에 저장, 다운로드 API 제공
- C. 정규화 Markdown과 메타데이터만 DB에 저장, 원본 다운로드 미제공
- 추천: C. Agent의 입력·검색 책임에 필요한 것은 Markdown이며, 별도 파일 저장소·백업·접근제어 정책을 도입하지 않아도 된다.

### D3. PDF 텍스트 추출 정책

- A. Apache PDFBox로 텍스트를 추출하고, 빈 텍스트·추출 오류는 업로드를 422로 거절
- B. 추출 실패도 문서를 저장하고 Agent 동기화만 건너뜀
- C. OCR까지 포함해 스캔 PDF를 지원
- 추천: A. 지원 여부를 명확히 하고, 손상되거나 이미지뿐인 PDF가 빈 지식으로 남는 일을 막는다.

### D4. 문서 revision·수정 모델

- A. 업로드는 새 문서만 생성하고 수정은 지원하지 않는다. 삭제 후 재업로드한다.
- B. 같은 문서에 파일을 재업로드해 revision을 증가시킨다.
- C. 제목·분류·본문을 UI에서 직접 수정하고 revision을 증가시킨다.
- 추천: A. 초기 UI와 API를 단순하게 유지한다. 문서 갱신은 후속 기능으로 분리한다.

### D5. Agent 동기화 실패 처리

- A. Spring 문서는 저장하고, AFTER_COMMIT 비동기 요청 실패를 서버 로그만 남긴다.
- B. 문서에 동기화 상태(PENDING/SYNCING/SYNCED/FAILED)를 저장하고 Admin에 표시한다.
- C. Agent 요청이 성공해야 업로드 트랜잭션도 성공으로 처리한다.
- 추천: B. Agent는 외부 비동기 작업이므로 업로드 원본과 동기화 결과를 분리하고, 사용자가 재시도 필요 여부를 알 수 있다.

### D6. Admin 화면 범위

- A. 프로젝트 설정에 업로드, 목록, 삭제와 동기화 상태를 모두 제공
- B. 업로드와 목록만 제공하고 삭제는 API만 제공
- C. 별도 프로젝트 문서 페이지를 만든다.
- 추천: A. 기존 프로젝트 설정의 저장소 관리와 맥락 문서 관리가 함께 있어 발견 가능성이 높다.

### D7. 문서 분류 입력

- A. 기존 `ProjectContextType`(요구사항/설계/아키텍처/정책/기타)을 사용자가 선택
- B. 항상 `ETC`로 저장하고 분류 UI는 제공하지 않는다.
- C. Agent가 분류한다.
- 추천: A. 이미 정의된 도메인 enum을 활용하고, PCM source metadata의 검색 맥락을 보존한다.

## 예상 API

- `GET /api/v1/projects/{projectId}/documents`
- `POST /api/v1/projects/{projectId}/documents` (`multipart/form-data`: title, type, file)
- `DELETE /api/v1/projects/{projectId}/documents/{documentId}`

구체적인 요청·응답 필드와 HTTP 오류 계약은 D2~D7 확정 후 구현 단계에서 테스트로 고정한다.
