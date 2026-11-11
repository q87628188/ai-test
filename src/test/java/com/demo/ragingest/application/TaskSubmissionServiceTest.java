package com.demo.ragingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.infrastructure.metrics.TaskMetrics;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.demo.ragingest.infrastructure.persistence.TaskStatusRepository;
import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskSubmissionServiceTest {

    @Mock TaskStatusRepository statusRepo;
    @Mock BoundedPriorityTaskQueue queue;
    @Mock TaskMetrics metrics;
    @Mock AcceptingGate gate;
    AtomicLong seq = new AtomicLong();
    TaskSubmissionService service;

    @BeforeEach
    void setUp() {
        when(gate.isAccepting()).thenReturn(true);
        service = new TaskSubmissionService(statusRepo, queue, metrics, gate, seq, new ObjectMapper());
    }

    @Test
    void persistsBeforeReturningAndOffersToQueue() {
        when(statusRepo.findByBusinessKey("doc:1:v1")).thenReturn(Optional.empty());
        when(statusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queue.size()).thenReturn(0);
        when(queue.capacity()).thenReturn(10);
        when(queue.offer(any())).thenReturn(true);

        SubmitResult r = service.submit(new SubmitCommand("doc:1:v1", "1", "a.pdf", 5, 0.0, false));
        assertThat(r.taskId()).isNotBlank();
        assertThat(r.admittedToMemory()).isTrue();
        verify(statusRepo).save(argThat(e -> e.getStatus() == TaskStatus.QUEUED));
        verify(queue).offer(argThat(t -> t.taskId().equals(r.taskId())));
    }

    @Test
    void duplicateBusinessKeyReturnsSameIdWithoutSecondSave() {
        TaskStatusEntity existing = new TaskStatusEntity();
        existing.setId("fixed-id");
        existing.setBusinessKey("doc:1:v1");
        when(statusRepo.findByBusinessKey("doc:1:v1")).thenReturn(Optional.of(existing));

        SubmitResult r = service.submit(new SubmitCommand("doc:1:v1", "1", "a.pdf", 5, null, null));
        assertThat(r.taskId()).isEqualTo("fixed-id");
        assertThat(r.idempotentReplay()).isTrue();
        verify(statusRepo, never()).save(any());
    }

    @Test
    void newKeyWhenQueueFullThrowsQueueFull() {
        when(statusRepo.findByBusinessKey("doc:2:v1")).thenReturn(Optional.empty());
        when(queue.size()).thenReturn(10);
        when(queue.capacity()).thenReturn(10);
        assertThatThrownBy(() -> service.submit(new SubmitCommand("doc:2:v1", "2", "b.pdf", 1, null, null)))
            .isInstanceOf(QueueFullException.class);
        verify(statusRepo, never()).save(any());
    }
}
