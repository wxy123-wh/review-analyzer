package com.wh.review.backend.controller;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.dto.AnalysisStartRequest;
import com.wh.review.backend.service.AnalysisJobService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisJobService analysisJobService;

    public AnalysisController(AnalysisJobService analysisJobService) {
        this.analysisJobService = analysisJobService;
    }

    @PostMapping("/start")
    public ResponseEntity<AnalysisJobResponse> start(@Valid @RequestBody AnalysisStartRequest request) {
        AnalysisJobResponse response = analysisJobService.createJob(request.productCode());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> job(@PathVariable("id") String id) {
        return analysisJobService.findJob(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "analysis job not found", "jobId", id)));
    }
}
