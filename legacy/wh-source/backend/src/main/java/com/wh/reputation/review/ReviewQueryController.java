package com.wh.reputation.review;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewQueryController {
    private final ReviewQueryService reviewQueryService;

    public ReviewQueryController(ReviewQueryService reviewQueryService) {
        this.reviewQueryService = reviewQueryService;
    }

    @GetMapping
    public ApiResponse<ReviewsPageDto> list(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "platformId", required = false) Long platformId,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.ok(reviewQueryService.list(productId, platformId, aspectId, sentiment, keyword, start, end, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewDetailDto> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(reviewQueryService.detail(id));
    }
}

