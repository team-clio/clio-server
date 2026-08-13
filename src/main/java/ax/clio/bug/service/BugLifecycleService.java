package ax.clio.bug.service;

import ax.clio.bug.dto.BugLifecycleResponse;
import ax.clio.bug.dto.UpdateBugRequest;
import ax.clio.bug.entity.Bug;
import ax.clio.bug.repository.BugRepository;
import ax.clio.common.ConflictException;
import ax.clio.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
}
