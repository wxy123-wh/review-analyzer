package com.wh.review.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void analysisJobShouldSupportStartAndQuery() throws Exception {
        String productCode = "demo-analysis-smoke-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

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

        String body = startResult.getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");

        mockMvc.perform(get("/api/v1/analysis/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void issuesEndpointShouldReturnArray() throws Exception {
        String productCode = "demo-issues-smoke-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/issues")
                        .queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].priorityScore").isNumber())
                .andExpect(jsonPath("$.items[0].evidenceSummary").isNotEmpty());
    }

    @Test
    void syncApiShouldSupportStartAndQuery() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"aggregator-demo\",\"targetProductCode\":\"demo-earphone\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("CONTROLLED_DATA_PATH"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String jobId = JsonPath.read(startResult.getResponse().getContentAsString(), "$.jobId");
        mockMvc.perform(get("/api/v1/sync/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void syncApiShouldExposeUnsupportedProviderInsteadOfLeavingQueuedForever() throws Exception {
        mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"provider\": \"mystery-sync\",
                                  \"platform\": \"taobao\",
                                  \"targetProductCode\": \"demo-earphone\"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("unsupported sync provider")))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("UNSUPPORTED_SOURCE"));
    }

    @Test
    void externalSyncFailureShouldNotBlockControlledDataInitAndAnalysis() throws Exception {
        org.mockito.Mockito.when(oneBoundReviewClient.fetchFirstPage("taobao", "600530677643"))
                .thenThrow(new IllegalStateException("ONEBOUND_API_KEY is not configured"));

        mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"provider\": \"onebound\",
                                  \"platform\": \"taobao\",
                                  \"targetProductCode\": \"600530677643\"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("BLOCKED_SYNC_FAILED"));

        String productCode = "demo-after-sync-failure-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"productCode\": \"%s\"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"productCode\": \"%s\"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void compareTrendActionAndValidationEndpointsShouldReturnStructuredData() throws Exception {
        String productCode = "demo-earphone-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String comparisonProductCode = "demo-earphone-competitor-smoke-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedKey").value("demo-comments"))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.targetReviewCount").value(100))
                .andExpect(jsonPath("$.insertedReviewCount").value(100))
                .andExpect(jsonPath("$.updatedReviewCount").value(0))
                .andExpect(jsonPath("$.totalReviewCount").value(100));

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(comparisonProductCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(comparisonProductCode));

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(comparisonProductCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/compare")
                        .queryParam("productCode", productCode)
                        .queryParam("comparisonProductCode", comparisonProductCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.comparisonProductCode").value(comparisonProductCode))
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].aspect").value("battery"))
                .andExpect(jsonPath("$.items[1].aspect").value("bluetooth"))
                .andExpect(jsonPath("$.items[2].aspect").value("noise-canceling"))
                .andExpect(jsonPath("$.items[3].aspect").value("comfort"))
                .andExpect(jsonPath("$.items[4].aspect").value("microphone"))
                .andExpect(jsonPath("$.items[0].ourScore").value(0.22))
                .andExpect(jsonPath("$.items[0].competitorScore").value(0.78))
                .andExpect(jsonPath("$.items[0].gap").value(-0.56))
                .andExpect(jsonPath("$.items[1].ourScore").value(0.78));

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.aspect").value("battery"))
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points[0].negativeRate").isNumber());

        mockMvc.perform(get("/api/v1/wordcloud")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.aspect").value("battery"))
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].keyword").isNotEmpty())
                .andExpect(jsonPath("$.items[0].frequency").isNumber())
                .andExpect(jsonPath("$.items[0].weight").isNumber())
                .andExpect(jsonPath("$.items[0].sentimentTag").isNotEmpty());

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "issueId": "iss-battery-001",
                                  "actionName": "Battery firmware optimization",
                                  "actionDesc": "Tune low-power mode for long idle use"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn();

        String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId");

        mockMvc.perform(get("/api/v1/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionId").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("PLANNED"));

        mockMvc.perform(get("/api/v1/actions/{actionId}", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionId").value(actionId))
                .andExpect(jsonPath("$.productCode").value(productCode));

        mockMvc.perform(get("/api/v1/validation")
                        .queryParam("actionId", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].actionId").value(actionId))
                .andExpect(jsonPath("$.items[0].beforeNegativeRate").isNumber())
                .andExpect(jsonPath("$.items[0].afterNegativeRate").isNumber())
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());
    }

    @Test
    void insightEndpointsShouldPreserveNoDataAndFallbackSemanticsWithoutMaterializedOutputs() throws Exception {
        String missingProductCode = "missing-insight-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(get("/api/v1/issues")
                        .queryParam("productCode", missingProductCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("empty"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", missingProductCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("empty"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());

        mockMvc.perform(get("/api/v1/wordcloud")
                        .queryParam("productCode", missingProductCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("empty"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());

        mockMvc.perform(get("/api/v1/compare")
                        .queryParam("productCode", missingProductCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("missing-target"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());
    }

    @Test
    void showcaseEndpointsShouldReturnRealStructuredData() throws Exception {
        String productCode = "demo-showcase-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String comparisonProductCode = "demo-showcase-competitor-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(comparisonProductCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "aggregator-demo",
                                  "targetProductCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(comparisonProductCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "issueId": "iss-battery-001",
                                  "actionName": "Battery firmware optimization",
                                  "actionDesc": "Tune low-power mode for long idle use"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isCreated())
                .andReturn();

        String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId");

        mockMvc.perform(get("/api/v1/validation")
                        .queryParam("actionId", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionId").value(actionId));

        mockMvc.perform(get("/api/v1/showcase/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("LIVE"),
                        org.hamcrest.Matchers.is("DEGRADED")
                )))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("data-source=sync_jobs+analysis_jobs+materialized_outputs+actions+validation")))
                .andExpect(jsonPath("$.stages").isArray())
                .andExpect(jsonPath("$.stages[0].name").value("SYNC"))
                .andExpect(jsonPath("$.stages[1].name").value("ANALYSIS"))
                .andExpect(jsonPath("$.stages[2].name").value("MATERIALIZATION"));

        mockMvc.perform(get("/api/v1/showcase/agent-arena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("lane rows are synthesized from persisted v1 subsystem health")))
                .andExpect(jsonPath("$.agents").isArray())
                .andExpect(jsonPath("$.agents[0].agentName").value("sync-lane"))
                .andExpect(jsonPath("$.agents[0].confidence").isNumber());

        mockMvc.perform(get("/api/v1/showcase/explainability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTROLLED_DATA_ONLY"))
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("v1-state=controlled-data-only")))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("strategy=keep")))
                .andExpect(jsonPath("$.featureContributions").isArray())
                .andExpect(jsonPath("$.featureContributions[0].feature").isNotEmpty())
                .andExpect(jsonPath("$.featureContributions[0].weight").isNumber());

        mockMvc.perform(get("/api/v1/showcase/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("v1-state=runtime-state")))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("data-source=sync_jobs+analysis_jobs+materialized_outputs")))
                .andExpect(jsonPath("$.drills").isArray())
                .andExpect(jsonPath("$.drills[0].scenario").value("sync-runtime"));

        mockMvc.perform(post("/api/v1/showcase/reports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"overview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(true))
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("LIVE"),
                        org.hamcrest.Matchers.is("DEGRADED")
                )))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("data-source=issues+compare+trends+actions+validation")))
                .andExpect(jsonPath("$.previewSections").isArray())
                .andExpect(jsonPath("$.previewSections[0]").value(org.hamcrest.Matchers.containsString(productCode)));
    }

    @Test
    void demoDataInitEndpointShouldReturnSeedStats() throws Exception {
        String productCode = "demo-earphone-init-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult seedResult = mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedKey").value("demo-comments"))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.dataVersion").value("demo-comments-v1"))
                .andExpect(jsonPath("$.targetReviewCount").value(100))
                .andExpect(jsonPath("$.insertedReviewCount").value(100))
                .andExpect(jsonPath("$.updatedReviewCount").value(0))
                .andExpect(jsonPath("$.totalReviewCount").value(100))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andReturn();

        String body = seedResult.getResponse().getContentAsString();
        int inserted = JsonPath.read(body, "$.insertedReviewCount");
        int updated = JsonPath.read(body, "$.updatedReviewCount");
        assertEquals(100, inserted + updated, "insertedReviewCount + updatedReviewCount should equal target count");
    }
}
