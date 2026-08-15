-- PCM(Project Context Memory)은 Python 에이전트가 소유하는 별도 데이터베이스다.
-- 비즈니스 DB(clio)와 같은 postgres 인스턴스를 쓰되, 소유권 경계는 DB 단위로 유지한다.
-- PCM 스키마와 vector/pg_trgm 확장은 에이전트 최초 연결 시 자체 migration이 만든다.
CREATE DATABASE clio_pcm;
