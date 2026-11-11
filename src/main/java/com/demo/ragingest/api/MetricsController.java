package com.demo.ragingest.api;

import com.demo.ragingest.api.dto.MetricsResponse;
import com.demo.ragingest.application.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final TaskQueryService queryService;

    public MetricsController(TaskQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public MetricsResponse metrics() {
        return queryService.metrics();
    }
}
