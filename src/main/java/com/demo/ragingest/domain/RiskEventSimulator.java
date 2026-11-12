package com.demo.ragingest.domain;

import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 风险事件处理模拟器：从 {@code payload_json} 解析字段，模拟清洗/特征与可配置失败。
 */
@Component
public class RiskEventSimulator {

    private final ObjectMapper objectMapper;

    public RiskEventSimulator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param task 含 payload_json 的任务实体
     * @throws FatalTaskException     不可重试失败
     * @throws RetryableTaskException 可退避重试失败
     */
    public Result simulate(TaskStatusEntity task, TaskEngineProperties props) throws InterruptedException {
        long sleep = props.getProcessSleepMs();
        Thread.sleep(sleep);

        // 轻量 CPU 忙等，模拟特征计算
        int sink = Objects.hash(task.getPayloadJson(), sleep);
        for (int i = 0; i < 20_000; i++) {
            sink = Objects.hash(sink, i);
        }

        // 消息体从 DB 字段反序列化
        JsonNode payload = parse(task.getPayloadJson());
        if (payload.path("fatal").asBoolean(false)) {
            throw new FatalTaskException("fatal flag set");
        }
        double p = payload.has("failProbability")
            ? payload.path("failProbability").asDouble()
            : props.getDefaultFailProbability();
        if (ThreadLocalRandom.current().nextDouble() < p) {
            throw new RetryableTaskException("simulated cleanse/feature failure");
        }

        String userId = payload.path("userId").asText("");
        String clientIp = payload.path("clientIp").asText("");
        String targetSystem = payload.path("targetSystem").asText("");
        if (userId.isBlank() || clientIp.isBlank() || targetSystem.isBlank()) {
            throw new FatalTaskException("invalid event payload");
        }

        List<String> tags = new ArrayList<>();
        if (clientIp.startsWith("10.") || clientIp.startsWith("192.168.")) {
            tags.add("private_ip");
        } else {
            tags.add("public_ip");
        }
        if (targetSystem.toLowerCase().contains("payment") || targetSystem.toLowerCase().contains("auth")) {
            tags.add("high_value_system");
        }
        if (Math.abs(sink % 7) == 0) {
            tags.add("anomaly_hint");
        }

        return new Result(true, List.copyOf(tags), sleep);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new FatalTaskException("invalid payload json");
        }
    }

    public record Result(boolean normalized, List<String> riskTags, long durationMs) {}
}
