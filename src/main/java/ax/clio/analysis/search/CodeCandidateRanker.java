package ax.clio.analysis.search;

import ax.clio.analysis.pipeline.RankedCodeCandidate;
import ax.clio.analysis.pipeline.ReportSearchInput;
import ax.clio.analysis.pipeline.ReportSearchInputType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ax.clio.code.entity.CodeSearchMatchType;
import ax.clio.code.entity.CodeSearchResult;
import ax.clio.code.service.CodeSearchService;
import ax.clio.code.entity.CodeSymbolType;
import ax.clio.memory.code.CodeChunk;
import ax.clio.memory.code.CodeChunkType;
import ax.clio.memory.code.CodeMemorySearchService;
import ax.clio.memory.code.ScoredCodeChunk;
import org.springframework.stereotype.Component;

@Component
public class CodeCandidateRanker {

	private static final int MAX_RESULTS = 20;
	private static final int PER_INPUT_LIMIT = 10;

	// D5/D6: semantic fallback — 키워드 후보가 이 수 미만이면만 semanticSearch로 보강("필요할 때만 호출").
	// 임계값은 상수, 튜닝은 backlog.
	static final int SEMANTIC_FALLBACK_MIN_CANDIDATES = 3;
	static final int SEMANTIC_LIMIT = 10;
	// 코사인 유사도(0~1)를 키워드 점수 스케일(40~100)에 맞추기 위한 배율.
	static final int SEMANTIC_SCORE_SCALE = 90;

	// D3: 곱연산 가중치 (기본값, 나중에 튜닝)
	static final double DOMAIN_CLASS_MULTIPLIER = 1.15;
	static final double DOMAIN_OTHER_MULTIPLIER = 1.05;
	static final double RAW_REPORT_MULTIPLIER = 1.10;
	static final double KEYWORD_MULTIPLIER = 1.0;

	// D4/D6: hitCount 가산점 (기본값, 나중에 튜닝)
	static final int HIT_COUNT_BONUS = 10;
	static final int HIT_COUNT_BONUS_CAP = 30;

	// D8: 파일 내 매칭 수 가산점
	static final int FILE_MATCH_THRESHOLD = 3;
	static final int FILE_MATCH_BONUS = 5;

	private final CodeSearchService codeSearchService;
	private final CodeMemorySearchService codeMemorySearchService;

	public CodeCandidateRanker(CodeSearchService codeSearchService,
			CodeMemorySearchService codeMemorySearchService) {
		this.codeSearchService = codeSearchService;
		this.codeMemorySearchService = codeMemorySearchService;
	}

	public List<RankedCodeCandidate> rank(Long projectId, List<ReportSearchInput> searchInputs) {
		// 1단계: 검색 실행, 심볼 단위로 결과 누적 (D5: 심볼 단위)
		Map<String, SymbolAccumulator> symbolAccumulators = new HashMap<>();
		// 파일 단위 매칭 수 추적 (D8)
		Map<String, Integer> fileMatchCounts = new HashMap<>();

		for (ReportSearchInput input : searchInputs) {
			List<CodeSearchResult> results = codeSearchService.search(projectId, input.query(), PER_INPUT_LIMIT);
			for (CodeSearchResult result : results) {
				String symbolKey = symbolKeyOf(result);
				SymbolAccumulator acc = symbolAccumulators.computeIfAbsent(symbolKey, k -> new SymbolAccumulator());
				acc.add(result, input.type(), input.query());

				fileMatchCounts.merge(result.filePath(), 1, Integer::sum);
			}
		}

		// 1.5단계 (D5/D6): 키워드 후보가 빈약할 때만 semantic 후보로 보강("필요할 때만 호출").
		if (symbolAccumulators.size() < SEMANTIC_FALLBACK_MIN_CANDIDATES) {
			semanticFallback(projectId, searchInputs, symbolAccumulators, fileMatchCounts);
		}

		// 2단계: 파일 단위 그룹화, 파일별 대표 1개 선택 (D7)
		Map<String, RankedCodeCandidate> fileBest = new HashMap<>();

		for (SymbolAccumulator acc : symbolAccumulators.values()) {
			RankedCodeCandidate candidate = acc.toCandidate(fileMatchCounts);
			String filePath = candidate.filePath();

			RankedCodeCandidate existing = fileBest.get(filePath);
			if (existing == null || candidate.adjustedScore() > existing.adjustedScore()) {
				fileBest.put(filePath, candidate);
			}
		}

		// 3단계: 정렬 및 제한
		return fileBest.values().stream()
				.sorted(Comparator.comparingInt(RankedCodeCandidate::adjustedScore).reversed()
						.thenComparing(RankedCodeCandidate::filePath))
				.limit(MAX_RESULTS)
				.toList();
	}

