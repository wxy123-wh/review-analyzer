package com.wh.reputation.alert;

import com.wh.reputation.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<AlertsResponseDto> list(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.ok(alertService.list(productId, status));
    }

    @PostMapping("/ack")
    public ApiResponse<AlertAckResponseDto> ack(@RequestParam("id") Long id) {
        return ApiResponse.ok(new AlertAckResponseDto(alertService.ack(id)));
    }
}

