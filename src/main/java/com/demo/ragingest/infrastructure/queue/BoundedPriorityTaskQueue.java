package com.demo.ragingest.infrastructure.queue;

import com.demo.ragingest.domain.QueuedTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 自研有界优先队列（JDK PriorityQueue + synchronized）。
 * <ul>
 *   <li>容量上限：满则 {@link #offer} 返回 false，由上层映射 429</li>
 *   <li>排序：priority 降序，同优先级按 enqueueSeq 升序（FIFO）</li>
 *   <li>元素仅含 taskId 等索引，消息体在 DB</li>
 * </ul>
 */
public final class BoundedPriorityTaskQueue {
    private final int capacity;
    private int size = 0;
    private final PriorityQueue<QueuedTask> queue = new PriorityQueue<>(
        Comparator.comparingInt(QueuedTask::priority).reversed()
            .thenComparingLong(QueuedTask::enqueueSeq)
    );

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    /** @return false 表示队列已满（反压） */
    public synchronized boolean offer(QueuedTask task) {
        if (size >= capacity) return false;
        if (!queue.offer(task)) return false;
        size++;
        notifyAll();
        return true;
    }

    /** Worker 阻塞取任务；队列空时 wait。 */
    public synchronized QueuedTask take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        QueuedTask task = queue.poll();
        size--;
        return task;
    }

    public synchronized boolean remove(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId) && queue.remove(candidate)) {
                size--;
                return true;
            }
        }
        return false;
    }

    /** 优雅停机：清空内存队列并返回已排出元素（DB 仍为真相源）。 */
    public synchronized List<QueuedTask> drainAll() {
        List<QueuedTask> drained = new ArrayList<>(queue);
        queue.clear();
        size = 0;
        return drained;
    }

    public synchronized boolean contains(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int size() { return size; }
    public int capacity() { return capacity; }
}
