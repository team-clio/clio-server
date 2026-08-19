package ax.clio.bug.service;

import ax.clio.bug.dto.BugLifecycleResponse;
import ax.clio.bug.dto.UpdateBugRequest;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.entity.BugStatus;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BugLifecycleService {

	private final BugRepository bugRepository;

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
