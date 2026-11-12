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

/**
 * 入队补偿：DB 已为 QUEUED 但内存队列缺失时补 offer（覆盖落库后 offer 失败等窗口）。
 */
@Component
public class EnqueueCompensator {

    private final TaskStatusRepository statusRepo;
    private final BoundedPriorityTaskQueue queue;
    private final AtomicLong enqueueSequence;
    private final AcceptingGate gate;

    public EnqueueCompensator(
        TaskStatusRepository statusRepo,
        BoundedPriorityTaskQueue queue,
        AtomicLong enqueueSequence,
        AcceptingGate gate
    ) {
        this.statusRepo = statusRepo;
        this.queue = queue;
        this.enqueueSequence = enqueueSequence;
        this.gate = gate;
    }

    @Scheduled(fixedDelayString = "${task.engine.compensator-fixed-delay-ms:500}")
    public void compensateMissingQueueEntries() {
        if (!gate.isAccepting()) {
            return;
        }
        List<TaskStatusEntity> queued = statusRepo.findByStatusIn(List.of(TaskStatus.QUEUED));
        for (TaskStatusEntity entity : queued) {
            if (!queue.contains(entity.getId())) {
                queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), enqueueSequence.incrementAndGet()));
            }
        }
    }
}
