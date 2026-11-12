package com.demo.ragingest.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.domain.TaskType;
import com.demo.ragingest.infrastructure.persistence.TaskResultEntity;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.worker-threads=1",
    "task.engine.queue-capacity=50",
    "task.engine.process-sleep-ms=5000",
    "task.engine.shutdown-timeout-seconds=2",
    "task.engine.default-fail-probability=0.0"
})
class StartupRecoveryTest {

    @Autowired StartupRecovery recovery;
    @Autowired GracefulShutdownManager shutdownManager;
    @Autowired AcceptingGate gate;
    @Autowired TaskStatusRepository statusRepo;
    @Autowired TaskResultRepository resultRepo;
    @Autowired BoundedPriorityTaskQueue queue;

    @Test
    void recoverReloadsUnconsumedAndSkipsTerminal() {
        shutdownManager.shutdown();
        queue.drainAll();
        gate.resetAcceptingForTest();

        TaskStatusEntity queued = persist("risk:rec:q:" + UUID.randomUUID(), TaskStatus.QUEUED, null);
        TaskStatusEntity running = persist("risk:rec:r:" + UUID.randomUUID(), TaskStatus.RUNNING, null);
        TaskStatusEntity success = persist("risk:rec:s:" + UUID.randomUUID(), TaskStatus.SUCCESS, Instant.now());
        TaskResultEntity result = new TaskResultEntity();
        result.setTaskId(success.getId());
        result.setResultJson("{\"normalized\":true,\"riskTags\":[],\"durationMs\":1}");
        result.setCreatedAt(Instant.now());
        resultRepo.saveAndFlush(result);
        TaskStatusEntity failed = persist("risk:rec:f:" + UUID.randomUUID(), TaskStatus.FAILED, Instant.now());
        TaskStatusEntity dead = persist("risk:rec:d:" + UUID.randomUUID(), TaskStatus.DEAD, Instant.now());

        recovery.recover();

        assertThat(statusRepo.findById(running.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(queue.contains(queued.getId())).isTrue();
        assertThat(queue.contains(running.getId())).isTrue();
        assertThat(queue.contains(success.getId())).isFalse();
        assertThat(queue.contains(failed.getId())).isFalse();
        assertThat(queue.contains(dead.getId())).isFalse();
        assertThat(statusRepo.findById(success.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(resultRepo.findById(success.getId())).isPresent();
    }

    private TaskStatusEntity persist(String key, TaskStatus status, Instant finishedAt) {
        TaskStatusEntity e = new TaskStatusEntity();
        e.setId(UUID.randomUUID().toString());
        e.setBusinessKey(key);
        e.setTaskType(TaskType.RISK_EVENT_INGEST);
        e.setPriority(5);
        e.setStatus(status);
        e.setRetryCount(0);
        e.setPayloadJson("{\"userId\":\"u1\",\"clientIp\":\"1.1.1.1\",\"targetSystem\":\"app\"}");
        e.setEnqueuedAt(Instant.now().minusSeconds(10));
        if (status == TaskStatus.RUNNING || status == TaskStatus.SUCCESS
            || status == TaskStatus.FAILED || status == TaskStatus.DEAD) {
            e.setStartedAt(Instant.now().minusSeconds(5));
        }
        e.setFinishedAt(finishedAt);
        if (status == TaskStatus.FAILED || status == TaskStatus.DEAD) {
            e.setFailReason("terminal");
        }
        return statusRepo.saveAndFlush(e);
    }
}
