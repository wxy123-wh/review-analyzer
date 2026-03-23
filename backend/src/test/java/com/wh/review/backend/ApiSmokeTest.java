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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void analysisJobShouldSupportStartAndQuery() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"demo-earphone\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String body = startResult.getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");

        mockMvc.perform(get("/api/v1/analysis/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void issuesEndpointShouldReturnArray() throws Exception {
        mockMvc.perform(get("/api/v1/issues"))
                .andExpect(status().isOk())
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
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andReturn();

        String jobId = JsonPath.read(startResult.getResponse().getContentAsString(), "$.jobId");
        mockMvc.perform(get("/api/v1/sync/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void compareTrendActionAndValidationEndpointsShouldReturnStructuredData() throws Exception {
        String productCode = "demo-earphone-smoke-" + UUID.randomUUID().toString().substring(0, 8);

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

        mockMvc.perform(get("/api/v1/compare")
                        .queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].aspect").isNotEmpty())
                .andExpect(jsonPath("$.items[0].ourScore").isNumber());

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.aspect").value("battery"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points[0].negativeRate").isNumber());

        mockMvc.perform(get("/api/v1/wordcloud")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.aspect").value("battery"))
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

        mockMvc.perform(get("/api/v1/validation")
                        .queryParam("actionId", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].actionId").value(actionId))
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());
    }

    @Test
    void showcasePlaceholderEndpointsShouldReturnStructuredData() throws Exception {
        mockMvc.perform(get("/api/v1/showcase/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.stages").isArray())
                .andExpect(jsonPath("$.stages[0].name").isNotEmpty());

        mockMvc.perform(get("/api/v1/showcase/agent-arena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.agents").isArray())
                .andExpect(jsonPath("$.agents[0].agentName").isNotEmpty());

        mockMvc.perform(get("/api/v1/showcase/explainability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.featureContributions").isArray())
                .andExpect(jsonPath("$.featureContributions[0].feature").isNotEmpty());

        mockMvc.perform(get("/api/v1/showcase/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.drills").isArray())
                .andExpect(jsonPath("$.drills[0].scenario").isNotEmpty());

        mockMvc.perform(post("/api/v1/showcase/reports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"overview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.previewSections").isArray())
                .andExpect(jsonPath("$.previewSections[0]").isNotEmpty());
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
