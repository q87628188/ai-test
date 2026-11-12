package com.demo.ragingest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 提交风险事件请求体；业务字段最终写入 task_status.payload_json。 */
public class SubmitTaskRequest {

    /** 幂等键。 */
    @NotBlank
    private String businessKey;

    @NotBlank
    private String userId;

    @NotBlank
    private String eventTime;

    @NotBlank
    private String deviceId;

    @NotBlank
    private String clientIp;

    @NotBlank
    private String targetSystem;

    /** 可选动作标签。 */
    private String action;

    @NotNull
    private Integer priority;

    /** 可选：模拟可重试失败概率。 */
    private Double failProbability;

    /** 可选：模拟 fatal 失败。 */
    private Boolean fatal;

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Double getFailProbability() {
        return failProbability;
    }

    public void setFailProbability(Double failProbability) {
        this.failProbability = failProbability;
    }

    public Boolean getFatal() {
        return fatal;
    }

    public void setFatal(Boolean fatal) {
        this.fatal = fatal;
    }
}
