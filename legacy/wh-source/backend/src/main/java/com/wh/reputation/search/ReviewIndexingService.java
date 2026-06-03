package com.wh.reputation.search;

import com.wh.reputation.persistence.ReviewEntity;
import com.wh.reputation.persistence.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewIndexingService {
    private static final Logger log = LoggerFactory.getLogger(ReviewIndexingService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewSearchRepository reviewSearchRepository;
    private final EmbeddingService embeddingService;

    public ReviewIndexingService(ReviewRepository reviewRepository, ReviewSearchRepository reviewSearchRepository,
            EmbeddingService embeddingService) {
        this.reviewRepository = reviewRepository;
        this.reviewSearchRepository = reviewSearchRepository;
        this.embeddingService = embeddingService;
    }

    @Async
    public void indexReview(Long reviewId) {
        try {
            ReviewEntity review = reviewRepository.findById(reviewId).orElse(null);
            if (review == null) {
                return;
            }
            indexReviewEntity(review);
        } catch (Exception e) {
            log.error("Error indexing review {}", reviewId, e);
        }
    }

    @Async
    public void indexReviews(List<Long> reviewIds) {
        for (Long id : reviewIds) {
            indexReview(id);
        }
    }

    private void indexReviewEntity(ReviewEntity review) {
        if (review.getContentClean() == null || review.getContentClean().isBlank()) {
            return;
        }

        float[] embedding = embeddingService.generateEmbedding(review.getContentClean());

        ReviewDocument doc = new ReviewDocument(
                review.getId(),
                review.getProduct().getId(),
                review.getPlatform().getId(),
                review.getContentClean(),
                review.getOverallSentimentLabel(),
                review.getOverallSentimentScore(),
                review.getReviewTime() != null ? review.getReviewTime() : review.getCreatedAt(),
                embedding);

        reviewSearchRepository.save(doc);
        log.debug("Indexed review {}", review.getId());
    }
}
