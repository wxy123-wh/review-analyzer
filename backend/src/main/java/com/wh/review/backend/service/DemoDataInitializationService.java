package com.wh.review.backend.service;

import com.wh.review.backend.dto.DemoDataInitResponse;
import com.wh.review.backend.persistence.DemoReviewSeedRepository;
import com.wh.review.backend.persistence.DemoReviewSeedRepository.DemoReviewSeedItem;
import com.wh.review.backend.persistence.DemoReviewSeedRepository.SeedResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DemoDataInitializationService {

    static final String SEED_KEY = "demo-comments";
    static final String SOURCE = "demo-seed";
    static final String DATA_VERSION = "demo-comments-v1";
    static final String DEFAULT_PRODUCT_CODE = "demo-earphone";
    static final int TARGET_REVIEW_COUNT = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializationService.class);
    private static final Instant BASE_REVIEW_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<String> ASPECTS = List.of("battery", "bluetooth", "noise-canceling", "comfort", "microphone");
    private static final List<String> POSITIVE_TEMPLATES = List.of(
            "连接稳定，连续使用时%s表现比预期更好。",
            "在通勤场景下，%s维持得不错，几乎不需要额外调整。",
            "固件更新后%s明显提升，日常体验顺滑。",
            "视频会议连续1小时，%s依旧在线。",
            "同价位对比里，%s属于第一梯队。"
    );
    private static final List<String> NEUTRAL_TEMPLATES = List.of(
            "整体可用，但%s仍有优化空间。",
            "%s表现中规中矩，和宣传基本一致。",
            "短时体验尚可，长时间使用时%s波动会出现。",
            "日常使用没有大问题，但%s还不够惊喜。",
            "在安静环境下%s正常，复杂场景下偶有变化。"
    );
    private static final List<String> NEGATIVE_TEMPLATES = List.of(
            "高峰通勤时%s容易波动，需要手动恢复。",
            "连续使用后%s衰减明显，体验被打断。",
            "运动场景里%s稳定性不足，偶发失真。",
            "多人语音时%s偏弱，影响沟通效率。",
            "两周后%s下降比预期快，希望尽快修复。"
    );

    private final DemoReviewSeedRepository demoReviewSeedRepository;

    public DemoDataInitializationService(DemoReviewSeedRepository demoReviewSeedRepository) {
        this.demoReviewSeedRepository = demoReviewSeedRepository;
    }

    public DemoDataInitResponse initializeComments(String requestedProductCode) {
        String productCode = normalizeProductCode(requestedProductCode);
        long startNanos = System.nanoTime();
        List<DemoReviewSeedItem> reviews = buildDemoReviews(productCode);
        SeedResult seedResult = demoReviewSeedRepository.upsertDemoReviews(
                SEED_KEY,
                SOURCE,
                productCode,
                DATA_VERSION,
                reviews
        );
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        Instant initializedAt = Instant.now();

        LOGGER.info(
                "demo data initialized: seedKey={}, productCode={}, productId={}, version={}, targetCount={}, insertedCount={}, updatedCount={}, totalCount={}, durationMs={}",
                SEED_KEY,
                seedResult.productCode(),
                seedResult.productId(),
                DATA_VERSION,
                TARGET_REVIEW_COUNT,
                seedResult.insertedCount(),
                seedResult.updatedCount(),
                seedResult.totalCount(),
                durationMs
        );

        return new DemoDataInitResponse(
                SEED_KEY,
                seedResult.productCode(),
                DATA_VERSION,
                TARGET_REVIEW_COUNT,
                seedResult.insertedCount(),
                seedResult.updatedCount(),
                seedResult.totalCount(),
                durationMs,
                initializedAt
        );
    }

    private List<DemoReviewSeedItem> buildDemoReviews(String productCode) {
        List<DemoReviewSeedItem> reviews = new ArrayList<>(TARGET_REVIEW_COUNT);
        for (int i = 0; i < TARGET_REVIEW_COUNT; i++) {
            String aspect = ASPECTS.get(i % ASPECTS.size());
            BigDecimal rating = ratingForIndex(i);
            String content = buildContent(i, aspect, rating);
            Instant reviewTime = BASE_REVIEW_TIME.plus(i * 6L, ChronoUnit.HOURS);
            String sourceReviewId = productCode + "-" + String.format("%03d", i + 1);
            String authorId = "demo-user-" + String.format("%03d", (i % 37) + 1);

            reviews.add(new DemoReviewSeedItem(
                    sourceReviewId,
                    rating,
                    content,
                    reviewTime,
                    authorId
            ));
        }
        return reviews;
    }

    private String buildContent(int index, String aspect, BigDecimal rating) {
        List<String> templates;
        if (rating.compareTo(new BigDecimal("2.5")) <= 0) {
            templates = NEGATIVE_TEMPLATES;
        } else if (rating.compareTo(new BigDecimal("3.5")) <= 0) {
            templates = NEUTRAL_TEMPLATES;
        } else {
            templates = POSITIVE_TEMPLATES;
        }
        String template = templates.get(index % templates.size());
        return "批次#" + String.format("%02d", (index % 16) + 1) + " " + String.format(template, aspect);
    }

    private BigDecimal ratingForIndex(int index) {
        int mod = index % 10;
        if (mod < 4) {
            return new BigDecimal("2.0");
        }
        if (mod < 7) {
            return new BigDecimal("3.0");
        }
        return new BigDecimal("4.5");
    }

    private String normalizeProductCode(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return DEFAULT_PRODUCT_CODE;
        }
        return productCode.trim();
    }
}
