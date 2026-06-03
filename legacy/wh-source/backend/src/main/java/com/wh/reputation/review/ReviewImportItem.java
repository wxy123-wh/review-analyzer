package com.wh.reputation.review;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewImportItem(
        @JsonProperty("platform_name")
        @JsonAlias({"platformName"})
        String platformName,
        @JsonProperty("product_name")
        @JsonAlias({"productName"})
        String productName,
        @JsonProperty("brand")
        String brand,
        @JsonProperty("model")
        String model,
        @JsonProperty("rating")
        Integer rating,
        @JsonProperty("review_time")
        @JsonAlias({"reviewTime"})
        String reviewTime,
        @JsonProperty("content")
        @JsonAlias({"content_raw", "contentRaw"})
        String content,
        @JsonProperty("like_count")
        @JsonAlias({"likeCount"})
        Integer likeCount,
        @JsonProperty("review_id_raw")
        @JsonAlias({"reviewIdRaw"})
        String reviewIdRaw,
        @JsonProperty("is_competitor")
        @JsonAlias({"isCompetitor"})
        Boolean isCompetitor
) {}