	private void semanticFallback(Long projectId, List<ReportSearchInput> searchInputs,
			Map<String, SymbolAccumulator> symbolAccumulators, Map<String, Integer> fileMatchCounts) {
		String query = semanticQuery(searchInputs);
		if (query.isBlank()) {
			return;
		}
		List<ScoredCodeChunk> scoredChunks = codeMemorySearchService.semanticSearch(projectId, query, SEMANTIC_LIMIT);
		for (ScoredCodeChunk scored : scoredChunks) {
			CodeSearchResult result = toSearchResult(scored);
			String symbolKey = symbolKeyOf(result);
			// 이미 키워드로 잡힌 심볼이면 semantic이 덮어쓰지 않게 keyword 우선(신규만 추가).
			if (symbolAccumulators.containsKey(symbolKey)) {
				continue;
			}
			SymbolAccumulator acc = symbolAccumulators.computeIfAbsent(symbolKey, key -> new SymbolAccumulator());
			acc.add(result, ReportSearchInputType.KEYWORD, query);
			fileMatchCounts.merge(result.filePath(), 1, Integer::sum);
		}
	}

	/** semantic 쿼리는 자연어 리포트(RAW_REPORT) 우선, 없으면 모든 입력을 이어붙인다. */
	private static String semanticQuery(List<ReportSearchInput> searchInputs) {
		return searchInputs.stream()
				.filter(input -> input.type() == ReportSearchInputType.RAW_REPORT)
				.map(ReportSearchInput::query)
				.findFirst()
				.orElseGet(() -> searchInputs.stream()
						.map(ReportSearchInput::query)
						.reduce((a, b) -> a + " " + b)
						.orElse(""));
	}

	private static CodeSearchResult toSearchResult(ScoredCodeChunk scored) {
		CodeChunk chunk = scored.chunk();
		int baseScore = Math.max(0, Math.min(SEMANTIC_SCORE_SCALE,
				(int) Math.round(scored.score() * SEMANTIC_SCORE_SCALE)));
		return new CodeSearchResult(
				chunk.getFile().getId(),
				chunk.getPath(),
				chunk.getSymbolName(),
				mapChunkType(chunk.getChunkType()),
				chunk.getRole(),
				CodeSearchMatchType.CODE_TEXT,
				chunk.getStartLine(),
				chunk.getContent(),
				baseScore,
				chunk.getFile().isTest()
		);
	}

	private static CodeSymbolType mapChunkType(CodeChunkType chunkType) {
		return chunkType == CodeChunkType.METHOD ? CodeSymbolType.METHOD : CodeSymbolType.CLASS;
	}

	private static String symbolKeyOf(CodeSearchResult result) {
		// 심볼 단위: 같은 파일 + 같은 심볼이름 + 같은 타입
		String name = result.symbolName() != null ? result.symbolName() : "line:" + result.lineNumber();
		String type = result.symbolType() != null ? result.symbolType().name() : "TEXT";
		return result.filePath() + "#" + name + "#" + type;
	}

	private static class SymbolAccumulator {

		private final List<CodeSearchResult> results = new ArrayList<>();
		private final Set<ReportSearchInputType> inputTypes = new LinkedHashSet<>();
		private final Set<String> matchedQueries = new LinkedHashSet<>();

		void add(CodeSearchResult result, ReportSearchInputType inputType, String query) {
			results.add(result);
			inputTypes.add(inputType);
			matchedQueries.add(query);
		}

		RankedCodeCandidate toCandidate(Map<String, Integer> fileMatchCounts) {
			CodeSearchResult best = results.stream()
					.max(Comparator.comparingInt(CodeSearchResult::score))
					.orElseThrow();

			int hitCount = matchedQueries.size();
			int baseScore = best.score();

			// D3: 곱연산 가중치
			double multiplier = calculateMultiplier(best.matchType(), inputTypes);
			int multipliedScore = (int) Math.round(baseScore * multiplier);

			// D4/D6: hitCount 가산점
			int hitBonus = Math.min((hitCount - 1) * HIT_COUNT_BONUS, HIT_COUNT_BONUS_CAP);

			// D8: 파일 내 매칭 수 가산점
			int fileMatches = fileMatchCounts.getOrDefault(best.filePath(), 0);
			int fileMatchBonus = fileMatches >= FILE_MATCH_THRESHOLD ? FILE_MATCH_BONUS : 0;

			int adjustedScore = multipliedScore + hitBonus + fileMatchBonus;

			return new RankedCodeCandidate(
					best.fileId(),
					best.filePath(),
					best.symbolName(),
					best.symbolType(),
					best.symbolRole(),
					best.matchType(),
					best.lineNumber(),
					best.snippet(),
					best.test(),
					baseScore,
					adjustedScore,
					hitCount,
					List.copyOf(inputTypes)
			);
		}

		private static double calculateMultiplier(CodeSearchMatchType matchType, Set<ReportSearchInputType> inputTypes) {
			double multiplier = KEYWORD_MULTIPLIER;

			if (inputTypes.contains(ReportSearchInputType.CANDIDATE_DOMAIN)) {
				multiplier = Math.max(multiplier,
						matchType == CodeSearchMatchType.CLASS_NAME ? DOMAIN_CLASS_MULTIPLIER : DOMAIN_OTHER_MULTIPLIER);
			}
			if (inputTypes.contains(ReportSearchInputType.RAW_REPORT)) {
				multiplier = Math.max(multiplier, RAW_REPORT_MULTIPLIER);
			}

			return multiplier;
		}
	}
}
