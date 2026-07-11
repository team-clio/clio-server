-- code-memory(#7): pgvector 확장. 런타임 semantic 검색이 embedding::vector <=> ... 캐스팅을 쓰므로 필요.
-- postgres 이미지의 docker-entrypoint-initdb.d에서 최초 초기화 시 1회 실행된다.
CREATE EXTENSION IF NOT EXISTS vector;
