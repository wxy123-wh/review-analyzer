package review.backend.api;

import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import review.backend.api.dto.IssueListResponse;
import review.backend.application.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IssueController {

    private final InsightQueryService insightQueryService;

    public IssueController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/issues")
    public IssueListResponse issues(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode
    ) {
        return insightQueryService.listIssues(productCode);
    }
}
