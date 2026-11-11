package com.demo.ragingest.application;

public record SubmitResult(String taskId, boolean admittedToMemory, boolean idempotentReplay) {}
