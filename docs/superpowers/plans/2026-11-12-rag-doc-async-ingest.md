# RAG Doc Async Ingest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付单机可运行的 Spring Boot 文档入库异步任务引擎：接收→有界优先队列→Worker 模拟处理→结果可追溯，满足入队不丢失、反压、幂等、重试与优雅停机。

**Architecture:** H2 文件库为任务真相源；自研有界优先队列仅作进程内调度加速；固定 Worker 拉取执行；退避重试经 `RETRY_WAIT` 落库，到期后再入队，避免占用有界槽位；停机停收并与 DB 对齐，启动扫描恢复。

**Tech Stack:** Java 17+、Spring Boot 3.2.5、Spring Web、Spring Data JPA、H2（文件模式）、JUnit 5、AssertJ、Awaitility（测试）；禁止 RabbitMQ/Kafka。

**Baseline:** `openspec/changes/archive/2026-11-11-rag-doc-async-ingest/{proposal,design,tasks}.md` 与同目录 `specs/*/spec.md`。

## Global Constraints

- Java 17+；Spring Framework / Spring Boot 3.x；H2 **文件持久化**；状态表与结果表分离；启动自动建表。
- 队列 MUST 自研 JDK 并发原语；MUST NOT 引入队列中间件。
- 「成功接收」= 任务已写入 `task_status`；仅写内存不算受理成功。
- 反压：内存队列满时对新业务键 fail-fast（HTTP 429 `QUEUE_FULL`）；已受理任务不丢，由补偿入队消化。
- 任务类型固定 `DOC_INGEST`；处理用 sleep/本地计算模拟，不接真实模型服务。
- 同 `business_key` 幂等返回同一 `taskId`；`FAILED/DEAD` 后本期不自动重建（无 `force`）。
- 包根：`com.demo.ragingest`；工程落在仓库根目录 Maven 标准布局。
- H2 URL **不得** 使用 `AUTO_SERVER=TRUE` 与 `DB_CLOSE_ON_EXIT=FALSE` 组合（H2 禁止）；生产配置用 `jdbc:h2:file:./data/tasks;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`。

## Traceability: archive `tasks.md` → Plan Tasks

| archive tasks.md | Plan Task | 可验证交付物 |
|------------------|-----------|--------------|
| 1.1 脚手架依赖与空应用启动 | Task 1 | `mvn package` + 启动日志 |
| 1.2 H2 文件模式 + schema | Task 2 | `TaskPersistenceTest` + `./data/tasks` |
| 1.3 分层包 + README | Task 1 + Task 9 | 包结构 + README 启动命令 |
| 2.1 双表实体与 UK | Task 2 | 唯一约束测试 |
| 2.2 提交用例幂等 | Task 4 | `TaskSubmissionServiceTest` |
| 2.3 `POST /api/tasks` | Task 4 | `TaskSubmitApiTest` |
| 3.1 有界优先队列 | Task 3 | `BoundedPriorityTaskQueueTest` |
| 3.2 入队与补偿扫描 | Task 4 + Task 5 | `EnqueueCompensator` + 不丢受理 |
| 3.3 Worker 成功路径 | Task 5 | 结果表 SUCCESS |
| 3.4 队列并发单测 | Task 3 | 多线程容量测试 |
| 4.1–4.3 失败/退避/死信 | Task 6 | `TaskRetryIntegrationTest` |
| 5.1 状态/结果/统计 API | Task 7 | `TaskQueryMetricsApiTest` |
| 5.2 指标出口 | Task 7 | `GET /api/metrics` |
| 5.3–5.4 停机恢复 | Task 8 | `GracefulShutdownIntegrationTest` |
| 6.1–6.3 文档与 E2E | Task 9 | tradeoffs / ai-dialogue / curl 清单 |

---

## File Structure

```
pom.xml
README.md
.gitignore
src/main/java/com/demo/ragingest/
  RagDocAsyncIngestApplication.java
  api/
    TaskController.java
    MetricsController.java
    dto/SubmitTaskRequest.java
    dto/SubmitTaskResponse.java
    dto/TaskStatusResponse.java
    dto/TaskResultResponse.java
    dto/TaskStatsResponse.java
    dto/MetricsResponse.java
    dto/ErrorResponse.java
    GlobalExceptionHandler.java
  application/
    TaskSubmissionService.java
    TaskQueryService.java
    EnqueueCompensator.java
    RetryScheduler.java
    GracefulShutdownManager.java
  domain/
    TaskStatus.java
    TaskType.java
    FailureKind.java
    QueuedTask.java
    RetryPolicy.java
    DocIngestSimulator.java
  infrastructure/
    config/TaskEngineProperties.java
    config/TaskEngineConfig.java
    persistence/TaskStatusEntity.java
    persistence/TaskResultEntity.java
    persistence/TaskStatusRepository.java
    persistence/TaskResultRepository.java
    queue/BoundedPriorityTaskQueue.java
    worker/TaskWorkerPool.java
    worker/TaskProcessor.java
    metrics/TaskMetrics.java
src/main/resources/
  application.yml
  schema.sql
src/test/java/com/demo/ragingest/
  infrastructure/queue/BoundedPriorityTaskQueueTest.java
  application/TaskSubmissionServiceTest.java
  application/TaskRetryIntegrationTest.java
  application/GracefulShutdownIntegrationTest.java
  api/TaskApiIntegrationTest.java
docs/tradeoffs.md
docs/ai-dialogue.md
data/                    # H2 文件目录（gitignore）
openspec/...             # 既有规划，实现时同步勾选 tasks.md
```

