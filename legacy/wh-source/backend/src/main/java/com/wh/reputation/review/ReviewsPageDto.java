package com.wh.reputation.review;

import java.util.List;

public record ReviewsPageDto(int page, int pageSize, long total, List<ReviewListItemDto> items) {}

