package com.wh.review.backend.controller;

import com.wh.review.backend.dto.DemoDataInitRequest;
import com.wh.review.backend.dto.DemoDataInitResponse;
import com.wh.review.backend.service.DemoDataInitializationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo-data")
public class DemoDataController {

    private final DemoDataInitializationService demoDataInitializationService;

    public DemoDataController(DemoDataInitializationService demoDataInitializationService) {
        this.demoDataInitializationService = demoDataInitializationService;
    }

    @PostMapping("/init")
    public DemoDataInitResponse initialize(@RequestBody(required = false) DemoDataInitRequest request) {
        String productCode = request == null ? null : request.productCode();
        return demoDataInitializationService.initializeComments(productCode);
    }
}
