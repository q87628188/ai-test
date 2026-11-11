package com.demo.ragingest.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskResultRepository extends JpaRepository<TaskResultEntity, String> {
}
