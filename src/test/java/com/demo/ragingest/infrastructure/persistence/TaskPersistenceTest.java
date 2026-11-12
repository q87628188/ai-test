package com.demo.ragingest.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.ragingest.domain.TaskStatus;
import com.demo.ragingest.domain.TaskType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:taskpersist;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema.sql"
})
class TaskPersistenceTest {
    @Autowired TaskStatusRepository statusRepo;
    @Autowired TaskResultRepository resultRepo;

    @Test
    void savesStatusAndEnforcesBusinessKeyUnique() {
        TaskStatusEntity a = newEntity("risk:evt:a1");
        statusRepo.saveAndFlush(a);
        TaskStatusEntity b = newEntity("risk:evt:a1");
        assertThatThrownBy(() -> statusRepo.saveAndFlush(b))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesResultLinkedToTask() {
        TaskStatusEntity t = statusRepo.saveAndFlush(newEntity("risk:evt:b1"));
        TaskResultEntity r = new TaskResultEntity();
        r.setTaskId(t.getId());
        r.setResultJson("{\"normalized\":true,\"riskTags\":[\"public_ip\"],\"durationMs\":10}");
        r.setCreatedAt(Instant.now());
        resultRepo.saveAndFlush(r);
        assertThat(resultRepo.findById(t.getId())).isPresent();
    }

    private static TaskStatusEntity newEntity(String key) {
        TaskStatusEntity e = new TaskStatusEntity();
        e.setId(UUID.randomUUID().toString());
        e.setBusinessKey(key);
        e.setTaskType(TaskType.RISK_EVENT_INGEST);
        e.setPriority(5);
        e.setStatus(TaskStatus.QUEUED);
        e.setRetryCount(0);
        e.setPayloadJson("{\"userId\":\"u1\",\"clientIp\":\"1.1.1.1\",\"targetSystem\":\"app\"}");
        e.setEnqueuedAt(Instant.now());
        return e;
    }
}
