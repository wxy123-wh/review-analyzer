package com.wh.reputation.analysis;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final ReviewAnalysisService reviewAnalysisService;
    private final AnalysisQueryService analysisQueryService;
    private final KeywordAnalysisService keywordAnalysisService;
    private final TopicAnalysisService topicAnalysisService;
    private final ClusterAnalysisService clusterAnalysisService;

    public AnalysisController(
            ReviewAnalysisService reviewAnalysisService,
            AnalysisQueryService analysisQueryService,
            KeywordAnalysisService keywordAnalysisService,
            TopicAnalysisService topicAnalysisService,
            ClusterAnalysisService clusterAnalysisService
    ) {
        this.reviewAnalysisService = reviewAnalysisService;
        this.analysisQueryService = analysisQueryService;
        this.keywordAnalysisService = keywordAnalysisService;
        this.topicAnalysisService = topicAnalysisService;
        this.clusterAnalysisService = clusterAnalysisService;
    }

    @PostMapping("/run")
    public ApiResponse<AnalysisRunResponseDto> run(@RequestBody AnalysisRunRequest body) {
        if (body == null || body.productId() == null) {
            throw new BadRequestException("缺少产品编号");
        }
        LocalDate start = parseDateOrNull(body.start());
        LocalDate end = parseDateOrNull(body.end());
        reviewAnalysisService.analyzeByProduct(body.productId(), start, end);
        return ApiResponse.ok(new AnalysisRunResponseDto(true));
    }

    @GetMapping("/aspects")
    public ApiResponse<AspectAnalysisResponseDto> aspects(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(analysisQueryService.aspects(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponseDto> trend(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(analysisQueryService.trend(productId, aspectId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/keywords")
    public ApiResponse<KeywordsResponseDto> keywords(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "topN", required = false, defaultValue = "20") Integer topN
    ) {
        return ApiResponse.ok(keywordAnalysisService.keywords(productId, aspectId, parseDateOrNull(start), parseDateOrNull(end), topN));
    }

    @GetMapping("/topics")
    public ApiResponse<TopicsResponseDto> topics(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(topicAnalysisService.topics(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/clusters")
    public ApiResponse<ClustersResponseDto> clusters(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(clusterAnalysisService.clusters(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/clusters/{id}")
    public ApiResponse<ClusterDetailResponseDto> clusterDetail(@PathVariable("id") Long id) {
        return ApiResponse.ok(clusterAnalysisService.clusterDetail(id));
    }
}