责任边界：`domain` 无 Spring 依赖；`infrastructure.queue` 仅调度结构；`application` 编排受理/查询/补偿/停机；`api` 只做 HTTP 适配。

---

### Task 1: Maven 脚手架与可启动空应用

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/com/demo/ragingest/RagDocAsyncIngestApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `README.md`
- Create: 空包占位（可在后续任务填类）：`api` / `application` / `domain` / `infrastructure`

**Interfaces:**
- Consumes: 无
- Produces: 可执行 Spring Boot 应用入口 `RagDocAsyncIngestApplication`

- [ ] **Step 1: 写 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
    <relativePath/>
  </parent>
  <groupId>com.demo</groupId>
  <artifactId>rag-doc-async-ingest</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>rag-doc-async-ingest</name>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写入口类、`.gitignore`、最小 `application.yml`、`README.md`**

```java
package com.demo.ragingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RagDocAsyncIngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagDocAsyncIngestApplication.class, args);
    }
}
```

```yaml
# application.yml（本任务先禁 JPA 自动 DDL，Task 2 再打开 schema）
spring:
  application:
    name: rag-doc-async-ingest
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
server:
  port: 8080
```

`.gitignore` 至少包含：`target/`、`.idea/`、`*.iml`、`data/`、`*.log`。

`README.md` 写明：`mvn spring-boot:run` 与 Java 17 要求。

- [ ] **Step 3: 验证启动**

Run: `mvn -q -DskipTests package spring-boot:run`
Expected: 日志出现 `Started RagDocAsyncIngestApplication`；用 Ctrl+C 停掉。

- [ ] **Step 4: Commit**

```bash
git add pom.xml .gitignore README.md src/main/java/com/demo/ragingest/RagDocAsyncIngestApplication.java src/main/resources/application.yml
git commit -m "chore: bootstrap Spring Boot 3 / Java 17 scaffold"
```

---

### Task 2: H2 文件库、DDL、实体与仓库

