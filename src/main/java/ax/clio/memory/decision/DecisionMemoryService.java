package ax.clio.memory.decision;

import ax.clio.memory.embedding.EmbeddingClient;
import ax.clio.memory.issue.IssueMemoryService;

import java.util.List;

import ax.clio.project.entity.Project;
import ax.clio.project.service.ProjectService;
import ax.clio.report.entity.BugReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision Memory 서비스 (roadmap #9). 사람이 설계 결정 메모를 등록하면(register) 임베딩해 저장하고,
 * 새 리포트를 분석할 때 관련 결정을 찾는다(findRelevant).
 *
 * <p>D3: 결정 임베딩 텍스트는 title+body. D4: 쿼리(리포트)는 #8과 동일 title+description
 * ({@link IssueMemoryService#embeddingText}을 재사용) — 결정↔리포트라 의도적 비대칭. D7: 충돌은 자동
 * 판정하지 않고 관련 결정을 검색해 분석 결과에 표시만 한다(자동판정은 #11).
 */
@Service
public class DecisionMemoryService {

	private final EmbeddingClient embeddingClient;
	private final DecisionMemoryRepository decisionMemoryRepository;
	private final DecisionVectorSearch decisionVectorSearch;
	private final ProjectService projectService;

	public DecisionMemoryService(EmbeddingClient embeddingClient, DecisionMemoryRepository decisionMemoryRepository,
			DecisionVectorSearch decisionVectorSearch, ProjectService projectService) {
		this.embeddingClient = embeddingClient;
		this.decisionMemoryRepository = decisionMemoryRepository;
		this.decisionVectorSearch = decisionVectorSearch;
		this.projectService = projectService;
	}

	/** 결정 메모를 등록하고 임베딩해 저장한다(D2 생성). */
	@Transactional
	public DecisionMemory register(Long projectId, String title, String body) {
		Project project = projectService.getProject(projectId);
		float[] embedding = embeddingClient.embed(embeddingText(title, body));
		return decisionMemoryRepository.save(new DecisionMemory(project, title, body, embedding));
	}

	/** 프로젝트의 결정 메모 목록(최신순, D2 조회). */
	@Transactional(readOnly = true)
	public List<DecisionMemory> findByProject(Long projectId) {
		projectService.getProject(projectId);
		return decisionMemoryRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
	}

	/** 새 리포트와 관련 있는 설계 결정 top-k (프로젝트 스코프). 결정이 0건이면 빈 리스트. */
	@Transactional(readOnly = true)
	public List<ScoredDecision> findRelevant(BugReport report, int topK) {
		if (topK <= 0) {
			return List.of();
		}
		float[] queryVector = embeddingClient.embed(IssueMemoryService.embeddingText(report));
		return decisionVectorSearch.searchByVector(report.getProject().getId(), queryVector, topK);
	}

	/** D3: 결정 저장 임베딩 텍스트 — title+body(결정의 의미는 본문에 있음). */
	static String embeddingText(String title, String body) {
		String safeTitle = title != null ? title : "";
		String safeBody = body != null ? body : "";
		return (safeTitle + "\n" + safeBody).strip();
	}
}
