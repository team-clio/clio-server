package ax.clio.report;

import java.time.Instant;

import ax.clio.project.Project;
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
@Table(name = "bug_reports")
public class BugReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 10000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BugReportStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected BugReport() {
	}

	public BugReport(Project project, String title, String description) {
		this.project = project;
		this.title = title;
		this.description = description;
		this.status = BugReportStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public void changeStatus(BugReportStatus status) {
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public BugReportStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
