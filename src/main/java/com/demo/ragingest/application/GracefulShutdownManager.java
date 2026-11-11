package com.demo.ragingest.application;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import com.demo.ragingest.infrastructure.worker.TaskWorkerPool;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GracefulShutdownManager {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    private final AcceptingGate gate;
    private final BoundedPriorityTaskQueue queue;
    private final TaskStatusRepository statusRepo;
    private final TaskWorkerPool workerPool;
    private final TaskEngineProperties props;

    public GracefulShutdownManager(
        AcceptingGate gate,
        BoundedPriorityTaskQueue queue,
        TaskStatusRepository statusRepo,
        TaskWorkerPool workerPool,
        TaskEngineProperties props
    ) {
        this.gate = gate;
        this.queue = queue;
        this.statusRepo = statusRepo;
        this.workerPool = workerPool;
        this.props = props;
    }

    public synchronized void shutdown() {
        gate.stopAccepting();
        List<QueuedTask> drained = queue.drainAll();
        log.info("Drained {} in-memory tasks during shutdown", drained.size());

        List<TaskStatusEntity> running = statusRepo.findByStatusIn(List.of(TaskStatus.RUNNING));
        for (TaskStatusEntity entity : running) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setFailReason("requeued on shutdown");
            statusRepo.save(entity);
        }

        workerPool.shutdown(Duration.ofSeconds(props.getShutdownTimeoutSeconds()));
    }

    @PreDestroy
    public void onDestroy() {
        shutdown();
    }
}
