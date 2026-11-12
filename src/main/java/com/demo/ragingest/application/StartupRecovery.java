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

/**
 * 启动恢复：将崩溃遗留的 RUNNING / 到期 RETRY_WAIT 纠正为 QUEUED，并重新 offer 进内存队列。
 * <p>支撑「不丢失」：DB 有记录即可恢复；语义为至少一次。
 */
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
        // 崩溃时正在跑的任务：改回 QUEUED，允许再次执行
        List<TaskStatusEntity> running = statusRepo.findByStatusIn(List.of(TaskStatus.RUNNING));
        for (TaskStatusEntity entity : running) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setFailReason("recovered from RUNNING after restart");
            statusRepo.save(entity);
        }

        // 已到期的重试任务：提升为 QUEUED
        List<TaskStatusEntity> dueRetries = statusRepo.findByStatusAndNextRunAtLessThanEqual(
            TaskStatus.RETRY_WAIT, Instant.now());
        for (TaskStatusEntity entity : dueRetries) {
            entity.setStatus(TaskStatus.QUEUED);
            entity.setNextRunAt(null);
            statusRepo.save(entity);
        }

        // 所有 QUEUED 重新进入内存队列（内存在重启后为空）
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
