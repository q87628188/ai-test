package com.demo.ragingest.application;

import com.demo.ragingest.api.dto.MetricsResponse;
import com.demo.ragingest.api.dto.TaskResultResponse;
import com.demo.ragingest.api.dto.TaskStatsResponse;
import com.demo.ragingest.api.dto.TaskStatusResponse;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.persistence.TaskResultEntity;
import com.demo.ragingest.infrastructure.persistence.TaskResultRepository;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** 任务查询：状态、结果、队列/状态统计。 */
@Service
public class TaskQueryService {

    private final TaskStatusRepository statusRepo;
    private final TaskResultRepository resultRepo;
    private final BoundedPriorityTaskQueue queue;
    private final TaskMetrics metrics;

    public TaskQueryService(
        TaskStatusRepository statusRepo,
        TaskResultRepository resultRepo,
        BoundedPriorityTaskQueue queue,
        TaskMetrics metrics
    ) {
        this.statusRepo = statusRepo;
        this.resultRepo = resultRepo;
        this.queue = queue;
        this.metrics = metrics;
    }

    public TaskStatusResponse getStatus(String taskId) {
        TaskStatusEntity entity = statusRepo.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("task not found: " + taskId));
        TaskStatusResponse response = new TaskStatusResponse();
        response.setTaskId(entity.getId());
        response.setBusinessKey(entity.getBusinessKey());
        response.setStatus(entity.getStatus().name());
        response.setPriority(entity.getPriority());
        response.setRetryCount(entity.getRetryCount());
        response.setFailReason(entity.getFailReason());
        response.setEnqueuedAt(entity.getEnqueuedAt());
        response.setStartedAt(entity.getStartedAt());
        response.setFinishedAt(entity.getFinishedAt());
        response.setNextRunAt(entity.getNextRunAt());
        return response;
    }

    public TaskResultResponse getResult(String taskId) {
        TaskStatusEntity status = statusRepo.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("task not found: " + taskId));
        if (status.getStatus() != TaskStatus.SUCCESS) {
            throw new NoSuchElementException("result not available for task: " + taskId);
        }
        TaskResultEntity result = resultRepo.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("result not found: " + taskId));
        TaskResultResponse response = new TaskResultResponse();
        response.setTaskId(result.getTaskId());
        response.setResultJson(result.getResultJson());
        response.setCreatedAt(result.getCreatedAt());
        return response;
    }

    public TaskStatsResponse stats() {
        long backlog = statusRepo.countByStatus(TaskStatus.QUEUED)
            + statusRepo.countByStatus(TaskStatus.RETRY_WAIT);
        long processing = statusRepo.countByStatus(TaskStatus.RUNNING);
        long failed = statusRepo.countByStatus(TaskStatus.FAILED)
            + statusRepo.countByStatus(TaskStatus.DEAD);
        return new TaskStatsResponse(backlog, processing, failed);
    }

    public MetricsResponse metrics() {
        MetricsResponse response = new MetricsResponse();
        response.setQueueSize(queue.size());
        response.setQueueCapacity(queue.capacity());
        response.setActiveThreads(metrics.activeThreads());
        response.setSuccessCount(metrics.successCount());
        response.setFailureCount(metrics.failureCount());
        response.setRetryCount(metrics.retryCount());
        response.setAvgQueueMs(metrics.avgQueueMs());
        response.setAvgProcessMs(metrics.avgProcessMs());
        return response;
    }
}
