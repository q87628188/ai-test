package com.demo.ragingest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SubmitTaskRequest {

    @NotBlank
    private String businessKey;

    @NotBlank
    private String docId;

    @NotBlank
    private String fileName;

    @NotNull
    private Integer priority;

    private Double failProbability;

    private Boolean fatal;

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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