**Files:**
- Create: `src/main/resources/schema.sql`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/demo/ragingest/domain/TaskStatus.java`
- Create: `src/main/java/com/demo/ragingest/domain/TaskType.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/persistence/TaskStatusEntity.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/persistence/TaskResultEntity.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/persistence/TaskStatusRepository.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/persistence/TaskResultRepository.java`
- Test: `src/test/java/com/demo/ragingest/infrastructure/persistence/TaskPersistenceTest.java`

**Interfaces:**
- Consumes: Spring Data JPA / H2
- Produces:
  - `enum TaskStatus { QUEUED, RUNNING, RETRY_WAIT, SUCCESS, FAILED, DEAD }`
  - `enum TaskType { DOC_INGEST }`
  - `TaskStatusRepository extends JpaRepository<TaskStatusEntity, String>`
  - `Optional<TaskStatusEntity> findByBusinessKey(String businessKey)`
  - `long countByStatus(TaskStatus status)`
  - `List<TaskStatusEntity> findByStatusIn(Collection<TaskStatus> statuses)`
  - `List<TaskStatusEntity> findByStatusAndNextRunAtLessThanEqual(TaskStatus status, Instant time)`

- [ ] **Step 1: 写失败测试（仓库保存 + business_key 唯一）**

```java
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
        TaskStatusEntity a = newEntity("doc:a:1");
        statusRepo.saveAndFlush(a);
        TaskStatusEntity b = newEntity("doc:a:1");
        assertThatThrownBy(() -> statusRepo.saveAndFlush(b))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesResultLinkedToTask() {
        TaskStatusEntity t = statusRepo.saveAndFlush(newEntity("doc:b:1"));
        TaskResultEntity r = new TaskResultEntity();
        r.setTaskId(t.getId());
        r.setResultJson("{\"chunks\":3}");
        r.setCreatedAt(Instant.now());
        resultRepo.saveAndFlush(r);
        assertThat(resultRepo.findById(t.getId())).isPresent();
    }

    private static TaskStatusEntity newEntity(String key) {
        TaskStatusEntity e = new TaskStatusEntity();
        e.setId(UUID.randomUUID().toString());
        e.setBusinessKey(key);
        e.setTaskType(TaskType.DOC_INGEST);
        e.setPriority(5);
        e.setStatus(TaskStatus.QUEUED);
        e.setRetryCount(0);
        e.setPayloadJson("{\"docId\":\"x\"}");
        e.setEnqueuedAt(Instant.now());
        return e;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TaskPersistenceTest test`
Expected: FAIL（缺 schema/实体/仓库或 JPA 未启用）

- [ ] **Step 3: 写 `schema.sql`、实体、仓库，并恢复 JPA 自动配置**

```sql
CREATE TABLE IF NOT EXISTS task_status (
  id VARCHAR(36) PRIMARY KEY,
  business_key VARCHAR(255) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_run_at TIMESTAMP NULL,
  fail_reason VARCHAR(1024) NULL,
  payload_json CLOB NOT NULL,
  enqueued_at TIMESTAMP NOT NULL,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  CONSTRAINT uk_task_status_business_key UNIQUE (business_key)
);

CREATE TABLE IF NOT EXISTS task_result (
  task_id VARCHAR(36) PRIMARY KEY,
  result_json CLOB NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_task_result_task FOREIGN KEY (task_id) REFERENCES task_status(id)
);

CREATE INDEX IF NOT EXISTS idx_task_status_status ON task_status(status);
CREATE INDEX IF NOT EXISTS idx_task_status_retry ON task_status(status, next_run_at);
```

`application.yml`（替换 Task 1 的 exclude）：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/tasks;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  h2:
    console:
      enabled: true
      path: /h2-console
```

实体字段对齐 design：`TaskStatusEntity` / `TaskResultEntity` 使用 JPA 注解映射上表；`id` 为 String UUID。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TaskPersistenceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/schema.sql src/main/resources/application.yml src/main/java/com/demo/ragingest/domain src/main/java/com/demo/ragingest/infrastructure/persistence src/test/java/com/demo/ragingest/infrastructure/persistence
git commit -m "feat: add H2 file schema and task status/result persistence"
```

---

### Task 3: 有界优先队列（纯 JDK）

**Files:**
- Create: `src/main/java/com/demo/ragingest/domain/QueuedTask.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/queue/BoundedPriorityTaskQueue.java`
- Test: `src/test/java/com/demo/ragingest/infrastructure/queue/BoundedPriorityTaskQueueTest.java`

**Interfaces:**
- Consumes: 无 Spring
- Produces:
  - `record QueuedTask(String taskId, int priority, long enqueueSeq) {}`
  - `BoundedPriorityTaskQueue(int capacity)`
  - `boolean offer(QueuedTask task)` — 满则 false，不阻塞
  - `QueuedTask take() throws InterruptedException`
  - `boolean remove(String taskId)`
  - `boolean contains(String taskId)`
  - `int size()` / `int capacity()` / `List<QueuedTask> drainAll()`

- [ ] **Step 1: Write the failing test**

```java
class BoundedPriorityTaskQueueTest {
    @Test
    void higherPriorityDequeuedFirst() throws Exception {
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(8);
        assertThat(q.offer(new QueuedTask("low", 1, 1))).isTrue();
        assertThat(q.offer(new QueuedTask("high", 10, 2))).isTrue();
        assertThat(q.take().taskId()).isEqualTo("high");
        assertThat(q.take().taskId()).isEqualTo("low");
    }

    @Test
    void rejectsWhenFullAndNeverExceedsCapacity() {
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(2);
        assertThat(q.offer(new QueuedTask("a", 1, 1))).isTrue();
        assertThat(q.offer(new QueuedTask("b", 1, 2))).isTrue();
        assertThat(q.offer(new QueuedTask("c", 9, 3))).isFalse();
        assertThat(q.size()).isEqualTo(2);
        assertThat(q.capacity()).isEqualTo(2);
    }

    @Test
    void concurrentOffersNeverExceedCapacity() throws Exception {
        int capacity = 50;
        BoundedPriorityTaskQueue q = new BoundedPriorityTaskQueue(capacity);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                if (q.offer(new QueuedTask("t" + idx, idx % 5, idx))) {
                    accepted.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdownNow();
        assertThat(q.size()).isLessThanOrEqualTo(capacity);
        assertThat(accepted.get()).isEqualTo(q.size());
        assertThat(accepted.get()).isLessThanOrEqualTo(capacity);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=BoundedPriorityTaskQueueTest test`
Expected: FAIL（类不存在）

- [ ] **Step 3: Write minimal implementation**

用 `synchronized` + `PriorityQueue` + `wait/notify`（JDK 并发原语，无中间件）。满则 `offer` 返回 false；`take` 在空队列上阻塞。

```java
package com.demo.ragingest.infrastructure.queue;

import com.demo.ragingest.domain.QueuedTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class BoundedPriorityTaskQueue {
    private final int capacity;
    private int size = 0;
    private final PriorityQueue<QueuedTask> queue = new PriorityQueue<>(
        Comparator.comparingInt(QueuedTask::priority).reversed()
            .thenComparingLong(QueuedTask::enqueueSeq)
    );

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public synchronized boolean offer(QueuedTask task) {
        if (size >= capacity) return false;
        if (!queue.offer(task)) return false;
        size++;
        notifyAll();
        return true;
    }

    public synchronized QueuedTask take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        QueuedTask task = queue.poll();
        size--;
        return task;
    }

    public synchronized boolean remove(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId) && queue.remove(candidate)) {
                size--;
                return true;
            }
        }
        return false;
    }

    public synchronized List<QueuedTask> drainAll() {
        List<QueuedTask> drained = new ArrayList<>(queue);
        queue.clear();
        size = 0;
        return drained;
    }

    public synchronized boolean contains(String taskId) {
        for (QueuedTask candidate : queue) {
            if (candidate.taskId().equals(taskId)) return true;
        }
        return false;
    }

    public synchronized int size() { return size; }
    public int capacity() { return capacity; }
}
```

同优先级 FIFO 依赖单调递增的 `enqueueSeq`（由提交服务用 `AtomicLong` 分配）。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=BoundedPriorityTaskQueueTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/demo/ragingest/domain/QueuedTask.java src/main/java/com/demo/ragingest/infrastructure/queue/BoundedPriorityTaskQueue.java src/test/java/com/demo/ragingest/infrastructure/queue/BoundedPriorityTaskQueueTest.java
git commit -m "feat: add bounded priority in-memory task queue"
```

