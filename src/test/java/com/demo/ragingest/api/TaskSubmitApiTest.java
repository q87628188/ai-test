package com.demo.ragingest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TaskSubmitApiTest {

    @Autowired MockMvc mvc;
    @Autowired TaskStatusRepository repo;
    @Autowired ObjectMapper objectMapper;

    @Test
    void submitReturnsTaskIdAndRowExists() throws Exception {
        String businessKey = "doc:api:" + java.util.UUID.randomUUID();
        String body = """
            {"businessKey":"%s","docId":"api","fileName":"a.pdf","priority":3,"failProbability":0.0}
            """.formatted(businessKey);
        String json = mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        String id = node.get("taskId").asText();
        assertThat(repo.findById(id)).isPresent();
    }
}
