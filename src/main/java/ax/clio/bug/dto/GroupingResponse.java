package ax.clio.bug.dto;

public record GroupingResponse(
		boolean grouped,
		Long issueId,
		String groupedBy,
		Double confidence
) {
}
