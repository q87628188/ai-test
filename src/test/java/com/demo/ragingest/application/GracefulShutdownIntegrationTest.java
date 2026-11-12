package com.demo.ragingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import com.demo.ragingest.support.TestEvents;
import java.util.UUID;
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
        String key = "risk:evt:sd:" + UUID.randomUUID();
        SubmitResult r = submission.submit(TestEvents.cmd(key, 9, 0.0, false));

        shutdownManager.shutdown();
        assertThat(gate.isAccepting()).isFalse();
        assertThatThrownBy(() -> submission.submit(TestEvents.cmd(
            "risk:evt:sd2:" + UUID.randomUUID(), 1, 0.0, false)))
            .isInstanceOf(NotAcceptingException.class);

        queue.drainAll();
        gate.resetAcceptingForTest();
        recovery.recover();

        TaskStatusEntity persisted = repo.findById(r.taskId()).orElseThrow();
        assertThat(persisted.getStatus()).isIn(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.SUCCESS);
        assertThat(queue.contains(r.taskId()) || persisted.getStatus() == TaskStatus.SUCCESS).isTrue();
    }
}
