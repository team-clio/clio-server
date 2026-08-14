package ax.clio.project.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ax.clio.project.entity.Project;
import ax.clio.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void createsProjectAndReturnsProjectList() throws Exception {
		projectRepository.save(Project.create("Zulu", "Existing project"));

		mockMvc.perform(post("/api/v1/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Clio Admin","description":"Admin project"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Clio Admin"))
				.andExpect(jsonPath("$.description").value("Admin project"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());

		mockMvc.perform(get("/api/v1/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].name").value("Clio Admin"))
				.andExpect(jsonPath("$.items[1].name").value("Zulu"));
	}

	@Test
	void rejectsBlankProjectName() throws Exception {
		mockMvc.perform(post("/api/v1/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":" ","description":"Invalid project"}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void rejectsDuplicateProjectNameIgnoringCaseAndWhitespace() throws Exception {
		projectRepository.save(Project.create("Clio Admin", null));

		mockMvc.perform(post("/api/v1/projects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"  clio admin  "}
							"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void updatesProjectDetails() throws Exception {
		Project project = projectRepository.save(Project.create("Clio Admin", "Old description"));

		mockMvc.perform(patch("/api/v1/projects/{projectId}", project.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"Clio Console","description":"Updated description"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Clio Console"))
				.andExpect(jsonPath("$.description").value("Updated description"));
	}

	@Test
	void createsUpdatesListsAndDeletesRepository() throws Exception {
		Project project = projectRepository.save(Project.create("Clio Admin", null));

		String createResponse = mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "provider":"GITHUB",
							  "owner":"acme",
							  "name":"clio-web",
							  "url":"https://github.com/acme/clio-web",
							  "defaultBranch":"main",
							  "includePaths":["src","packages/api"],
							  "excludePaths":["dist"],
							  "enabled":true
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.projectId").value(project.getId()))
				.andExpect(jsonPath("$.provider").value("GITHUB"))
				.andExpect(jsonPath("$.syncStatus").value("PENDING"))
				.andExpect(jsonPath("$.includePaths[1]").value("packages/api"))
				.andReturn().getResponse().getContentAsString();

		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(createResponse).get("id").asLong();

		mockMvc.perform(get("/api/v1/projects/{projectId}/repositories", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].name").value("clio-web"));

		mockMvc.perform(patch("/api/v1/projects/{projectId}/repositories/{repositoryId}", project.getId(), repositoryId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "provider":"GITHUB",
							  "owner":"acme",
							  "name":"clio-admin",
							  "url":"https://github.com/acme/clio-admin",
							  "defaultBranch":"develop",
							  "includePaths":[],
							  "excludePaths":["node_modules"],
							  "enabled":false
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("clio-admin"))
				.andExpect(jsonPath("$.defaultBranch").value("develop"))
				.andExpect(jsonPath("$.enabled").value(false));

		mockMvc.perform(delete("/api/v1/projects/{projectId}/repositories/{repositoryId}", project.getId(), repositoryId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/projects/{projectId}/repositories", project.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void rejectsDuplicateRepositoryUrlWithinProject() throws Exception {
		Project project = projectRepository.save(Project.create("Clio Admin", null));
		String body = """
				{
				  "provider":"GITHUB", "owner":"acme", "name":"clio",
				  "url":"https://github.com/acme/clio", "defaultBranch":"main",
				  "includePaths":[], "excludePaths":[], "enabled":true
				}
				""";

		mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/projects/{projectId}/repositories", project.getId())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}
}
