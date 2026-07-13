package ax.clio.report.service;

import ax.clio.report.dto.BugReportCreateRequest;
import ax.clio.report.dto.BugReportResponse;
import ax.clio.report.dto.BugReportStatusUpdateRequest;
import ax.clio.report.entity.BugReport;
import ax.clio.report.repository.BugReportRepository;

import java.util.List;

import ax.clio.common.BusinessException;
import ax.clio.project.entity.Project;
import ax.clio.project.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BugReportService {

	private final BugReportRepository bugReportRepository;
	private final ProjectService projectService;

	public BugReportService(BugReportRepository bugReportRepository, ProjectService projectService) {
		this.bugReportRepository = bugReportRepository;
		this.projectService = projectService;
	}

	@Transactional
	public BugReportResponse create(BugReportCreateRequest request) {
		Project project = projectService.getProject(request.projectId());
		BugReport report = new BugReport(project, request.title(), request.description());
		return BugReportResponse.from(bugReportRepository.save(report));
	}

	@Transactional(readOnly = true)
	public List<BugReportResponse> findAll(Long projectId) {
		if (projectId == null) {
			return bugReportRepository.findAll().stream()
					.map(BugReportResponse::from)
					.toList();
		}
		projectService.getProject(projectId);
		return bugReportRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
				.map(BugReportResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public BugReportResponse findById(Long id) {
		return BugReportResponse.from(getReport(id));
	}

	@Transactional
	public BugReportResponse updateStatus(Long id, BugReportStatusUpdateRequest request) {
		BugReport report = getReport(id);
		report.changeStatus(request.status());
		return BugReportResponse.from(report);
	}

	public BugReport getReport(Long id) {
		return bugReportRepository.findById(id)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Bug report not found"));
	}
}
