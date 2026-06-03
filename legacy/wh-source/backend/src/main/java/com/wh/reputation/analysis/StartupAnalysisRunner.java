package com.wh.reputation.analysis;

import com.wh.reputation.persistence.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
@Order(3)
public class StartupAnalysisRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupAnalysisRunner.class);

    private final ReviewRepository reviewRepository;
    private final ReviewAnalysisService reviewAnalysisService;

    public StartupAnalysisRunner(ReviewRepository reviewRepository, ReviewAnalysisService reviewAnalysisService) {
        this.reviewRepository = reviewRepository;
        this.reviewAnalysisService = reviewAnalysisService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> productIds = reviewRepository.findProductIdsWithMissingTokens();
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        for (Long productId : new LinkedHashSet<>(productIds)) {
            if (productId == null) {
                continue;
            }
            try {
                reviewAnalysisService.analyzeByProduct(productId, null, null);
                log.info("启动分析完成：产品编号={}", productId);
            } catch (Exception e) {
                log.warn("启动分析失败：产品编号={}", productId, e);
            }
        }
    }
}
