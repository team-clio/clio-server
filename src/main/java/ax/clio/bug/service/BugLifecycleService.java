package ax.clio.bug.service;

import ax.clio.agent.event.BugCollectedEvent;
import ax.clio.bug.dto.BugLifecycleResponse;
import ax.clio.bug.dto.UpdateBugRequest;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import ax.clio.project.entity.ProjectSource;
import ax.clio.project.entity.ProjectSourceSyncStatus;
import ax.clio.project.repository.ProjectSourceRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BugLifecycleService {

	private final BugRepository bugRepository;
	private final ProjectSourceRepository projectSourceRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public BugLifecycleResponse update(Long projectId, Long bugId, UpdateBugRequest request) {
		Bug bug = bugRepository.findByIdAndProjectId(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
		try {
			if (request.status() != null) {
				bug.updateStatus(request.status());
			}
			if (request.severity() != null) {
				bug.updateSeverity(request.severity());
			}
		} catch (IllegalStateException exception) {
			throw new ConflictException(exception.getMessage());
		}
		return BugLifecycleResponse.from(bug);
	}

	/**
	 * Agent 처리에 실패한 버그를 다시 처리 대기열에 넣는다. 저장소가 동기화 중이면 {@code NEW}로 남아
	 * 동기화 완료 시 디스패치되고, 동기화에 실패한 저장소가 있으면 먼저 재동기화해야 한다.
	 */
	@Transactional
	public BugLifecycleResponse retry(Long projectId, Long bugId) {
		Bug bug = bugRepository.findByIdAndProjectIdForUpdate(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
		if (bug.getStatus() != BugStatus.FAILED) {
			throw new ConflictException("Only FAILED bugs can be retried: " + bug.getStatus());
		}
		String failedSources = projectSourceRepository.findAllByProjectIdAndEnabledTrue(projectId).stream()
				.filter(source -> source.getSyncStatus() == ProjectSourceSyncStatus.FAILED)
				.map(ProjectSource::getId)
				.map(String::valueOf)
				.collect(Collectors.joining(", "));
		if (!failedSources.isEmpty()) {
			throw new ConflictException(
					"Repository synchronization failed. Resync repositories before retrying: " + failedSources
			);
		}
		bug.updateStatus(BugStatus.NEW);
		eventPublisher.publishEvent(new BugCollectedEvent(projectId, bugId));
		return BugLifecycleResponse.from(bug);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean claimForAnalysis(Long projectId, Long bugId) {
		Bug bug = bugRepository.findByIdAndProjectIdForUpdate(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
		if (bug.getStatus() != BugStatus.NEW) {
			return false;
		}
		bug.updateStatus(BugStatus.ANALYZING);
		return true;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markNew(Long projectId, Long bugId) {
		changeStatus(projectId, bugId, BugStatus.NEW);
	}

	private void changeStatus(Long projectId, Long bugId, BugStatus nextStatus) {
		Bug bug = bugRepository.findByIdAndProjectId(bugId, projectId)
				.orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
		try {
			bug.updateStatus(nextStatus);
		} catch (IllegalStateException exception) {
			throw new ConflictException(exception.getMessage());
		}
	}
}
