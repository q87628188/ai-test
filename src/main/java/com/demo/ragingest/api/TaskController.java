package com.demo.ragingest.api;

import com.demo.ragingest.api.dto.SubmitTaskRequest;
import com.demo.ragingest.api.dto.SubmitTaskResponse;
import com.demo.ragingest.api.dto.TaskResultResponse;
import com.demo.ragingest.api.dto.TaskStatsResponse;
import com.demo.ragingest.api.dto.TaskStatusResponse;
import com.demo.ragingest.application.SubmitCommand;
import com.demo.ragingest.application.SubmitResult;
import com.demo.ragingest.application.TaskQueryService;
import com.demo.ragingest.application.TaskSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 任务 HTTP API：提交 / 查状态 / 查结果 / 统计。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskSubmissionService submissionService;
    private final TaskQueryService queryService;

    public TaskController(TaskSubmissionService submissionService, TaskQueryService queryService) {
        this.submissionService = submissionService;
        this.queryService = queryService;
    }

    /** 提交单条风险事件；同 businessKey 幂等返回原 taskId。 */
    @PostMapping
    public SubmitTaskResponse submit(@Valid @RequestBody SubmitTaskRequest request) {
        SubmitResult result = submissionService.submit(new SubmitCommand(
            request.getBusinessKey(),
            request.getUserId(),
            request.getEventTime(),
            request.getDeviceId(),
            request.getClientIp(),
            request.getTargetSystem(),
            request.getAction(),
            request.getPriority(),
            request.getFailProbability(),
            request.getFatal()
        ));
        return new SubmitTaskResponse(result.taskId(), result.admittedToMemory(), result.idempotentReplay());
    }

    @GetMapping("/{id}")
    public TaskStatusResponse status(@PathVariable("id") String id) {
        return queryService.getStatus(id);
    }

    @GetMapping("/{id}/result")
    public TaskResultResponse result(@PathVariable("id") String id) {
        return queryService.getResult(id);
    }

    @GetMapping("/stats")
    public TaskStatsResponse stats() {
        return queryService.stats();
    }
}
