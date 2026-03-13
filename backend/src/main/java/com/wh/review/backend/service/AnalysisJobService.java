package com.wh.review.backend.service;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobService {

    private static final String STATUS_QUEUED = "QUEUED";

    private final AnalysisJobRepository analysisJobRepository;

    public AnalysisJobService(AnalysisJobRepository analysisJobRepository) {
        this.analysisJobRepository = analysisJobRepository;
    }

    public AnalysisJobResponse createJob(String productCode) {
        return analysisJobRepository.create(productCode, STATUS_QUEUED, Instant.now());
    }

    public Optional<AnalysisJobResponse> findJob(String jobId) {
        return analysisJobRepository.findById(jobId);
    }
}
