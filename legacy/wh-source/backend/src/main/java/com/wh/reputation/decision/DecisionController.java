package com.wh.reputation.decision;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@RestController
@RequestMapping("/api/decision")
public class DecisionController {
    private final DecisionPriorityService decisionPriorityService;
    private final SuggestionService suggestionService;
    private final AiAnalysisService aiAnalysisService;

    public DecisionController(
            DecisionPriorityService decisionPriorityService,
            SuggestionService suggestionService,
            AiAnalysisService aiAnalysisService) {
        this.decisionPriorityService = decisionPriorityService;
        this.suggestionService = suggestionService;
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping("/priority")
    public ApiResponse<PriorityResponseDto> priority(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "topN", required = false, defaultValue = "10") Integer topN) {
        return ApiResponse
                .ok(decisionPriorityService.priorities(productId, parseDateOrNull(start), parseDateOrNull(end), topN));
    }

    @GetMapping("/suggestions")
    public ApiResponse<SuggestionsResponseDto> suggestions(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end) {
        return ApiResponse.ok(suggestionService.suggestions(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @PostMapping("/ai-summary")
    public ApiResponse<AiSummaryResponseDto> aiSummary(@RequestBody AiSummaryRequestDto request) {
        return ApiResponse.ok(aiAnalysisService.analyzeCluster(request.clusterId()));
    }
}
