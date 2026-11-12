package com.demo.ragingest.infrastructure.config;

import com.demo.ragingest.infrastructure.queue.BoundedPriorityTaskQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 注册有界队列、入队序号，并开启定时调度（重试/补偿）。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TaskEngineProperties.class)
public class TaskEngineConfig {

    /** 按配置容量创建内存有界优先队列。 */
    @Bean
    BoundedPriorityTaskQueue boundedPriorityTaskQueue(TaskEngineProperties properties) {
        return new BoundedPriorityTaskQueue(properties.resolvedQueueCapacity());
    }

    /** 全局单调入队序号，保证同优先级 FIFO。 */
    @Bean
    AtomicLong enqueueSequence() {
        return new AtomicLong();
    }
}
