package com.demo.ragingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.process-sleep-ms=5000",
    "task.engine.worker-threads=1",
    "task.engine.queue-capacity=20",
    "task.engine.shutdown-timeout-seconds=2",
    "task.engine.default-fail-probability=0.0"
})
class GracefulShutdownIntegrationTest {

    @Autowired TaskSubmissionService submission;
    @Autowired BoundedPriorityTaskQueue queue;
    @Autowired GracefulShutdownManager shutdownManager;
    @Autowired StartupRecovery recovery;
    @Autowired TaskStatusRepository repo;
    @Autowired AcceptingGate gate;

    @Test
    void shutdownStopsAcceptAndStartupRequeuesPersistedWork() {
        String key = "doc:sd:" + UUID.randomUUID();
        SubmitResult r = submission.submit(new SubmitCommand(key, "sd", "sd.pdf", 9, 0.0, false));

        shutdownManager.shutdown();
        assertThat(gate.isAccepting()).isFalse();
        assertThatThrownBy(() -> submission.submit(new SubmitCommand(
            "doc:sd2:" + UUID.randomUUID(), "sd2", "x.pdf", 1, 0.0, false)))
            .isInstanceOf(NotAcceptingException.class);

        queue.drainAll();
        gate.resetAcceptingForTest();
        recovery.recover();

        TaskStatusEntity persisted = repo.findById(r.taskId()).orElseThrow();
        assertThat(persisted.getStatus()).isIn(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.SUCCESS);
        // Workers were stopped by shutdown; recovery must re-admit durable work into memory.
        assertThat(queue.contains(r.taskId()) || persisted.getStatus() == TaskStatus.SUCCESS).isTrue();
    }
}