---

### Task 4: 配置、指标桩、提交用例与 POST API

**Files:**
- Create: `src/main/java/com/demo/ragingest/infrastructure/config/TaskEngineProperties.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/config/TaskEngineConfig.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/metrics/TaskMetrics.java`
- Create: `src/main/java/com/demo/ragingest/application/TaskSubmissionService.java`
- Create: `src/main/java/com/demo/ragingest/application/AcceptingGate.java`
- Create: `src/main/java/com/demo/ragingest/api/dto/*.java`（本任务所需 DTO）
- Create: `src/main/java/com/demo/ragingest/api/TaskController.java`（仅 POST）
- Create: `src/main/java/com/demo/ragingest/api/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.yml`（增加 `task.engine.*`）
- Test: `src/test/java/com/demo/ragingest/application/TaskSubmissionServiceTest.java`
- Test: `src/test/java/com/demo/ragingest/api/TaskSubmitApiTest.java`

**Interfaces:**
- Consumes: `TaskStatusRepository`, `BoundedPriorityTaskQueue`, `TaskMetrics`
- Produces:
  - `SubmitResult submit(SubmitCommand cmd)` where `record SubmitCommand(String businessKey, String docId, String fileName, int priority, Double failProbability, Boolean fatal)`
  - `record SubmitResult(String taskId, boolean admittedToMemory, boolean idempotentReplay)`
  - `AcceptingGate.isAccepting()` / `stopAccepting()`
  - `POST /api/tasks` → 200 `{taskId, admittedToMemory, idempotentReplay}`；满队列新键 → 429 `{code:QUEUE_FULL}`；停机 → 503

- [ ] **Step 1: Write failing unit tests for submission semantics**

```java
@ExtendWith(MockitoExtension.class)
class TaskSubmissionServiceTest {
    @Mock TaskStatusRepository statusRepo;
    @Mock BoundedPriorityTaskQueue queue;
    @Mock TaskMetrics metrics;
    @Mock AcceptingGate gate;
    AtomicLong seq = new AtomicLong();
    TaskSubmissionService service;

    @BeforeEach
    void setUp() {
        when(gate.isAccepting()).thenReturn(true);
        service = new TaskSubmissionService(statusRepo, queue, metrics, gate, seq);
    }

    @Test
    void persistsBeforeReturningAndOffersToQueue() {
        when(statusRepo.findByBusinessKey("doc:1:v1")).thenReturn(Optional.empty());
        when(statusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queue.size()).thenReturn(0);
        when(queue.capacity()).thenReturn(10);
        when(queue.offer(any())).thenReturn(true);

        SubmitResult r = service.submit(new SubmitCommand("doc:1:v1", "1", "a.pdf", 5, 0.0, false));
        assertThat(r.taskId()).isNotBlank();
        assertThat(r.admittedToMemory()).isTrue();
        verify(statusRepo).save(argThat(e -> e.getStatus() == TaskStatus.QUEUED));
        verify(queue).offer(argThat(t -> t.taskId().equals(r.taskId())));
    }

    @Test
    void duplicateBusinessKeyReturnsSameIdWithoutSecondSave() {
        TaskStatusEntity existing = new TaskStatusEntity();
        existing.setId("fixed-id");
        existing.setBusinessKey("doc:1:v1");
        when(statusRepo.findByBusinessKey("doc:1:v1")).thenReturn(Optional.of(existing));

        SubmitResult r = service.submit(new SubmitCommand("doc:1:v1", "1", "a.pdf", 5, null, null));
        assertThat(r.taskId()).isEqualTo("fixed-id");
        assertThat(r.idempotentReplay()).isTrue();
        verify(statusRepo, never()).save(any());
    }

    @Test
    void newKeyWhenQueueFullThrowsQueueFull() {
        when(statusRepo.findByBusinessKey("doc:2:v1")).thenReturn(Optional.empty());
        when(queue.size()).thenReturn(10);
        when(queue.capacity()).thenReturn(10);
        assertThatThrownBy(() -> service.submit(new SubmitCommand("doc:2:v1", "2", "b.pdf", 1, null, null)))
            .isInstanceOf(QueueFullException.class);
        verify(statusRepo, never()).save(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TaskSubmissionServiceTest test`
Expected: FAIL

- [ ] **Step 3: Implement properties, metrics stub, gate, service, API**

`application.yml` 追加：

```yaml
task:
  engine:
    worker-threads: 4
    queue-factor: 20
    # capacity = worker-threads * queue-factor （可被 queue-capacity 覆盖）
    queue-capacity: 0
    max-retries: 3
    retry-base-ms: 200
    retry-max-ms: 5000
    process-sleep-ms: 100
    default-fail-probability: 0.1
    shutdown-timeout-seconds: 10
    compensator-fixed-delay-ms: 500
    retry-scheduler-fixed-delay-ms: 200
```

`TaskEngineConfig`：

```java
@Bean
BoundedPriorityTaskQueue boundedPriorityTaskQueue(TaskEngineProperties p) {
    int capacity = p.resolvedQueueCapacity(); // queueCapacity>0 ? that : threads*factor
    return new BoundedPriorityTaskQueue(capacity);
}
```

`TaskSubmissionService.submit` 逻辑顺序：

