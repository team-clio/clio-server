# Code Memory (RAG 3.1)

## 이 문서의 목적

지금까지 관련 코드 탐색은 **키워드 문자열 매칭**(심볼 이름 / 애노테이션 / 파일 본문 라인)만으로
동작했다. 이 작업은 코드를 **chunk 단위로 저장하고, chunk별 embedding을 만들어,
키워드 검색과 벡터(의미) 검색을 함께** 쓸 수 있게 만든다. roadmap "추천 개발 순서" #7 (3.1 Code Memory).

목표(roadmap 3.1):

> 버그 리포트와 **의미적으로 가까운 코드**를 찾을 수 있어야 한다.

## 배경 / 현재 구현 상태

### 이미 있는 것 (Code Memory가 얹힐 기반)

| 요소 | 위치 | 비고 |
|------|------|------|
| 파일 메타 | `code.CodeFile` | path, fileName, isTest, size, lastModifiedAt |
| 심볼 | `code.CodeSymbol` | name, type, role, packageName, **startLine/endLine**, annotations, imports |
| 스캔 파이프라인 | `code.CodeAnalysisService#scan` | 스캔 시 파일/심볼을 지우고 다시 저장 |
| Java 파서 | `code.JavaCodeIndexer` | JavaParser로 클래스/메서드/필드 심볼 추출 |
| 키워드 검색 | `code.CodeSearchService` | 심볼명/애노테이션/파일본문 부분일치 + 점수 |
| 후보 랭킹 | `analysis.CodeCandidateRanker` | 분석 시 검색어들로 후보를 모아 재점수 |
| 분석 워커 | `analysis.AnalysisWorker#run` | ranker → flow → draft |
| LLM 클라이언트 | `llm.LlmClient` | `completeJson`(chat)만 있음 |
| memory 패키지 | `ax.clio.memory` | **비어 있음(예약됨)** — 여기에 구현 |

핵심: `CodeSymbol`이 이미 `startLine/endLine`을 갖고 있어 **클래스/메서드 chunk 경계로 재사용**할 수 있다.
파일 원문은 `Project.rootPath` 기준으로 다시 읽으면 되므로(현재 `CodeSearchService`가 그렇게 함),
chunk 본문 저장 여부도 선택지가 된다.

### 없는 것 (이번에 만들어야 하는 것)

1. **코드 chunk 저장소** — 클래스/메서드 단위 코드 조각 + 메타(파일, 라인 범위, 심볼, role).
2. **embedding 생성 경로** — `LlmClient`에는 embedding 호출이 없다. embedding API 연동 or 대체 수단이 필요.
3. **벡터 저장/검색** — Postgres(런타임)/H2(테스트) 환경에서 벡터를 어떻게 저장하고 유사도를 계산할지.
4. **하이브리드 검색** — 키워드 점수 + 벡터 유사도를 합쳐 후보를 뽑는 경로.

## 문제점 / 필요

1. 키워드 매칭은 **표현이 다르면 못 찾는다.** ("결제 실패" 리포트인데 코드엔 `settle`, `charge`로 적혀 있으면 놓침.)
2. 후보 랭킹이 결국 키워드 히트에 의존 → 의미적으로 가까운 코드가 상위로 못 올라옴.
3. 이후 단계(Issue/Decision Memory, LLM 요약)도 결국 "의미 기반 검색"을 재사용하므로,
   여기서 **chunk + embedding + 하이브리드 검색의 뼈대**를 제대로 세워야 한다.

## 개선 방향 (뼈대)

### A. 코드 chunk 만들기
스캔 결과(`CodeSymbol`)를 chunk로 변환한다. chunk 하나 = 코드 조각 + (파일, 라인 범위, 심볼 이름/역할).
granularity(클래스 / 메서드 / 둘 다)는 결정 포인트.

### B. chunk embedding
chunk 텍스트를 embedding 벡터로 만든다. embedding 소스(실 LLM embedding API vs 대체 수단)는 결정 포인트.

### C. 벡터 저장 + 유사도 검색
벡터를 저장하고, 쿼리 벡터와의 코사인 유사도로 top-k를 뽑는다.
저장 방식(pgvector vs 앱단 계산)은 결정 포인트.

### D. 하이브리드 검색
기존 키워드 검색 결과와 벡터 검색 결과를 **합쳐서** 후보를 만든다.
분석 파이프라인(`CodeCandidateRanker`/`AnalysisWorker`)과 어떻게 붙일지는 결정 포인트.

## 범위

포함(예정): 코드 chunk 저장, chunk embedding 생성, 벡터 유사도 검색, 키워드+벡터 하이브리드 검색의 최소 동작 경로.

제외: Issue Memory(#8) / Decision Memory(#9), LLM 요약(#10), chunk 재랭킹 정교화(추후 튜닝),
      대규모 성능 최적화, 증분(변경분만) 재임베딩(초기엔 스캔 시 전체 재생성 허용).

## 이 작업에서 사용자가 정할 것 (plan에서 결정 포인트로 상세화)

- **Chunk granularity**: 클래스 단위 / 메서드 단위 / 둘 다.
- **Chunk 본문 저장 여부**: DB에 코드 텍스트를 넣을지, 라인 범위만 저장하고 필요 시 파일에서 다시 읽을지.
- **Embedding 소스**: 실제 embedding API(OpenAI 등) 연동 / 결정적 로컬 대체(해시·토큰 기반) / 우선 인터페이스만 두고 스텁.
- **벡터 저장·검색 방식**: pgvector 확장 / 벡터를 배열·JSON으로 저장 후 앱단 코사인 계산 / (H2 테스트 호환 고려).
- **하이브리드 결합 전략**: 점수 정규화 후 가중합 / rank fusion(RRF 등) / 단순 union 후 재랭킹.
- **분석 파이프라인 연동 지점**: 새 검색 경로를 `CodeCandidateRanker` 입력에 추가할지, 별도 조회 API로만 노출할지.
- **재생성 시점**: 스캔 시 전체 재임베딩 / 별도 트리거.

## 관련 파일

```
src/main/java/ax/clio/memory/                 - (신규) chunk 저장·embedding·하이브리드 검색
src/main/java/ax/clio/code/CodeSymbol.java    - chunk 경계(startLine/endLine) 원천
src/main/java/ax/clio/code/CodeAnalysisService.java - 스캔 파이프라인(임베딩 훅 지점 후보)
src/main/java/ax/clio/code/CodeSearchService.java   - 키워드 검색(하이브리드의 한 축)
src/main/java/ax/clio/analysis/CodeCandidateRanker.java - 후보 랭킹(연동 지점 후보)
src/main/java/ax/clio/llm/LlmClient.java      - embedding 호출 추가 지점 후보
build.gradle                                   - 벡터 저장 방식에 따라 의존성 추가 가능
```

## 주의사항

1. **테스트는 H2로 돈다.** 런타임 Postgres 전용 기능(pgvector 등)을 쓰면 테스트 전략을 함께 정해야 한다(결정 포인트).
2. **embedding API는 외부 비용·의존성**이다. 없이도 파이프라인이 서게 대체 경로를 둘지 결정한다.
3. 기존 키워드 검색/랭킹 동작을 **깨지 않는 것**을 목표로 한다(하이브리드는 추가지 대체가 아님).
4. 워크플로우 규칙 준수: 이 overview 컨펌 → plan(결정 포인트) → 하나씩 결정·커밋 → result.
```
