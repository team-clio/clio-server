package ax.clio.agent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ax.clio.agent.client.ClioAgentClient;
import ax.clio.agent.client.RepositorySyncRequestType;
import ax.clio.project.entity.Project;
import ax.clio.project.entity.ProjectSourceSyncStatus;
import ax.clio.project.repository.ProjectRepository;
import ax.clio.project.repository.ProjectSourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "clio.agent.enabled=true")
@AutoConfigureMockMvc
class RepositorySyncDispatchIntegrationTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired private MockMvc mockMvc;
	@Autowired private ProjectRepository projectRepository;
	@Autowired private ProjectSourceRepository projectSourceRepository;
	@MockitoBean private ClioAgentClient agentClient;

	@AfterEach
	void cleanUp() {
		projectSourceRepository.deleteAll();
		projectRepository.deleteAll();
	}

	@Test
	void dispatchesRepositoryAddedAfterCommitAndMarksSyncing() throws Exception {
		Project project = projectRepository.save(Project.create("Sync dispatch", null));

		String response = mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "provider":"GITHUB",
								  "owner":"acme",
								  "name":"clio-web",
								  "url":"https://github.com/acme/clio-web",
								  "defaultBranch":"main",
								  "includePaths":[],
								  "excludePaths":[],
								  "enabled":true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode created = OBJECT_MAPPER.readTree(response);
		long sourceId = created.get("id").asLong();

		verify(agentClient).dispatchRepositorySync(
				eq(project.getId()),
				eq(sourceId),
				eq(RepositorySyncRequestType.REPOSITORY_ADDED),
				anyString(),
				eq("main"),
				eq("https://github.com/acme/clio-web"),
				eq(List.of()),
				eq(List.of())
		);
		assertEquals(
				ProjectSourceSyncStatus.SYNCING,
				projectSourceRepository.findById(sourceId).orElseThrow().getSyncStatus()
		);
	}

	@Test
	void dispatchesRepositoryRemovedAfterCommit() throws Exception {
		Project project = projectRepository.save(Project.create("Sync dispatch", null));

		String response = mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "provider":"GITHUB",
								  "owner":"acme",
								  "name":"clio-web",
								  "url":"https://github.com/acme/clio-web",
								  "defaultBranch":"main",
								  "includePaths":[],
								  "excludePaths":[],
								  "enabled":true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long sourceId = OBJECT_MAPPER.readTree(response).get("id").asLong();

		mockMvc.perform(delete("/api/v1/projects/{projectId}/repositories/{repositoryId}", project.getId(), sourceId))
				.andExpect(status().isNoContent());

		verify(agentClient).dispatchRepositorySync(
				eq(project.getId()),
				eq(sourceId),
				eq(RepositorySyncRequestType.REPOSITORY_REMOVED),
				anyString(),
				eq("main"),
				eq("https://github.com/acme/clio-web"),
				eq(List.of()),
				eq(List.of())
		);
	}

	@Test
	void dispatchesRepositoryUpdateWithConfiguredAnalysisPaths() throws Exception {
		Project project = projectRepository.save(Project.create("Scope dispatch", null));
		String response = mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "provider":"GITHUB", "owner":"acme", "name":"clio-web",
								  "url":"https://github.com/acme/clio-web", "defaultBranch":"main",
								  "includePaths":[], "excludePaths":[], "enabled":true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long sourceId = OBJECT_MAPPER.readTree(response).get("id").asLong();
		clearInvocations(agentClient);

		mockMvc.perform(patch("/api/v1/projects/{projectId}/repositories/{repositoryId}", project.getId(), sourceId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "provider":"GITHUB", "owner":"acme", "name":"clio-web",
								  "url":"https://github.com/acme/clio-web", "defaultBranch":"main",
								  "includePaths":["src/**"],
								  "excludePaths":["src/generated/**"], "enabled":true
								}
								"""))
				.andExpect(status().isOk());

		verify(agentClient).dispatchRepositorySync(
				eq(project.getId()), eq(sourceId), eq(RepositorySyncRequestType.REPOSITORY_ADDED),
				anyString(), eq("main"), eq("https://github.com/acme/clio-web"),
				eq(List.of("src/**")), eq(List.of("src/generated/**"))
		);
		assertEquals(
				ProjectSourceSyncStatus.SYNCING,
				projectSourceRepository.findById(sourceId).orElseThrow().getSyncStatus()
		);
	}
}
