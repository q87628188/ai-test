package com.demo.ragingest.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 映射 {@code task_result}：任务成功后的输出结果（与 task_status 1:1）。
 */
@Entity
@Table(name = "task_result")
public class TaskResultEntity {

    /** 对应 task_status.id。 */
    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    /** 输出 JSON：normalized / riskTags / durationMs。 */
    @Lob
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    /** 结果写入时间。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
