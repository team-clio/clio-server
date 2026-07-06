package ax.clio.analysis;

import java.time.Instant;

import ax.clio.report.BugReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private BugReport report;

	private Long llmConfigId;

	@Column(length = 200)
	private String llmModel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportSearchInputMode searchMode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisJobStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant startedAt;

	private Instant completedAt;

	@Column(length = 1000)
	private String failureReason;

	private Integer importanceScore;

	private Integer difficultyScore;

	private Integer riskScore;

	@Column(length = 500)
	private String issueType;

	@Column(length = 1000)
	private String keywords;

	@Column(length = 1000)
	private String domains;

	@Column(length = 2000)
	private String summary;

	@Column(length = 10000)
	private String relatedCode;

	@Column(length = 4000)
	private String rationale;

	@Column(length = 4000)
	private String recommendedFix;

	@Column(length = 4000)
	private String recommendedTests;

	protected AnalysisJob() {
	}

	public AnalysisJob(BugReport report, Long llmConfigId, String llmModel, ReportSearchInputMode searchMode) {
		this.report = report;
		this.llmConfigId = llmConfigId;
		this.llmModel = llmModel;
		this.searchMode = searchMode;
		this.status = AnalysisJobStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public void start() {
		this.status = AnalysisJobStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public void complete(AnalysisResultDraft draft) {
		this.status = AnalysisJobStatus.COMPLETED;
		this.completedAt = Instant.now();
		this.importanceScore = draft.importanceScore();
		this.difficultyScore = draft.difficultyScore();
		this.riskScore = draft.riskScore();
		this.issueType = draft.issueType();
		this.keywords = draft.keywords();
		this.domains = draft.domains();
		this.summary = draft.summary();
		this.relatedCode = draft.relatedCode();
		this.rationale = draft.rationale();
		this.recommendedFix = draft.recommendedFix();
		this.recommendedTests = draft.recommendedTests();
	}

	public void fail(String failureReason) {
		this.status = AnalysisJobStatus.FAILED;
		this.completedAt = Instant.now();
		this.failureReason = failureReason;
	}

	public Long getId() {
		return id;
	}

	public BugReport getReport() {
		return report;
	}

	public Long getLlmConfigId() {
		return llmConfigId;
	}

	public String getLlmModel() {
		return llmModel;
	}

	public ReportSearchInputMode getSearchMode() {
		return searchMode;
	}

	public AnalysisJobStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Integer getImportanceScore() {
		return importanceScore;
	}

	public Integer getDifficultyScore() {
		return difficultyScore;
	}

	public Integer getRiskScore() {
		return riskScore;
	}

	public String getIssueType() {
		return issueType;
	}

	public String getKeywords() {
		return keywords;
	}

	public String getDomains() {
		return domains;
	}

	public String getSummary() {
		return summary;
	}

	public String getRelatedCode() {
		return relatedCode;
	}

	public String getRationale() {
		return rationale;
	}

	public String getRecommendedFix() {
		return recommendedFix;
	}

	public String getRecommendedTests() {
		return recommendedTests;
	}
}
