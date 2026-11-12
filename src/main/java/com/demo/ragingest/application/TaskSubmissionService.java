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

/**
 * 任务提交用例：停机门禁 → businessKey 幂等 → 有界队列反压 → DB 落库 QUEUED → 内存 offer。
 * <p>成功受理边界是 H2 事务提交；内存入队失败可由补偿/恢复补齐。
 */
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

    /**
     * 提交单条风险事件任务。
     *
     * @throws NotAcceptingException 优雅停机中（对外 503）
     * @throws QueueFullException    内存队列已满（对外 429，且不落新库记录）
     */
    public SubmitResult submit(SubmitCommand cmd) {
        // 停机门禁：拒绝新流量
        if (!gate.isAccepting()) {
            throw new NotAcceptingException("task engine is not accepting new submissions");
        }

        // 幂等：同 businessKey 直接返回已有 taskId
        Optional<TaskStatusEntity> existing = statusRepo.findByBusinessKey(cmd.businessKey());
        if (existing.isPresent()) {
            return new SubmitResult(existing.get().getId(), false, true);
        }

        // 反压：满队 fail-fast，避免 OOM；此时尚未写库
        if (queue.size() >= queue.capacity()) {
            throw new QueueFullException("in-memory queue is full");
        }

        TaskStatusEntity entity;
        try {
            // 真相源：先事务写入 QUEUED + payload_json
            entity = transactionTemplate.execute(status -> persistNew(cmd));
        } catch (DataIntegrityViolationException ex) {
            // 并发同键：唯一约束冲突后读出胜者，当作幂等回放
            return statusRepo.findByBusinessKey(cmd.businessKey())
                .map(winner -> new SubmitResult(winner.getId(), false, true))
                .orElseThrow(() -> ex);
        }
        long seq = enqueueSequence.incrementAndGet();
        // 加速器：内存有界优先队列；失败不推翻已受理事实
        boolean admitted = queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), seq));
        return new SubmitResult(entity.getId(), admitted, false);
    }

    /** 新建任务行：状态 QUEUED，消息体写入 payload_json。 */
    private TaskStatusEntity persistNew(SubmitCommand cmd) {
        TaskStatusEntity entity = new TaskStatusEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setBusinessKey(cmd.businessKey());
        entity.setTaskType(TaskType.RISK_EVENT_INGEST);
        entity.setPriority(cmd.priority());
        entity.setStatus(TaskStatus.QUEUED);
        entity.setRetryCount(0);
        entity.setPayloadJson(toPayloadJson(cmd));
        entity.setEnqueuedAt(Instant.now());
        return statusRepo.save(entity);
    }

    /** 将提交字段序列化为 payload_json（整包存库，不按列拆表）。 */
    private String toPayloadJson(SubmitCommand cmd) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", cmd.userId());
        payload.put("eventTime", cmd.eventTime());
        payload.put("deviceId", cmd.deviceId());
        payload.put("clientIp", cmd.clientIp());
        payload.put("targetSystem", cmd.targetSystem());
        if (cmd.action() != null) {
            payload.put("action", cmd.action());
        }
        payload.put("taskType", TaskType.RISK_EVENT_INGEST.name());
        payload.put("executionProfile", "CPU_HYBRID");
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
