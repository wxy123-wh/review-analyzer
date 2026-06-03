package com.wh.reputation.evaluate;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluate")
public class EvaluateController {
    private final EvaluateService evaluateService;

    public EvaluateController(EvaluateService evaluateService) {
        this.evaluateService = evaluateService;
    }

    @GetMapping("/before-after")
    public ApiResponse<BeforeAfterResponseDto> beforeAfter(@RequestParam("eventId") Long eventId) {
        return ApiResponse.ok(evaluateService.beforeAfter(eventId));
    }
}