1. 若 `!gate.isAccepting()` → `NotAcceptingException`
2. `findByBusinessKey` 命中 → 返回原 id（幂等）
3. 若 `queue.size() >= capacity` → `QueueFullException`（**不写 DB**，反压新流量）
4. 构造实体 `QUEUED`，`save`
5. `offer(QueuedTask)`；若 false（竞态），仍返回 taskId 且 `admittedToMemory=false`（补偿器稍后入队）
6. 返回 `SubmitResult`

`POST /api/tasks` body：

```json
{
  "businessKey": "doc:handbook:v1",
  "docId": "handbook",
  "fileName": "handbook.pdf",
  "priority": 10,
  "failProbability": 0.0,
  "fatal": false
}
```

- [ ] **Step 4: API 集成测试**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class TaskSubmitApiTest {
    @Autowired MockMvc mvc;
    @Autowired TaskStatusRepository repo;

    @Test
    void submitReturnsTaskIdAndRowExists() throws Exception {
        String body = """
            {"businessKey":"doc:api:1","docId":"api","fileName":"a.pdf","priority":3,"failProbability":0.0}
            """;
        String json = mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.taskId");
        assertThat(repo.findById(id)).isPresent();
    }
}
```

（若不想引 json-path 依赖，可用 ObjectMapper 解析。）

Run: `mvn -q -Dtest=TaskSubmissionServiceTest,TaskSubmitApiTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/demo/ragingest src/main/resources/application.yml src/test/java/com/demo/ragingest
git commit -m "feat: add durable task submission with idempotency and queue backpressure"
```

---

### Task 5: Worker 模拟处理成功路径

**Files:**
- Create: `src/main/java/com/demo/ragingest/domain/FailureKind.java`
- Create: `src/main/java/com/demo/ragingest/domain/DocIngestSimulator.java`
- Create: `src/main/java/com/demo/ragingest/domain/RetryPolicy.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/worker/TaskProcessor.java`
- Create: `src/main/java/com/demo/ragingest/infrastructure/worker/TaskWorkerPool.java`
- Modify: `TaskEngineConfig.java`（启动 Worker）
- Test: `src/test/java/com/demo/ragingest/infrastructure/worker/TaskProcessorSuccessTest.java`
- Test: `src/test/java/com/demo/ragingest/api/TaskHappyPathIT.java`

**Interfaces:**
- Consumes: queue.take、repositories、`DocIngestSimulator`
- Produces:
  - `DocIngestSimulator.Result simulate(TaskStatusEntity task)` → success 时 `{chunks, vectors, elapsedMs}`，失败抛 `RetryableTaskException` / `FatalTaskException`
  - `TaskProcessor.process(QueuedTask qt)`
  - `TaskWorkerPool.start()` / `shutdown(timeout)`

- [ ] **Step 1: Write failing success-path test**

```java
@SpringBootTest
class TaskHappyPathIT {
    @Autowired TaskSubmissionService submission;
    @Autowired TaskStatusRepository statusRepo;
    @Autowired TaskResultRepository resultRepo;

    @Test
    void submittedTaskBecomesSuccessWithResult() throws Exception {
        SubmitResult r = submission.submit(new SubmitCommand(
            "doc:ok:1", "ok", "ok.pdf", 8, 0.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = statusRepo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(resultRepo.findById(r.taskId())).isPresent();
        });
    }
}
```

加测试依赖 Awaitility（`pom.xml`）：

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TaskHappyPathIT test`
Expected: FAIL（无 Worker / 超时）

- [ ] **Step 3: Implement simulator + processor + worker pool**

`DocIngestSimulator`：

```java
public Result simulate(TaskStatusEntity task, TaskEngineProperties props) {
    long sleep = props.getProcessSleepMs();
    Thread.sleep(sleep);
    // 本地计算：hash payload 若干次模拟 CPU 片段
    int sink = Objects.hash(task.getPayloadJson(), sleep);
    for (int i = 0; i < 10_000; i++) sink = Objects.hash(sink, i);

    JsonNode payload = parse(task.getPayloadJson());
    if (payload.path("fatal").asBoolean(false)) {
        throw new FatalTaskException("fatal flag set");
    }
    double p = payload.path("failProbability").asDouble(props.getDefaultFailProbability());
    if (ThreadLocalRandom.current().nextDouble() < p) {
        throw new RetryableTaskException("simulated parse/embed failure");
    }
    return new Result(3 + Math.abs(sink % 5), 12 + Math.abs(sink % 9), sleep);
}
```

`TaskProcessor` 成功分支：

1. 加载实体；若非 `QUEUED/RETRY_WAIT` 则跳过
2. 置 `RUNNING` + `startedAt`
3. 调 simulator
4. 写 `task_result`，状态 `SUCCESS`，`finishedAt`，metrics 成功计数与处理时延

`TaskWorkerPool`：创建 `workerThreads` 个守护线程，循环：

```java
while (running) {
  QueuedTask qt = queue.take(); // 可中断
  metrics.incActiveWorkers();
  try { processor.process(qt); }
  finally { metrics.decActiveWorkers(); }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q -Dtest=TaskHappyPathIT,TaskProcessorSuccessTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/demo/ragingest src/test/java/com/demo/ragingest
git commit -m "feat: add worker pool and DOC_INGEST simulation success path"
```

---

### Task 6: 失败分类、指数退避重试与死信

