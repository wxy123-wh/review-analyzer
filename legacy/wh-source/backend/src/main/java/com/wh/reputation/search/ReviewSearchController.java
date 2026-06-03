package com.wh.reputation.search;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wh.reputation.persistence.ReviewRepository;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class ReviewSearchController {

    private final ReviewIndexingService reviewIndexingService;
    private final ReviewRepository reviewRepository;

    public ReviewSearchController(ReviewIndexingService reviewIndexingService, ReviewRepository reviewRepository) {
        this.reviewIndexingService = reviewIndexingService;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping("/index/all")
    public String indexAll() {
        List<Long> allIds = reviewRepository.findAllIds();
        reviewIndexingService.indexReviews(allIds);
        return "Triggered indexing for " + allIds.size() + " reviews.";
    }
}
