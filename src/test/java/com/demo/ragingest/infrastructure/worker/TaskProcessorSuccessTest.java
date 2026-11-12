package com.demo.ragingest.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.domain.TaskType;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.process-sleep-ms=10",
    "task.engine.default-fail-probability=0.0",
    "task.engine.worker-threads=1",
    "task.engine.queue-capacity=10"
})
class TaskProcessorSuccessTest {

    @Autowired TaskProcessor processor;
    @Autowired TaskStatusRepository statusRepo;
    @Autowired TaskResultRepository resultRepo;
    @Autowired ObjectMapper objectMapper;

    @Test
    void processMarksSuccessAndWritesResult() throws Exception {
        String id = UUID.randomUUID().toString();
        TaskStatusEntity entity = new TaskStatusEntity();
        entity.setId(id);
        entity.setBusinessKey("risk:evt:proc:" + id);
        entity.setTaskType(TaskType.RISK_EVENT_INGEST);
        entity.setPriority(5);
        entity.setStatus(TaskStatus.QUEUED);
        entity.setRetryCount(0);
        entity.setPayloadJson(
            "{\"userId\":\"u1\",\"eventTime\":\"2026-11-12T12:00:00Z\",\"deviceId\":\"d1\","
                + "\"clientIp\":\"203.0.113.10\",\"targetSystem\":\"payment-gateway\",\"action\":\"login\","
                + "\"failProbability\":0.0,\"fatal\":false}"
        );
        entity.setEnqueuedAt(Instant.now());
        statusRepo.saveAndFlush(entity);

        processor.process(new QueuedTask(id, 5, 1));

        TaskStatusEntity updated = statusRepo.findById(id).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(resultRepo.findById(id)).isPresent();
        JsonNode result = objectMapper.readTree(resultRepo.findById(id).orElseThrow().getResultJson());
        assertThat(result.path("normalized").asBoolean()).isTrue();
        assertThat(result.path("riskTags").isArray()).isTrue();
        assertThat(result.path("durationMs").isNumber()).isTrue();
    }
}
