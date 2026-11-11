package com.demo.ragingest.domain;

public record QueuedTask(String taskId, int priority, long enqueueSeq) {}
