package com.wh.reputation.analytics;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final WordcloudService wordcloudService;
    private final SentimentTrendService sentimentTrendService;

    public AnalyticsController(WordcloudService wordcloudService, SentimentTrendService sentimentTrendService) {
        this.wordcloudService = wordcloudService;
        this.sentimentTrendService = sentimentTrendService;
    }

    @GetMapping("/wordcloud")
    public ApiResponse<WordcloudResponseDto> wordcloud(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "topN", required = false) Integer topN
    ) {
        return ApiResponse.ok(wordcloudService.wordcloud(
                productId,
                parseDateOrNull(start),
                parseDateOrNull(end),
                aspectId,
                sentiment,
                topN
        ));
    }

    @GetMapping("/sentiment-trend")
    public ApiResponse<SentimentTrendResponseDto> sentimentTrend(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "granularity", required = false) String granularity
    ) {
        return ApiResponse.ok(sentimentTrendService.sentimentTrend(
                productId,
                parseDateOrNull(start),
                parseDateOrNull(end),
                granularity
        ));
    }
}
