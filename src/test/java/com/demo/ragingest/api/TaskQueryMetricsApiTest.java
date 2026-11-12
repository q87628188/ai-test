package com.demo.ragingest.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.ragingest.application.TaskSubmissionService;
import com.demo.ragingest.support.TestEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TaskQueryMetricsApiTest {

    @Autowired MockMvc mvc;
    @Autowired TaskSubmissionService submission;

    @Test
    void queryStatsAndMetricsFieldsPresent() throws Exception {
        submission.submit(TestEvents.cmd("risk:evt:q:" + UUID.randomUUID(), 2, 0.0, false));
        mvc.perform(get("/api/tasks/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.backlog").exists())
            .andExpect(jsonPath("$.processing").exists())
            .andExpect(jsonPath("$.failed").exists());
        mvc.perform(get("/api/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueSize").exists())
            .andExpect(jsonPath("$.queueCapacity").exists())
            .andExpect(jsonPath("$.activeThreads").exists())
            .andExpect(jsonPath("$.successCount").exists())
            .andExpect(jsonPath("$.failureCount").exists())
            .andExpect(jsonPath("$.retryCount").exists())
            .andExpect(jsonPath("$.avgQueueMs").exists())
            .andExpect(jsonPath("$.avgProcessMs").exists());
    }
}
