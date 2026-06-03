package com.wh.reputation.dashboard;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewDto> overview(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(dashboardService.overview(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }
}
