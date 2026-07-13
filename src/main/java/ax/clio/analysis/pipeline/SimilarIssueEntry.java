package ax.clio.analysis.pipeline;

/**
 * 분석 결과에 붙는 "유사 과거 이슈" 항목 (roadmap #8). reportId로 과거 리포트를 가리키고, score는 유사도.
 */
public record SimilarIssueEntry(
		Long reportId,
		String title,
		double score
) implements java.io.Serializable {
}
