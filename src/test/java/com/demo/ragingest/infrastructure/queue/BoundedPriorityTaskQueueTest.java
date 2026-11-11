package com.demo.ragingest.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.ragingest.domain.QueuedTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedPriorityTaskQueueTest {
    @Test
    void higherPriorityDequeuedFirst() throws Exception {
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(8);
        assertThat(q.offer(new QueuedTask("low", 1, 1))).isTrue();
        assertThat(q.offer(new QueuedTask("high", 10, 2))).isTrue();
        assertThat(q.take().taskId()).isEqualTo("high");
        assertThat(q.take().taskId()).isEqualTo("low");
    }

    @Test
    void rejectsWhenFullAndNeverExceedsCapacity() {
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(2);
        assertThat(q.offer(new QueuedTask("a", 1, 1))).isTrue();
        assertThat(q.offer(new QueuedTask("b", 1, 2))).isTrue();
        assertThat(q.offer(new QueuedTask("c", 9, 3))).isFalse();
        assertThat(q.size()).isEqualTo(2);
        assertThat(q.capacity()).isEqualTo(2);
    }

    @Test
    void concurrentOffersNeverExceedCapacity() throws Exception {
        int capacity = 50;
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(capacity);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                if (q.offer(new QueuedTask("t" + idx, idx % 5, idx))) {
                    accepted.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdownNow();
        assertThat(q.size()).isLessThanOrEqualTo(capacity);
        assertThat(accepted.get()).isEqualTo(q.size());
        assertThat(accepted.get()).isLessThanOrEqualTo(capacity);
    }
}
