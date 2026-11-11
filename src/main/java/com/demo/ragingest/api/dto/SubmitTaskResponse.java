package com.demo.ragingest.api.dto;

public class SubmitTaskResponse {

    private String taskId;
    private boolean admittedToMemory;
    private boolean idempotentReplay;

    public SubmitTaskResponse() {}

    public SubmitTaskResponse(String taskId, boolean admittedToMemory, boolean idempotentReplay) {
        this.taskId = taskId;
        this.admittedToMemory = admittedToMemory;
        this.idempotentReplay = idempotentReplay;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public boolean isAdmittedToMemory() {
        return admittedToMemory;
    }

    public void setAdmittedToMemory(boolean admittedToMemory) {
        this.admittedToMemory = admittedToMemory;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public void setIdempotentReplay(boolean idempotentReplay) {
        this.idempotentReplay = idempotentReplay;
    }
}
