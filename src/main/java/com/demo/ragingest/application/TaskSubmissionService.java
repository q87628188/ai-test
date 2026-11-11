package com.demo.ragingest.application;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.domain.TaskType;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskSubmissionService {

    private final TaskStatusRepository statusRepo;
    private final BoundedPriorityTaskQueue queue;
    private final TaskMetrics metrics;
    private final AcceptingGate gate;
    private final AtomicLong enqueueSequence;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TaskSubmissionService(
        TaskStatusRepository statusRepo,
        BoundedPriorityTaskQueue queue,
        TaskMetrics metrics,
        AcceptingGate gate,
        AtomicLong enqueueSequence,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate
    ) {
        this.statusRepo = statusRepo;
        this.queue = queue;
        this.metrics = metrics;
        this.gate = gate;
        this.enqueueSequence = enqueueSequence;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public SubmitResult submit(SubmitCommand cmd) {
        if (!gate.isAccepting()) {
            throw new NotAcceptingException("task engine is not accepting new submissions");
        }

        Optional<TaskStatusEntity> existing = statusRepo.findByBusinessKey(cmd.businessKey());
        if (existing.isPresent()) {
            return new SubmitResult(existing.get().getId(), false, true);
        }

        if (queue.size() >= queue.capacity()) {
            throw new QueueFullException("in-memory queue is full");
        }

        TaskStatusEntity entity;
        try {
            entity = transactionTemplate.execute(status -> persistNew(cmd));
        } catch (DataIntegrityViolationException ex) {
            return statusRepo.findByBusinessKey(cmd.businessKey())
                .map(winner -> new SubmitResult(winner.getId(), false, true))
                .orElseThrow(() -> ex);
        }
        long seq = enqueueSequence.incrementAndGet();
        boolean admitted = queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), seq));
        return new SubmitResult(entity.getId(), admitted, false);
    }

    private TaskStatusEntity persistNew(SubmitCommand cmd) {
        TaskStatusEntity entity = new TaskStatusEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setBusinessKey(cmd.businessKey());
        entity.setTaskType(TaskType.DOC_INGEST);
        entity.setPriority(cmd.priority());
        entity.setStatus(TaskStatus.QUEUED);
        entity.setRetryCount(0);
        entity.setPayloadJson(toPayloadJson(cmd));
        entity.setEnqueuedAt(Instant.now());
        return statusRepo.save(entity);
    }

    private String toPayloadJson(SubmitCommand cmd) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", cmd.docId());
        payload.put("fileName", cmd.fileName());
        payload.put("taskType", TaskType.DOC_INGEST.name());
        payload.put("executionProfile", "IO_HYBRID");
        if (cmd.failProbability() != null) {
            payload.put("failProbability", cmd.failProbability());
        }
        if (cmd.fatal() != null) {
            payload.put("fatal", cmd.fatal());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize payload", e);
        }
    }
}