**Files:**
- Create: `src/main/java/com/demo/ragingest/domain/RetryableTaskException.java`
- Create: `src/main/java/com/demo/ragingest/domain/FatalTaskException.java`
- Create: `src/main/java/com/demo/ragingest/application/RetryScheduler.java`
- Create: `src/main/java/com/demo/ragingest/application/EnqueueCompensator.java`
- Modify: `TaskProcessor.java`（失败分支）
- Modify: `RetryPolicy.java`
- Test: `src/test/java/com/demo/ragingest/domain/RetryPolicyTest.java`
- Test: `src/test/java/com/demo/ragingest/application/TaskRetryIntegrationTest.java`

**Interfaces:**
- Consumes: `TaskStatusRepository`, queue, metrics
- Produces:
  - `RetryPolicy.nextDelay(attempt, baseMs, maxMs)` → `baseMs * 2^attempt` capped
  - Fatal → `FAILED`，不再重试
  - Retryable 且 `retryCount < maxRetries` → `RETRY_WAIT` + `nextRunAt`，**不**占队列槽
  - 超限 → `DEAD`，记录原因
  - `RetryScheduler` 扫描 `RETRY_WAIT && nextRunAt<=now` → 改 `QUEUED` 并 `offer`
  - `EnqueueCompensator` 扫描 `QUEUED` 不在内存中的任务并 `offer`

- [ ] **Step 1: Write failing tests**

```java
class RetryPolicyTest {
    @Test
    void exponentialBackoffCaps() {
        RetryPolicy p = new RetryPolicy();
        assertThat(p.nextDelayMs(0, 200, 5000)).isEqualTo(200);
        assertThat(p.nextDelayMs(1, 200, 5000)).isEqualTo(400);
        assertThat(p.nextDelayMs(2, 200, 5000)).isEqualTo(800);
        assertThat(p.nextDelayMs(10, 200, 5000)).isEqualTo(5000);
    }
}

@SpringBootTest
@TestPropertySource(properties = {
    "task.engine.process-sleep-ms=10",
    "task.engine.max-retries=2",
    "task.engine.retry-base-ms=50",
    "task.engine.retry-max-ms=200"
})
class TaskRetryIntegrationTest {
    @Autowired TaskSubmissionService submission;
    @Autowired TaskStatusRepository repo;

    @Test
    void fatalGoesToFailedWithoutRetry() {
        SubmitResult r = submission.submit(new SubmitCommand(
            "doc:fatal:1", "f", "f.pdf", 1, 0.0, true));
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(e.getRetryCount()).isZero();
            assertThat(e.getFailReason()).isNotBlank();
        });
    }

    @Test
    void retryableExhaustionGoesDead() {
        SubmitResult r = submission.submit(new SubmitCommand(
            "doc:retry:1", "r", "r.pdf", 1, 1.0, false));
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TaskStatusEntity e = repo.findById(r.taskId()).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.DEAD);
            assertThat(e.getRetryCount()).isGreaterThanOrEqualTo(2);
            assertThat(e.getFailReason()).isNotBlank();
        });
    }

    @Test
    void retryWaitDoesNotOccupyQueueSlot() throws Exception {
        // 提交必失败任务后短暂断言：进入 RETRY_WAIT 时 queue.size 可不包含该任务
        // 实现时：注入 BoundedPriorityTaskQueue，等待状态=RETRY_WAIT 后 assert size 不含该 id
    }
}
```

把第三个测试写完整：提交 `failProbability=1.0`，await `RETRY_WAIT`，`assertThat(queue.size()).isLessThan(capacity)` 且 drain/peek 不含该 taskId（可为 queue 增加 `contains(taskId)` 仅测试可见，或 `drainAll` 后重新 offer——生产代码用 `boolean contains(String taskId)` 基于同步快照）。

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=RetryPolicyTest,TaskRetryIntegrationTest test`
Expected: FAIL

- [ ] **Step 3: Implement failure branches + schedulers**

`TaskProcessor` catch：

```java
catch (FatalTaskException ex) {
  entity.setStatus(TaskStatus.FAILED);
  entity.setFailReason(ex.getMessage());
  entity.setFinishedAt(Instant.now());
  metrics.incFailed();
} catch (RetryableTaskException ex) {
  int next = entity.getRetryCount() + 1;
  entity.setRetryCount(next);
  entity.setFailReason(ex.getMessage());
  metrics.incRetry();
  if (next > props.getMaxRetries()) {
    entity.setStatus(TaskStatus.DEAD);
    entity.setFinishedAt(Instant.now());
    metrics.incFailed();
  } else {
    entity.setStatus(TaskStatus.RETRY_WAIT);
    entity.setNextRunAt(Instant.now().plusMillis(
        retryPolicy.nextDelayMs(next - 1, props.getRetryBaseMs(), props.getRetryMaxMs())));
  }
}
// 注意：失败/退避路径不要把任务重新 offer 回队列
```

`@Scheduled` 启用 `@EnableScheduling`：

- `RetryScheduler`：到期 `RETRY_WAIT` → `QUEUED` + `offer`（满则保留 QUEUED 等补偿）
- `EnqueueCompensator`：`QUEUED` 且 `!queue.contains(id)` → `offer`

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=RetryPolicyTest,TaskRetryIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/demo/ragingest src/test/java/com/demo/ragingest
git commit -m "feat: add classified failures, backoff retry, and dead-letter terminal state"
```

---

### Task 7: 查询、统计与指标 API

