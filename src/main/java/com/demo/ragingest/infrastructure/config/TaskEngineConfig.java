package com.demo.ragingest.infrastructure.config;

import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TaskEngineProperties.class)
public class TaskEngineConfig {

    @Bean
    BoundedPriorityTaskQueue boundedPriorityTaskQueue(TaskEngineProperties properties) {
        return new BoundedPriorityTaskQueue(properties.resolvedQueueCapacity());
    }

    @Bean
    AtomicLong enqueueSequence() {
        return new AtomicLong();
    }
}
