package com.wh.reputation.crawl;

public record CrawlRunResult(int inserted, int skipped, int errors, String batchId) {}

