package com.demo.ragingest.application;

/**
 * 提交结果。
 *
 * @param taskId            任务 ID
 * @param admittedToMemory  是否成功进入内存队列
 * @param idempotentReplay  是否因同 businessKey 命中已有任务
 */
public record SubmitResult(String taskId, boolean admittedToMemory, boolean idempotentReplay) {}
