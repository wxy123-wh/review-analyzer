package com.wh.review.backend.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "integration.onebound")
public class OneBoundProperties {

    private String baseUrl = "https://api-gw.onebound.cn";
    private String apiKey = "";
    private String apiSecret = "";
    private String defaultPlatform = "taobao";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getDefaultPlatform() {
        return defaultPlatform;
    }

    public void setDefaultPlatform(String defaultPlatform) {
        this.defaultPlatform = defaultPlatform;
    }

    public String getEffectiveSecret() {
        if (apiSecret != null && !apiSecret.isBlank()) {
            return apiSecret;
        }
        return apiKey;
    }
}