**Files:**
- Create: `src/main/java/com/demo/ragingest/application/TaskQueryService.java`
- Modify: `src/main/java/com/demo/ragingest/api/TaskController.java`（GET 状态/结果/统计）
- Create: `src/main/java/com/demo/ragingest/api/MetricsController.java`
- Create: 剩余 dto
- Enhance: `TaskMetrics.java`（排队时延：`enqueuedAt`→`startedAt`；处理时延：`startedAt`→`finishedAt`）
- Test: `src/test/java/com/demo/ragingest/api/TaskQueryMetricsApiTest.java`

**Interfaces:**
- Consumes: repositories, queue, worker pool metrics
- Produces:
  - `GET /api/tasks/{id}` → status, retryCount, failReason, priority, timestamps
  - `GET /api/tasks/{id}/result` → resultJson（仅 SUCCESS，否则 404）
  - `GET /api/tasks/stats` → `{queued, running, failed}` 其中 failed=`FAILED+DEAD`，queued=`QUEUED+RETRY_WAIT` 积压口径在响应字段注释清楚：`backlog`=`QUEUED+RETRY_WAIT`，`processing`=`RUNNING`，`failed`=`FAILED+DEAD`
  - `GET /api/metrics` → queueSize, queueCapacity, activeThreads, successCount, failureCount, retryCount, avgQueueMs, avgProcessMs

