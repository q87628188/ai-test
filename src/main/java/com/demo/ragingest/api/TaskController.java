package com.demo.ragingest.api;

import com.demo.ragingest.api.dto.SubmitTaskRequest;
import com.demo.ragingest.api.dto.SubmitTaskResponse;
import com.demo.ragingest.application.SubmitCommand;
import com.demo.ragingest.application.SubmitResult;
import com.demo.ragingest.application.TaskSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskSubmissionService submissionService;

    public TaskController(TaskSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public SubmitTaskResponse submit(@Valid @RequestBody SubmitTaskRequest request) {
        SubmitResult result = submissionService.submit(new SubmitCommand(
            request.getBusinessKey(),
            request.getDocId(),
            request.getFileName(),
            request.getPriority(),
            request.getFailProbability(),
            request.getFatal()
        ));
        return new SubmitTaskResponse(result.taskId(), result.admittedToMemory(), result.idempotentReplay());
    }
}
