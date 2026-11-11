package com.demo.ragingest.infrastructure.persistence;

import com.demo.ragingest.domain.TaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStatusRepository extends JpaRepository<TaskStatusEntity, String> {

    Optional<TaskStatusEntity> findByBusinessKey(String businessKey);

    long countByStatus(TaskStatus status);

    List<TaskStatusEntity> findByStatusIn(Collection<TaskStatus> statuses);

    List<TaskStatusEntity> findByStatusAndNextRunAtLessThanEqual(TaskStatus status, Instant time);
}
