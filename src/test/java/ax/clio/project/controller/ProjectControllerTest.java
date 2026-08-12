package ax.clio.project.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
