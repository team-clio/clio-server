package ax.clio.pcm.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ax.clio.agent.client.PcmInspectClient;
import lombok.RequiredArgsConstructor;

/**
 * PCM inspect API를 admin 화면용으로 중계한다. PCM project_id는 Spring project.id의
 * 문자열 표현(D5)을 그대로 사용하므로 이 서비스에서 Long→String 변환만 수행한다.
 */
@Service
@RequiredArgsConstructor
public class PcmInspectService {

	private final PcmInspectClient pcmInspectClient;

	public Map<String, Object> readSnapshot(Long projectId) {
		return pcmInspectClient.readSnapshot(projectId.toString());
	}

	public List<Map<String, Object>> listKnowledge(Long projectId) {
		return pcmInspectClient.listKnowledge(projectId.toString());
	}

	public Map<String, Object> readKnowledge(Long projectId, String knowledgeId) {
		return pcmInspectClient.readKnowledge(projectId.toString(), knowledgeId);
	}
}
