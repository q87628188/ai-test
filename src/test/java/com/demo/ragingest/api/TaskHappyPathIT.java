package com.demo.ragingest.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.application.SubmitCommand;
import com.demo.ragingest.application.SubmitResult;
import com.demo.ragingest.application.TaskSubmissionService;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
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

    @Test
    void submittedTaskBecomesSuccessWithResult() {
        String key = "doc:ok:" + UUID.randomUUID();
        SubmitResult r = submission.submit(new SubmitCommand(key, "ok", "ok.pdf", 8, 0.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = statusRepo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(resultRepo.findById(r.taskId())).isPresent();
        });
    }
}
