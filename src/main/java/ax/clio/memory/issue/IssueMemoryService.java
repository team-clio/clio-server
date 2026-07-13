package ax.clio.memory.issue;

import ax.clio.memory.embedding.EmbeddingClient;

import java.util.List;

import ax.clio.report.entity.BugReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issue Memory 서비스 (roadmap #8). 분석된 리포트를 임베딩해 기억하고(remember), 새 리포트에 유사한
 * 과거 이슈를 찾는다(findSimilar).
 *
 * <p>I1: 임베딩 텍스트는 title+description만(쿼리와 대칭). I2: remember는 분석 완료 시 호출.
 * I6: 검색만 — outcome/학습 루프는 이번 범위 밖.
 */
@Service
public class IssueMemoryService {

	private final EmbeddingClient embeddingClient;
	private final IssueEmbeddingRepository issueEmbeddingRepository;
	private final IssueVectorSearch issueVectorSearch;

	public IssueMemoryService(EmbeddingClient embeddingClient, IssueEmbeddingRepository issueEmbeddingRepository,
			IssueVectorSearch issueVectorSearch) {
		this.embeddingClient = embeddingClient;
		this.issueEmbeddingRepository = issueEmbeddingRepository;
		this.issueVectorSearch = issueVectorSearch;
	}

	/** 분석 완료 시 리포트를 임베딩해 저장한다(재분석 시 갱신). */
	@Transactional
	public void remember(BugReport report) {
		issueEmbeddingRepository.deleteByReportId(report.getId());
		float[] embedding = embeddingClient.embed(embeddingText(report));
		issueEmbeddingRepository.save(
				new IssueEmbedding(report.getProject(), report, report.getTitle(), embedding));
	}

	/** 새 리포트와 유사한 과거 이슈 top-k (자기 자신 제외). */
	@Transactional(readOnly = true)
	public List<ScoredIssue> findSimilar(BugReport report, int topK) {
		if (topK <= 0) {
			return List.of();
		}
		float[] queryVector = embeddingClient.embed(embeddingText(report));
		return issueVectorSearch.searchByVector(report.getProject().getId(), queryVector, topK + 1).stream()
				.filter(scored -> !scored.issue().getReport().getId().equals(report.getId()))
				.limit(topK)
				.toList();
	}

	/** I1: 저장·쿼리 임베딩 텍스트를 동일 규칙으로 — 대칭 유지. */
	public static String embeddingText(BugReport report) {
		String title = report.getTitle() != null ? report.getTitle() : "";
		String description = report.getDescription() != null ? report.getDescription() : "";
		return (title + "\n" + description).strip();
	}
}
