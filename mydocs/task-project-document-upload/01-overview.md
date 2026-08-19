# 프로젝트 문서 업로드 overview

## 목적

프로젝트에 참고 문서를 등록해 Agent PCM(Project Context Memory)에 반영한다.
초기 지원 형식은 Markdown(.md)과 PDF(.pdf)다.

## 현재 상태

- Admin은 프로젝트 기본 정보와 Git 저장소만 관리한다.
- Spring은 프로젝트 문서 업로드·조회·삭제 API를 제공하지 않는다.
- `ProjectContext` 엔티티는 있으나 서비스·저장소·HTTP API에 연결되지 않았다.
- Agent는 `document_added`와 `document_deleted` 요청을 받아 Markdown 문서를 PCM에 반영할 수 있다.

## 개선 방향

Spring이 문서 원본과 메타데이터를 소유하고, 업로드 후 Markdown 본문을 기존 Agent 요청 계약으로 전달한다.
Admin은 프로젝트 설정에서 문서 등록과 목록·삭제를 제공한다.

## 범위

- PDF와 Markdown 업로드
- 문서 메타데이터와 원본 보관 정책 정의
- PDF 텍스트를 Markdown 입력으로 변환
- 업로드·삭제 때 Agent 문서 동기화 요청 발행
- Admin의 문서 등록·목록·삭제 UI

## 범위 밖

- DOCX 등 추가 파일 형식
- 스캔 PDF OCR 및 표·이미지의 구조 보존
- Agent PCM 내부 처리 변경
- 문서 본문 편집 UI

## 확정된 결정

### D1. 최초 지원 형식

- 결정: Markdown(.md), PDF(.pdf)만 허용한다.
- 근거: 사용자가 초기 범위를 지정했다. Agent 입력은 Markdown이므로 두 형식을 같은 처리 경로로 정규화할 수 있다.

## 다음 단계에서 결정할 항목

- 원본 파일 보관 위치와 다운로드 제공 여부
- PDF 텍스트 추출 실패·빈 본문 처리
- 문서 수정·재업로드의 revision 규칙
- 업로드 완료 응답과 Agent 동기화 실패 처리 방식
- 목록·삭제 UI의 정보와 동작 범위
