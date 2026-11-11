package com.demo.ragingest.infrastructure.worker;

import com.demo.ragingest.domain.DocIngestSimulator;
import com.demo.ragingest.domain.FatalTaskException;
import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.RetryableTaskException;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.persistence.TaskResultEntity;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskProcessor {

    private final TaskStatusRepository statusRepo;
    private final TaskResultRepository resultRepo;
    private final DocIngestSimulator simulator;
    private final TaskEngineProperties props;
    private final TaskMetrics metrics;
    private final ObjectMapper objectMapper;

    public TaskProcessor(
        TaskStatusRepository statusRepo,
        TaskResultRepository resultRepo,
        DocIngestSimulator simulator,
        TaskEngineProperties props,
        TaskMetrics metrics,
        ObjectMapper objectMapper
    ) {
        this.statusRepo = statusRepo;
        this.resultRepo = resultRepo;
        this.simulator = simulator;
        this.props = props;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(QueuedTask qt) {
        Optional<TaskStatusEntity> optional = statusRepo.findById(qt.taskId());
        if (optional.isEmpty()) {
            return;
        }
        TaskStatusEntity entity = optional.get();
        if (entity.getStatus() != TaskStatus.QUEUED && entity.getStatus() != TaskStatus.RETRY_WAIT) {
            return;
        }

        Instant startedAt = Instant.now();
        if (entity.getEnqueuedAt() != null) {
            metrics.recordQueueWaitMs(Duration.between(entity.getEnqueuedAt(), startedAt).toMillis());
        }
        entity.setStatus(TaskStatus.RUNNING);
        entity.setStartedAt(startedAt);
        statusRepo.save(entity);

        try {
            DocIngestSimulator.Result result = simulator.simulate(entity, props);
            ObjectNode json = objectMapper.createObjectNode();
            json.put("chunks", result.chunks());
            json.put("vectors", result.vectors());
            json.put("elapsedMs", result.elapsedMs());

            TaskResultEntity resultEntity = new TaskResultEntity();
            resultEntity.setTaskId(entity.getId());
            resultEntity.setResultJson(json.toString());
            resultEntity.setCreatedAt(Instant.now());
            resultRepo.save(resultEntity);

            Instant finishedAt = Instant.now();
            entity.setStatus(TaskStatus.SUCCESS);
            entity.setFinishedAt(finishedAt);
            entity.setFailReason(null);
            statusRepo.save(entity);

            metrics.incSuccess();
            metrics.recordProcessMs(Duration.between(startedAt, finishedAt).toMillis());
        } catch (FatalTaskException | RetryableTaskException | InterruptedException ex) {
            // Task 6 will classify retries; Task 5 success path only.
            // Keep InterruptedException from leaking as silent success.
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("processing failed; retry handling comes in Task 6", ex);
        }
    }
}
