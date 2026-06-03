package com.wh.review.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.wh.review.backend.service.OneBoundReviewClient oneBoundReviewClient;

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void realReviewImportShouldDriveAnalysisAndIssues() throws Exception {
        String productCode = "jd-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode, "local-jsonl", "jd");

        MvcResult startResult = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andReturn();

        String jobId = JsonPath.read(startResult.getResponse().getContentAsString(), "$.jobId");
        mockMvc.perform(get("/api/v1/analysis/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/issues").queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].priorityScore").isNumber());

        mockMvc.perform(get("/api/v1/data-quality").queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.rawCount").value(3))
                .andExpect(jsonPath("$.cleanedCount").value(3));

        mockMvc.perform(get("/api/v1/positive-insights").queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items[0].uxSecondaryLabel").isNotEmpty())
                .andExpect(jsonPath("$.items[0].sellingPoint").isNotEmpty());
    }

    @Test
    void compareTrendWordCloudActionAndValidationShouldUseImportedReviews() throws Exception {
        String productCode = "jd-main-" + UUID.randomUUID().toString().substring(0, 8);
        String comparisonProductCode = "jd-comp-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode, "local-jsonl", "jd");
        importReviews(comparisonProductCode, "local-jsonl", "jd");
        analyze(productCode);
        analyze(comparisonProductCode);

        mockMvc.perform(get("/api/v1/compare")
                        .queryParam("productCode", productCode)
                        .queryParam("comparisonProductCode", comparisonProductCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "bluetooth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.points").isArray());

        mockMvc.perform(get("/api/v1/wordcloud")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "bluetooth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray());

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "issueId": "iss-bluetooth-1",
                                  "actionName": "优化蓝牙连接稳定性",
                                  "actionDesc": "根据真实评论中的断连反馈安排固件优化"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn();

        String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId");
        mockMvc.perform(get("/api/v1/validation").queryParam("actionId", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionId").value(actionId))
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());
    }

    @Test
    void unsupportedSyncProviderShouldNotExposeSeedPath() throws Exception {
        mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "legacy-seed",
                                  "platform": "taobao",
                                  "targetProductCode": "jd-product"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("UNSUPPORTED_SOURCE"));
    }

    @Test
    void showcaseEndpointsShouldReturnRuntimeStateFromRealImports() throws Exception {
        String productCode = "jd-showcase-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode, "local-jsonl", "jd");
        analyze(productCode);

        mockMvc.perform(get("/api/v1/showcase/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.stages").isArray());

        mockMvc.perform(get("/api/v1/showcase/explainability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("v1-state=live")));

        mockMvc.perform(post("/api/v1/showcase/reports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"overview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.previewSections").isArray());
    }

    private void analyze(String productCode) throws Exception {
        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"%s\"}".formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    private void importReviews(String productCode, String provider, String platform) throws Exception {
        mockMvc.perform(post("/api/v1/reviews/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "%s",
                                  "platform": "%s",
                                  "productCode": "%s",
                                  "cleaningSummary": {
                                    "rawCount": 3,
                                    "cleanedCount": 3,
                                    "removedCount": 0,
                                    "htmlCleanedCount": 0,
                                    "exactDuplicateCount": 0,
                                    "emptyContentCount": 0,
                                    "invalidJsonCount": 0
                                  },
                                  "reviews": [
                                    {
                                      "sourceReviewId": "%s-001",
                                      "rating": 2,
                                      "content": "蓝牙连接偶尔断开，通话声音也不够清晰。",
                                      "reviewTime": "2026-06-01T10:20:00Z",
                                      "anonymizedAuthorId": "user-a"
                                    },
                                    {
                                      "sourceReviewId": "%s-002",
                                      "rating": 5,
                                      "content": "续航很好，佩戴舒适，日常通勤很满意。",
                                      "reviewTime": "2026-06-02T10:20:00Z",
                                      "anonymizedAuthorId": "user-b"
                                    },
                                    {
                                      "sourceReviewId": "%s-003",
                                      "rating": 2,
                                      "content": "降噪一般，地铁里噪音明显。",
                                      "reviewTime": "2026-06-03T10:20:00Z",
                                      "anonymizedAuthorId": "user-c"
                                    }
                                  ]
                                }
                                """.formatted(provider, platform, productCode, productCode, productCode, productCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"));
    }
}
