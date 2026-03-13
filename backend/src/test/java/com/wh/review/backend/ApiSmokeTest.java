package com.wh.review.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
        mockMvc.perform(get("/api/v1/compare")
                        .queryParam("productCode", "demo-earphone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].aspect").isNotEmpty())
                .andExpect(jsonPath("$.items[0].ourScore").isNumber());

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", "demo-earphone")
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aspect").value("battery"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points[0].negativeRate").isNumber());

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "demo-earphone",
                                  "issueId": "iss-battery-001",
                                  "actionName": "Battery firmware optimization",
                                  "actionDesc": "Tune low-power mode for long idle use"
                                }
                                """))
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
}
