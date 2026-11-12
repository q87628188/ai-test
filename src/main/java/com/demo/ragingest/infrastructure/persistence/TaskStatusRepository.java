package com.demo.ragingest.infrastructure.persistence;

import com.demo.ragingest.domain.TaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** task_status 仓储：幂等查询、按状态/到期时间扫描。 */
public interface TaskStatusRepository extends JpaRepository<TaskStatusEntity, String> {

    /** 幂等：按 business_key 查找已有任务。 */
    Optional<TaskStatusEntity> findByBusinessKey(String businessKey);

    long countByStatus(TaskStatus status);

    /** 恢复/补偿：按一批状态拉取。 */
    List<TaskStatusEntity> findByStatusIn(Collection<TaskStatus> statuses);

    /** 重试调度：到期 RETRY_WAIT。 */
    List<TaskStatusEntity> findByStatusAndNextRunAtLessThanEqual(TaskStatus status, Instant time);
}
