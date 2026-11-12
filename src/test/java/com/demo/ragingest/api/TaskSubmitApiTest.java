package com.demo.ragingest.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.support.TestEvents;
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
        String businessKey = "risk:evt:" + java.util.UUID.randomUUID();
        String body = TestEvents.jsonBody(businessKey, 3, 0.0);
        String json = mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        String id = node.get("taskId").asText();
        assertThat(repo.findById(id)).isPresent();
    }

    @Test
    void duplicateBusinessKeyIsIdempotent() throws Exception {
        String businessKey = "risk:evt:" + java.util.UUID.randomUUID();
        String body = TestEvents.jsonBody(businessKey, 5, 0.0);
        String first = mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotentReplay").value(true))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(first).get("taskId").asText())
            .isEqualTo(objectMapper.readTree(second).get("taskId").asText());
    }

    @Test
    void missingRequiredFieldRejected() throws Exception {
        String body = """
            {"businessKey":"risk:evt:bad","priority":1}
            """;
        mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}
