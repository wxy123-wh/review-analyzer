package com.wh.reputation.compare;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@RestController
@RequestMapping("/api/compare")
public class CompareController {
    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/aspects")
    public ApiResponse<CompareAspectsResponseDto> aspects(
            @RequestParam("productId") Long productId,
            @RequestParam("competitorId") Long competitorId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(compareService.compareAspects(productId, competitorId, parseDateOrNull(start), parseDateOrNull(end)));
    }
}
