# 프로젝트 문서 업로드 결과

## 완료 범위

- PDF(.pdf)와 Markdown(.md/.markdown) 업로드 API
- 원본 바이트 SHA-256 해시로 프로젝트별 동일 파일 중복 업로드 거절
- PDFBox 텍스트 추출과 빈·읽기 실패 PDF의 422 응답
- 문서 생성·목록·삭제 API
- Agent `document_added`/`document_deleted` PCM 동기화
- `PENDING`, `SYNCING`, `SYNCED`, `FAILED`, `DELETING` 상태 추적
- 프로젝트 설정의 업로드·목록·상태·삭제 UI

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/projects/{projectId}/documents` | 문서 목록 |
| POST | `/api/v1/projects/{projectId}/documents` | `multipart/form-data`의 `title`, `file`로 문서 등록 |
| DELETE | `/api/v1/projects/{projectId}/documents/{documentId}` | PCM 삭제가 끝난 뒤 문서 제거 |

지원 파일 이외, 빈 Markdown, 텍스트를 뽑지 못한 PDF는 `422 UNPROCESSABLE_CONTENT`다.
같은 프로젝트에 원본 바이트가 같은 파일을 올리면 `409 CONFLICT`다.

## 검증

- `./gradlew test bootJar` 통과
- PDFBox가 생성한 PDF에서 실제 텍스트를 추출하는 단위 테스트 통과
- 문서 생성·목록·삭제, 해시 중복 거절 Controller 테스트 통과
- `npm run typecheck` 통과
- `npm run lint` 통과

## 남은 과제

- Agent가 비활성화된 환경에서 새 문서는 `PENDING`으로 남으며 PCM에 반영되지 않는다.
- 스캔 이미지 PDF OCR, 원본 파일 다운로드, 문서 수정·재업로드는 범위 밖이다.
- 현재 Agent가 정한 분류는 PCM 내부에만 저장하며 Spring 문서 목록에는 노출하지 않는다.
