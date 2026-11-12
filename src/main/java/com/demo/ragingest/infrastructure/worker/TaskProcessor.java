package com.demo.ragingest.infrastructure.worker;

import com.demo.ragingest.domain.FatalTaskException;
import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.RetryPolicy;
import com.demo.ragingest.domain.RetryableTaskException;
import com.demo.ragingest.domain.RiskEventSimulator;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.persistence.TaskResultEntity;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    private final RiskEventSimulator simulator;
    private final TaskEngineProperties props;
    private final TaskMetrics metrics;
    private final ObjectMapper objectMapper;
    private final RetryPolicy retryPolicy = new RetryPolicy();

    public TaskProcessor(
        TaskStatusRepository statusRepo,
        TaskResultRepository resultRepo,
        RiskEventSimulator simulator,
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
            RiskEventSimulator.Result result = simulator.simulate(entity, props);
            ObjectNode json = objectMapper.createObjectNode();
            json.put("normalized", result.normalized());
            ArrayNode tags = json.putArray("riskTags");
            for (String tag : result.riskTags()) {
                tags.add(tag);
            }
            json.put("durationMs", result.durationMs());

            TaskResultEntity resultEntity = new TaskResultEntity();
            resultEntity.setTaskId(entity.getId());
            resultEntity.setResultJson(json.toString());
            resultEntity.setCreatedAt(Instant.now());
            resultRepo.save(resultEntity);

            Instant finishedAt = Instant.now();
            entity.setStatus(TaskStatus.SUCCESS);
            entity.setFinishedAt(finishedAt);
            entity.setFailReason(null);
            entity.setNextRunAt(null);
            statusRepo.save(entity);

            metrics.incSuccess();
            metrics.recordProcessMs(Duration.between(startedAt, finishedAt).toMillis());
        } catch (FatalTaskException ex) {
            entity.setStatus(TaskStatus.FAILED);
            entity.setFailReason(ex.getMessage());
            entity.setFinishedAt(Instant.now());
            entity.setNextRunAt(null);
            statusRepo.save(entity);
            metrics.incFailed();
            metrics.recordProcessMs(Duration.between(startedAt, Instant.now()).toMillis());
        } catch (RetryableTaskException ex) {
            int next = entity.getRetryCount() + 1;
            entity.setRetryCount(next);
            entity.setFailReason(ex.getMessage());
            metrics.incRetry();
            if (next > props.getMaxRetries()) {
                entity.setStatus(TaskStatus.DEAD);
                entity.setFinishedAt(Instant.now());
                entity.setNextRunAt(null);
                metrics.incFailed();
            } else {
                entity.setStatus(TaskStatus.RETRY_WAIT);
                entity.setNextRunAt(Instant.now().plusMillis(
                    retryPolicy.nextDelayMs(next - 1, props.getRetryBaseMs(), props.getRetryMaxMs())));
            }
            statusRepo.save(entity);
            metrics.recordProcessMs(Duration.between(startedAt, Instant.now()).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            entity.setStatus(TaskStatus.QUEUED);
            entity.setFailReason("interrupted");
            statusRepo.save(entity);
        }
    }
}
