package com.demo.ragingest.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import com.demo.ragingest.support.TestEvents;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.process-sleep-ms=10",
    "task.engine.max-retries=2",
    "task.engine.retry-base-ms=50",
    "task.engine.retry-max-ms=200",
    "task.engine.retry-scheduler-fixed-delay-ms=50",
    "task.engine.compensator-fixed-delay-ms=100"
})
class TaskRetryIntegrationTest {

    @Autowired TaskSubmissionService submission;
    @Autowired TaskStatusRepository repo;
    @Autowired BoundedPriorityTaskQueue queue;

    @Test
    void fatalGoesToFailedWithoutRetry() {
        String key = "risk:evt:fatal:" + UUID.randomUUID();
        SubmitResult r = submission.submit(TestEvents.cmd(key, 1, 0.0, true));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(e.getRetryCount()).isZero();
            assertThat(e.getFailReason()).isNotBlank();
        });
    }

    @Test
    void retryableExhaustionGoesDead() {
        String key = "risk:evt:retry:" + UUID.randomUUID();
        SubmitResult r = submission.submit(TestEvents.cmd(key, 1, 1.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.DEAD);
            assertThat(e.getRetryCount()).isGreaterThanOrEqualTo(2);
            assertThat(e.getFailReason()).isNotBlank();
        });
    }

    @Test
    void retryWaitDoesNotOccupyQueueSlot() {
        String key = "risk:evt:wait:" + UUID.randomUUID();
        SubmitResult r = submission.submit(TestEvents.cmd(key, 1, 1.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isIn(TaskStatus.RETRY_WAIT, TaskStatus.DEAD, TaskStatus.QUEUED, TaskStatus.RUNNING);
        });
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            if (e.getStatus() == TaskStatus.RETRY_WAIT) {
                assertThat(queue.contains(r.taskId())).isFalse();
                assertThat(queue.size()).isLessThanOrEqualTo(queue.capacity());
            }
        });
    }
}
