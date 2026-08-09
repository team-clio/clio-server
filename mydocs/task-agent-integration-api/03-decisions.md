# 03. Decisions — Agent Graph 연동 API 구현

## D1. Agent 소유 엔티티 제거 변경의 선행 반영

- 결정일: 2026-08-09
- 상태: 확정·반영 완료
- 선택: **A — `refactor/drop-ai-only-entities` 변경을 이번 브랜치에 반영**

### 검토한 대안

- A. Agent가 소유하는 AI/RAG 데이터의 JPA 엔티티를 Server에서 제거한다.
- B. 엔티티를 남기되 이번 API 구현에서는 사용하지 않는다.

### 결정 근거

- 사용하지 않는 `@Entity`도 Hibernate entity scan과 `ddl-auto=update`의 관리 대상이다.
- Server의 기존 `BugEmbedding`은 `bug_id`와 Java `float[]`를 사용하지만, Agent의 최신
  `bug_embeddings`는 `retrieval_document_id`와 PostgreSQL `vector`를 사용한다.
- Agent Alembic은 기존 JPA형 `bug_embeddings`를 `legacy_bug_embeddings`로 보존한 뒤 새 스키마를
  생성하므로, Server의 오래된 매핑을 남기면 같은 테이블의 소유권과 스키마가 충돌할 수 있다.
- Server는 업무 데이터와 Agent 결과 참조를 소유하고, retrieval·embedding·PCM 상세 데이터는
  Agent가 소유하는 경계가 현재 Agent 구현과 일치한다.

### 반영 범위

- 제거: `BugEmbedding`
- 제거: `CodeChunk`, `CodeChunkType`, `CodeFile`, `CodeSymbol`, `CodeSymbolType`
- 제거: `DecisionMemory`
- 제거: `ProjectContextChunk`

### 영향

- Java 매핑만 제거하며 이 변경 자체가 기존 DB 테이블이나 데이터를 삭제하지는 않는다.
- Server가 해당 데이터를 필요로 하면 Agent 연동 API 또는 명시적인 읽기 계약을 사용해야 한다.
- 다음 결정: D2 Agent 연동 JSON과 ID 계약
