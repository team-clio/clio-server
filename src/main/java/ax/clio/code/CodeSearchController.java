package ax.clio.code;

import java.util.List;

import ax.clio.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class CodeSearchController {

	private final CodeSearchService codeSearchService;

	public CodeSearchController(CodeSearchService codeSearchService) {
		this.codeSearchService = codeSearchService;
	}

	@GetMapping
	public ApiResponse<List<CodeSearchResult>> search(@PathVariable Long projectId,
			@RequestParam String query,
			@RequestParam(required = false) Integer limit) {
		return ApiResponse.ok(codeSearchService.search(projectId, query, limit));
	}
}
