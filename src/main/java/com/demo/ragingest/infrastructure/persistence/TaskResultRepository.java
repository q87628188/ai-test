package com.demo.ragingest.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** task_result 仓储。 */
public interface TaskResultRepository extends JpaRepository<TaskResultEntity, String> {
}
