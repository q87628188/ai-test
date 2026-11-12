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

/**
 * 优雅停机：停收新任务 → drain 内存队列 → RUNNING 回写 QUEUED → 限时停 Worker。
 */
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
        // 1) 关闭受理门，新提交返回 503
        gate.stopAccepting();
        // 2) 清空内存队列（DB 仍保留 QUEUED）
        List<QueuedTask> drained = queue.drainAll();
        log.info("Drained {} in-memory tasks during shutdown", drained.size());

        try {
            // 3) 尽量把 RUNNING 对齐回 QUEUED，便于下次启动恢复
            List<TaskStatusEntity> running = statusRepo.findByStatusIn(List.of(TaskStatus.RUNNING));
            for (TaskStatusEntity entity : running) {
                entity.setStatus(TaskStatus.QUEUED);
                entity.setFailReason("requeued on shutdown");
                statusRepo.save(entity);
            }
        } catch (RuntimeException ex) {
            log.warn("Skip RUNNING requeue during shutdown (datasource may already be closed): {}", ex.getMessage());
        }

        // 4) 限时打断并等待 Worker 退出
        workerPool.shutdown(Duration.ofSeconds(props.getShutdownTimeoutSeconds()));
    }

    /** Spring 容器销毁钩子。 */
    @PreDestroy
    public void onDestroy() {
        shutdown();
    }
}
