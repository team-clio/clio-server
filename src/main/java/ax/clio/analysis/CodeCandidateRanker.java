package ax.clio.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ax.clio.code.CodeSearchMatchType;
import ax.clio.code.CodeSearchResult;
import ax.clio.code.CodeSearchService;
import org.springframework.stereotype.Component;

@Component
public class CodeCandidateRanker {

	private static final int MAX_RESULTS = 20;
	private static final int PER_INPUT_LIMIT = 10;

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

	public CodeCandidateRanker(CodeSearchService codeSearchService) {
		this.codeSearchService = codeSearchService;
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
