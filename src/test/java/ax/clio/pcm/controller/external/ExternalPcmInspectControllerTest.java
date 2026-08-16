package ax.clio.pcm.controller.external;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ax.clio.pcm.service.PcmInspectService;

@SpringBootTest
@AutoConfigureMockMvc
class ExternalPcmInspectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PcmInspectService pcmInspectService;

	@Test
	void relaysSnapshotUsingStringProjectId() throws Exception {
		org.mockito.Mockito.when(pcmInspectService.readSnapshot(7L)).thenReturn(Map.of(
				"project_id", "7",
				"pcm_revision", 2,
				"knowledge_index_revision", 2
		));

		mockMvc.perform(get("/external-api/v1/pcm/projects/7/snapshot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.project_id").value("7"))
				.andExpect(jsonPath("$.pcm_revision").value(2));

		verify(pcmInspectService).readSnapshot(7L);
	}

	@Test
	void relaysKnowledgeList() throws Exception {
		org.mockito.Mockito.when(pcmInspectService.listKnowledge(7L)).thenReturn(List.of(
				Map.of(
						"project_id", "7",
						"knowledge_id", "kn_1",
						"logical_key", "saved-search-permissions",
						"title", "Saved Search permissions",
						"is_tombstone", false
				)
		));

		mockMvc.perform(get("/external-api/v1/pcm/projects/7/knowledge"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].knowledge_id").value("kn_1"));

		verify(pcmInspectService).listKnowledge(7L);
	}

	@Test
	void relaysKnowledgeDetailAndKeepsAgentBody() throws Exception {
		Map<String, Object> detail = new java.util.LinkedHashMap<>();
		detail.put("project_id", "7");
		detail.put("knowledge_id", "kn_1");
		detail.put("body_markdown", "Only the owner can edit.");
		detail.put("knowledge_revision", 1);
		detail.put("valid_from_pcm_revision", 1);
		detail.put("valid_until_pcm_revision", null);
		org.mockito.Mockito.when(pcmInspectService.readKnowledge(7L, "kn_1")).thenReturn(detail);

		mockMvc.perform(get("/external-api/v1/pcm/projects/7/knowledge/kn_1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body_markdown").value("Only the owner can edit."))
				.andExpect(jsonPath("$.knowledge_revision").value(1));

		verify(pcmInspectService).readKnowledge(7L, "kn_1");
	}
}
