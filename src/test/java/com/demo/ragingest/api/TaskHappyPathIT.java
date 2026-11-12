package com.demo.ragingest.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.application.SubmitResult;
import com.demo.ragingest.application.TaskSubmissionService;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.support.TestEvents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.process-sleep-ms=20",
    "task.engine.default-fail-probability=0.0"
})
class TaskHappyPathIT {

    @Autowired TaskSubmissionService submission;
    @Autowired TaskStatusRepository statusRepo;
    @Autowired TaskResultRepository resultRepo;
    @Autowired ObjectMapper objectMapper;

    @Test
    void submittedTaskBecomesSuccessWithResult() {
        String key = "risk:evt:ok:" + UUID.randomUUID();
        SubmitResult r = submission.submit(TestEvents.cmd(key, 8, 0.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = statusRepo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(resultRepo.findById(r.taskId())).isPresent();
            JsonNode result = objectMapper.readTree(
                resultRepo.findById(r.taskId()).orElseThrow().getResultJson());
            assertThat(result.path("normalized").asBoolean()).isTrue();
            assertThat(result.has("riskTags")).isTrue();
        });
    }
}
