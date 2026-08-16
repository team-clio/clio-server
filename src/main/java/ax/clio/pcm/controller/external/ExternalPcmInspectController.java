package ax.clio.pcm.controller.external;

import static ax.clio.common.api.ApiPaths.EXTERNAL_V1;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ax.clio.pcm.service.PcmInspectService;
import lombok.RequiredArgsConstructor;

/**
 * 에이전트 PCM inspect API를 admin 화면에 중계한다. 응답은 에이전트 JSON을 그대로
 * 통과시키므로 Spring은 PCM 데이터를 해석하지 않는다.
 */
@RestController
@RequestMapping(EXTERNAL_V1 + "/pcm/projects/{projectId}")
@RequiredArgsConstructor
public class ExternalPcmInspectController {

	private final PcmInspectService pcmInspectService;

	@GetMapping("/snapshot")
	public ResponseEntity<Map<String, Object>> snapshot(@PathVariable Long projectId) {
		return ResponseEntity.ok(pcmInspectService.readSnapshot(projectId));
	}

	@GetMapping("/knowledge")
	public ResponseEntity<List<Map<String, Object>>> knowledge(@PathVariable Long projectId) {
		return ResponseEntity.ok(pcmInspectService.listKnowledge(projectId));
	}

	@GetMapping("/knowledge/{knowledgeId}")
	public ResponseEntity<Map<String, Object>> knowledgeDetail(
			@PathVariable Long projectId,
			@PathVariable String knowledgeId
	) {
		return ResponseEntity.ok(pcmInspectService.readKnowledge(projectId, knowledgeId));
	}
}
