package com.wh.reputation.crawl;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crawl")
public class CrawlController {
    private final CrawlService crawlService;

    public CrawlController(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @PostMapping("/run")
    public ApiResponse<CrawlRunResult> run(@RequestBody CrawlRunRequest body) {
        if (body == null) {
            throw new BadRequestException("请求体不能为空");
        }
        return ApiResponse.ok(crawlService.run(body));
    }
}
