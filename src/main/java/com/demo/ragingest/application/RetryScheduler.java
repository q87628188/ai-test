package com.demo.ragingest.application;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetryScheduler {

    private final TaskStatusRepository statusRepo;
    private final BoundedPriorityTaskQueue queue;
    private final AtomicLong enqueueSequence;

    public RetryScheduler(
        TaskStatusRepository statusRepo,
        BoundedPriorityTaskQueue queue,
        AtomicLong enqueueSequence
    ) {
        this.statusRepo = statusRepo;
        this.queue = queue;
        this.enqueueSequence = enqueueSequence;
    }

    @Scheduled(fixedDelayString = "${task.engine.retry-scheduler-fixed-delay-ms:200}")
    @Transactional
    public void promoteDueRetries() {
        List<TaskStatusEntity> due = statusRepo.findByStatusAndNextRunAtLessThanEqual(
            TaskStatus.RETRY_WAIT, Instant.now());
        for (TaskStatusEntity entity : due) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setNextRunAt(null);
            statusRepo.save(entity);
            queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), enqueueSequence.incrementAndGet()));
        }
    }
}