- [ ] **Step 1: Write failing API tests**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class TaskQueryMetricsApiTest {
    @Autowired MockMvc mvc;
    @Autowired TaskSubmissionService submission;

    @Test
    void queryStatsAndMetricsFieldsPresent() throws Exception {
        submission.submit(new SubmitCommand("doc:q:1", "q", "q.pdf", 2, 0.0, false));
        mvc.perform(get("/api/tasks/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.backlog").exists())
            .andExpect(jsonPath("$.processing").exists())
            .andExpect(jsonPath("$.failed").exists());
        mvc.perform(get("/api/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueSize").exists())
            .andExpect(jsonPath("$.queueCapacity").exists())
            .andExpect(jsonPath("$.activeThreads").exists())
            .andExpect(jsonPath("$.successCount").exists())
            .andExpect(jsonPath("$.failureCount").exists())
            .andExpect(jsonPath("$.retryCount").exists())
            .andExpect(jsonPath("$.avgQueueMs").exists())
            .andExpect(jsonPath("$.avgProcessMs").exists());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TaskQueryMetricsApiTest test`
Expected: FAIL

- [ ] **Step 3: Implement query service + controllers；在 processor 更新 metrics 直方图/累计**

`TaskMetrics` 用 `LongAdder` 计数 + `LongAdder` 累计时延毫秒与样本数计算平均值。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TaskQueryMetricsApiTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/demo/ragingest src/test/java/com/demo/ragingest
git commit -m "feat: add task query, stats, and runtime metrics endpoints"
```

---

### Task 8: 优雅停机与重启恢复

**Files:**
- Create: `src/main/java/com/demo/ragingest/application/GracefulShutdownManager.java`
- Create: `src/main/java/com/demo/ragingest/application/StartupRecovery.java`
- Modify: `AcceptingGate`, `TaskWorkerPool`
- Test: `src/test/java/com/demo/ragingest/application/GracefulShutdownIntegrationTest.java`

**Interfaces:**
- Consumes: gate, queue, worker pool, repositories
- Produces:
  - 停机：`stopAccepting` → `drainAll`（任务已在 DB 为 QUEUED/RUNNING 处理）→ RUNNING 超时回收为 QUEUED → worker shutdown
  - 启动：`StartupRecovery` 将 `RUNNING` 重置为 `QUEUED`，将 `QUEUED`/`到期 RETRY_WAIT` offer 进队列

- [ ] **Step 1: Write failing recovery test**

```java
@SpringBootTest
class GracefulShutdownIntegrationTest {
    @Autowired TaskSubmissionService submission;
    @Autowired BoundedPriorityTaskQueue queue;
    @Autowired GracefulShutdownManager shutdownManager;
    @Autowired StartupRecovery recovery;
    @Autowired TaskStatusRepository repo;
    @Autowired AcceptingGate gate;

    @Test
    void shutdownStopsAcceptAndStartupRequeuesPersistedWork() {
        // 临时停 worker：通过把 process-sleep 调大或直接测 shutdown API 组件
        SubmitResult r = submission.submit(new SubmitCommand(
            "doc:sd:1", "sd", "sd.pdf", 9, 0.0, false));
        // 确保仍在 QUEUED（可用 spy 或极慢 sleep 配置的专用 test props）
        shutdownManager.shutdown();
        assertThat(gate.isAccepting()).isFalse();
        assertThatThrownBy(() -> submission.submit(new SubmitCommand(
            "doc:sd:2", "sd2", "x.pdf", 1, 0.0, false)))
            .isInstanceOf(NotAcceptingException.class);

        // 模拟重启：清空内存队列后 recovery.run()
        queue.drainAll();
        gate.resetAcceptingForTest(); // 仅测试可见包内方法，或新 Spring 上下文
        recovery.recover();
        assertThat(repo.findById(r.taskId())).isPresent();
        // 恢复后应能再次被调度：await SUCCESS 或 queue.contains
    }
}
```

测试属性建议：`task.engine.process-sleep-ms=5000`、`worker-threads=1`，以便停机前任务仍排队。

停机细节（实现必须遵守）：

1. `accepting=false`
2. `List<QueuedTask> left = queue.drainAll()` — 这些任务 DB 仍为 `QUEUED`（或处理中单独处理）
3. 将卡在 `RUNNING` 超过阈值的任务改回 `QUEUED`（停机时一律 `RUNNING→QUEUED`）
4. `workerPool.shutdown(timeout)`
5. 不把内存当真相；无需“回写丢失的 payload”，只需状态一致

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=GracefulShutdownIntegrationTest test`
Expected: FAIL

- [ ] **Step 3: Implement shutdown + recovery**

`@PreDestroy` / `SmartLifecycle` 调用 `GracefulShutdownManager.shutdown()`。

`StartupRecovery` 实现 `ApplicationRunner`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=GracefulShutdownIntegrationTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/demo/ragingest src/test/java/com/demo/ragingest
git commit -m "feat: add graceful shutdown and startup task recovery"
```

---

### Task 9: 权衡文档、AI 对话留存与端到端验收

**Files:**
- Create: `docs/tradeoffs.md`
- Create: `docs/ai-dialogue.md`
- Modify: `README.md`（API 示例、容量公式、验收步骤）
- Modify: `openspec/changes/rag-doc-async-ingest/tasks.md`（将对应项勾选为完成——在实现过程中由执行者更新）

**Interfaces:**
- Consumes: 已实现配置键与 API
- Produces: 可提交考核的文档与手工验收记录

- [ ] **Step 1: 写 `docs/tradeoffs.md`（必须覆盖）**

内容至少包含：

1. 容量：`queueCapacity = workerThreads × queueFactor`（默认 4×20=80），以及为何 IO 型用该系数。
2. 反压：新业务键在内存满时 429；已持久化任务由补偿器入队；对比阻塞提交的危害。
3. 不丢失边界：成功接收=DB；崩溃窗口至多「至少一次」；`RUNNING` 回收策略。

- [ ] **Step 2: 写 `docs/ai-dialogue.md`**

粘贴本对话中与需求分析、OpenSpec propose、本 plan 相关的关键摘录（中文即可）。

- [ ] **Step 3: 更新 README 验收清单并手工跑通**

```bash
mvn test
mvn spring-boot:run
# 另开终端：
curl -s -X POST localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"businessKey\":\"doc:e2e:low\",\"docId\":\"e2e\",\"fileName\":\"a.pdf\",\"priority\":1,\"failProbability\":0}"
curl -s -X POST localhost:8080/api/tasks -H "Content-Type: application/json" -d "{\"businessKey\":\"doc:e2e:high\",\"docId\":\"e2e2\",\"fileName\":\"b.pdf\",\"priority\":100,\"failProbability\":0}"
curl -s localhost:8080/api/tasks/stats
curl -s localhost:8080/api/metrics
# 打满队列：将 queue-capacity 临时改为 2 后重启，连续 POST 3 个新 businessKey，第 3 个期望 429
# failProbability=1.0 至 DEAD；停进程再启动，确认未完成任务继续
```

Expected: 上述行为与 specs 一致。

- [ ] **Step 4: Commit**

```bash
git add docs/tradeoffs.md docs/ai-dialogue.md README.md
git commit -m "docs: add capacity/backpressure/no-loss tradeoffs and AI dialogue notes"
```

---

## Self-Review

**1. Spec coverage**

| Spec requirement | Task |
|------------------|------|
| DOC_INGEST 场景建模 / 输入输出 | Task 4 DTO + Task 5 simulator + README |
| 快速返回 taskId、不阻塞处理 | Task 4 |
| 成功接收=DB 持久化 | Task 2 + Task 4 |
| business_key 幂等 | Task 4 |
| 自研有界优先队列、无中间件 | Task 3 |
| 容量可配 + 公式 | Task 4 properties + Task 9 docs |
| 优先级调度 | Task 3 |
| 反压满队列 | Task 4 |
| Worker 模拟处理 | Task 5 |
| 失败分类 / 重试 / 死信 / 退避不占槽 | Task 6 |
| 状态/结果/统计/指标 | Task 7 |
| 优雅停机与重启恢复 | Task 8 |
| 权衡文档、AI 对话、可运行交付 | Task 9 |
| openspec tasks.md 分组 1–6 | 分别映射 Task 1–9 |

**2. Placeholder scan:** 无 TBD/TODO；测试与实现步骤含具体代码与命令。

**3. Type consistency:** 状态枚举统一 `QUEUED/RUNNING/RETRY_WAIT/SUCCESS/FAILED/DEAD`；提交命令 `SubmitCommand` / `SubmitResult`；队列元素 `QueuedTask(taskId, priority, enqueueSeq)` 贯穿 Task 3–8。

---

## Mapping from OpenSpec `tasks.md`

（完整追溯见文首 **Traceability** 表。）

| openspec tasks.md | Plan tasks |
|-------------------|------------|
| 1.1–1.3 scaffolding | Task 1 |
| 2.1–2.3 persistence + submit | Task 2, 4 |
| 3.1–3.4 queue + workers | Task 3, 5 (+ 4 入队) |
| 4.1–4.3 retry/DLQ | Task 6 |
| 5.1–5.4 query/metrics/shutdown | Task 7, 8 |
| 6.1–6.3 docs/E2E | Task 9 |
