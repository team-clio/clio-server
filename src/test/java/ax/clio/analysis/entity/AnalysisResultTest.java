package ax.clio.analysis.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createsImmutableIssueAnalysisSnapshot() {
		AnalysisJob job = new AnalysisJob();
		ObjectNode snapshot = objectMapper.createObjectNode()
				.put("analysis_job_id", 501)
				.put("status", "COMPLETED");

		AnalysisResult result = AnalysisResult.create(
				job,
				AnalysisResultStatus.COMPLETED,
				null,
				snapshot
		);
		snapshot.put("status", "CHANGED_AFTER_CREATION");
		((ObjectNode)result.getResultSnapshot()).put("status", "CHANGED_THROUGH_GETTER");

		assertThat(result.getJob()).isSameAs(job);
		assertThat(result.getStatus()).isEqualTo(AnalysisResultStatus.COMPLETED);
		assertThat(result.getPreviousAnalysisJob()).isNull();
		assertThat(result.getResultSnapshot().path("status").asText()).isEqualTo("COMPLETED");
	}
}
