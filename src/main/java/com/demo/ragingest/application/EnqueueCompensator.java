package com.demo.ragingest.application;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EnqueueCompensator {

    private final TaskStatusRepository statusRepo;
    private final BoundedPriorityTaskQueue queue;
    private final AtomicLong enqueueSequence;

    public EnqueueCompensator(
        TaskStatusRepository statusRepo,
        BoundedPriorityTaskQueue queue,
        AtomicLong enqueueSequence
    ) {
        this.statusRepo = statusRepo;
        this.queue = queue;
        this.enqueueSequence = enqueueSequence;
    }

    @Scheduled(fixedDelayString = "${task.engine.compensator-fixed-delay-ms:500}")
    public void compensateMissingQueueEntries() {
        List<TaskStatusEntity> queued = statusRepo.findByStatusIn(List.of(TaskStatus.QUEUED));
        for (TaskStatusEntity entity : queued) {
            if (!queue.contains(entity.getId())) {
                queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), enqueueSequence.incrementAndGet()));
            }
        }
    }
}
