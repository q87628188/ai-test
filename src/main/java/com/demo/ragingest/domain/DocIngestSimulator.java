package com.demo.ragingest.domain;

import com.demo.ragingest.infrastructure.config.TaskEngineProperties;
import com.demo.ragingest.infrastructure.persistence.TaskStatusEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class DocIngestSimulator {

    private final ObjectMapper objectMapper;

    public DocIngestSimulator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result simulate(TaskStatusEntity task, TaskEngineProperties props) throws InterruptedException {
        long sleep = props.getProcessSleepMs();
        Thread.sleep(sleep);

        int sink = Objects.hash(task.getPayloadJson(), sleep);
        for (int i = 0; i < 10_000; i++) {
            sink = Objects.hash(sink, i);
        }

        JsonNode payload = parse(task.getPayloadJson());
        if (payload.path("fatal").asBoolean(false)) {
            throw new FatalTaskException("fatal flag set");
        }
        double p = payload.has("failProbability")
            ? payload.path("failProbability").asDouble()
            : props.getDefaultFailProbability();
        if (ThreadLocalRandom.current().nextDouble() < p) {
            throw new RetryableTaskException("simulated parse/embed failure");
        }
        return new Result(3 + Math.abs(sink % 5), 12 + Math.abs(sink % 9), sleep);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new FatalTaskException("invalid payload json");
        }
    }

    public record Result(int chunks, int vectors, long elapsedMs) {}
}
