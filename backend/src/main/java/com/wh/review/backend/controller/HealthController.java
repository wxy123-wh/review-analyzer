package com.wh.review.backend.controller;

import com.wh.review.backend.dto.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now());
    }
}
