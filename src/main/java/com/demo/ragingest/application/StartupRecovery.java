package com.demo.ragingest.application;

import com.demo.ragingest.domain.QueuedTask;
import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final TaskStatusRepository statusRepo;
    private final BoundedPriorityTaskQueue queue;
    private final AtomicLong enqueueSequence;

    public StartupRecovery(
        TaskStatusRepository statusRepo,
        BoundedPriorityTaskQueue queue,
        AtomicLong enqueueSequence
    ) {
        this.statusRepo = statusRepo;
        this.queue = queue;
        this.enqueueSequence = enqueueSequence;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    @Transactional
    public void recover() {
        List<TaskStatusEntity> running = statusRepo.findByStatusIn(List.of(TaskStatus.RUNNING));
        for (TaskStatusEntity entity : running) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setFailReason("recovered from RUNNING after restart");
            statusRepo.save(entity);
        }

        List<TaskStatusEntity> dueRetries = statusRepo.findByStatusAndNextRunAtLessThanEqual(
            TaskStatus.RETRY_WAIT, Instant.now());
        for (TaskStatusEntity entity : dueRetries) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setNextRunAt(null);
            statusRepo.save(entity);
        }

        List<TaskStatusEntity> toOffer = new ArrayList<>();
        toOffer.addAll(statusRepo.findByStatusIn(List.of(TaskStatus.QUEUED)));
        int offered = 0;
        for (TaskStatusEntity entity : toOffer) {
            if (!queue.contains(entity.getId())
                && queue.offer(new QueuedTask(entity.getId(), entity.getPriority(), enqueueSequence.incrementAndGet()))) {
                offered++;
            }
        }
        log.info("Startup recovery offered {} queued tasks into memory queue", offered);
    }
}
