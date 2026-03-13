package com.wh.review.backend.controller;

import com.wh.review.backend.dto.SyncJobResponse;
import com.wh.review.backend.dto.SyncStartRequest;
import com.wh.review.backend.service.SyncJobService;
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
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncJobService syncJobService;

    public SyncController(SyncJobService syncJobService) {
        this.syncJobService = syncJobService;
    }

    @PostMapping("/start")
    public ResponseEntity<SyncJobResponse> start(@Valid @RequestBody SyncStartRequest request) {
        SyncJobResponse job = syncJobService.createJob(
                request.provider(),
                request.platform(),
                request.targetProductCode()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> job(@PathVariable("id") String id) {
        return syncJobService.findJob(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "sync job not found", "jobId", id)));
    }
}
