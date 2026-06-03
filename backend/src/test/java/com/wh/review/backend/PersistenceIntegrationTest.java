package com.wh.review.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wh.review.backend.persistence.ExternalReviewRawRepository.ExternalRawReview;
import com.wh.review.backend.service.OneBoundReviewClient;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
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
class PersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private OneBoundReviewClient oneBoundReviewClient;

    @Test
    void importedReviewsShouldPersistAndMaterializeAnalysisOutputs() throws Exception {
        String productCode = "jd-persist-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode);

        MvcResult analysisResult = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"%s\"}".formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();

        long analysisJobId = Long.parseLong(JsonPath.read(analysisResult.getResponse().getContentAsString(), "$.jobId"));
        assertCount("SELECT COUNT(*) FROM analysis_jobs WHERE id = ?", analysisJobId, 1L);
        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode) > 0L);
        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM issue_clusters ic
                JOIN products p ON p.id = ic.product_id
                WHERE p.product_code = ?
                """, productCode) > 0L);
        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM review_semantic_labels sl
                JOIN reviews_raw r ON r.id = sl.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode) > 0L);

        mockMvc.perform(get("/api/v1/positive-insights").queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("success"))
                .andExpect(jsonPath("$.items[0].sellingPoint").isNotEmpty());
    }

    @Test
    void analysisJobShouldReuseSucceededResultWhenImportedDataIsUnchanged() throws Exception {
        String productCode = "jd-reuse-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode);
        long firstJobId = analyzeAndReadJobId(productCode);
        long secondJobId = analyzeAndReadJobId(productCode);
        assertEquals(firstJobId, secondJobId);
    }

    @Test
    void oneBoundSyncShouldPersistRealRawReviewsForLaterAnalysis() throws Exception {
        when(oneBoundReviewClient.fetchFirstPage(eq("taobao"), eq("600530677643")))
                .thenReturn(new OneBoundReviewClient.FetchedReviewPage(
                        List.of(
                                new ExternalRawReview(
                                        "onebound",
                                        "taobao",
                                        "600530677643",
                                        "rv-1",
                                        "dedupe-1",
                                        new BigDecimal("4.5"),
                                        "续航不错，佩戴舒适",
                                        Instant.parse("2026-04-06T08:00:00Z"),
                                        "author-a",
                                        "{\"page\":1,\"sourcePath\":\"data.comments\"}"
                                ),
                                new ExternalRawReview(
                                        "onebound",
                                        "taobao",
                                        "600530677643",
                                        "rv-2",
                                        "dedupe-2",
                                        new BigDecimal("2.0"),
                                        "蓝牙偶发断连",
                                        Instant.parse("2026-04-06T09:00:00Z"),
                                        "author-b",
                                        "{\"page\":1,\"sourcePath\":\"data.comments\"}"
                                )
                        ),
                        "{\"page\":1,\"sourcePath\":\"data.comments\"}"
                ));

        MvcResult syncResult = mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "onebound",
                                  "platform": "taobao",
                                  "targetProductCode": "600530677643"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"))
                .andReturn();

        long syncJobId = Long.parseLong(JsonPath.read(syncResult.getResponse().getContentAsString(), "$.jobId"));
        assertCount("SELECT COUNT(*) FROM sync_jobs WHERE id = ?", syncJobId, 1L);
        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, "600530677643") >= 2L);
    }

    @Test
    void analysisShouldFailWhenNoRealReviewsExist() throws Exception {
        String productCode = "missing-real-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"%s\"}".formatted(productCode)))
                .andExpect(status().isAccepted())
                .andReturn();

        long jobId = Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.jobId"));
        assertEquals("FAILED", JsonPath.read(result.getResponse().getContentAsString(), "$.status"));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT error_message FROM analysis_jobs WHERE id = ?")) {
            ps.setLong(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString("error_message").contains("no reviews found"));
            }
        }
    }

    @Test
    void actionValidationShouldUseImportedReviewWindow() throws Exception {
        String productCode = "jd-validation-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode);
        analyzeAndReadJobId(productCode);

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "issueId": "iss-bluetooth-1",
                                  "actionName": "处理真实评论断连问题",
                                  "actionDesc": "基于真实导入评论创建验证动作"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isCreated())
                .andReturn();

        long actionId = Long.parseLong(JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId"));
        mockMvc.perform(get("/api/v1/validation").queryParam("actionId", String.valueOf(actionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT window_start, window_end, before_metrics, after_metrics FROM validation_metrics WHERE action_id = ?")) {
            ps.setLong(1, actionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotNull(rs.getTimestamp("window_start"));
                assertNotNull(rs.getTimestamp("window_end"));
                assertTrue(rs.getString("before_metrics").contains("negativeRate"));
                assertTrue(rs.getString("after_metrics").contains("negativeRate"));
            }
        }
    }

    private long analyzeAndReadJobId(String productCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"%s\"}".formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.jobId"));
    }

    private void importReviews(String productCode) throws Exception {
        mockMvc.perform(post("/api/v1/reviews/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "local-jsonl",
                                  "platform": "jd",
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
                                      "reviewTime": "2026-06-01T10:20:00Z"
                                    },
                                    {
                                      "sourceReviewId": "%s-002",
                                      "rating": 5,
                                      "content": "续航很好，佩戴舒适，日常通勤很满意。",
                                      "reviewTime": "2026-06-02T10:20:00Z"
                                    },
                                    {
                                      "sourceReviewId": "%s-003",
                                      "rating": 2,
                                      "content": "降噪一般，地铁里噪音明显。",
                                      "reviewTime": "2026-06-03T10:20:00Z"
                                    }
                                  ]
                                }
                                """.formatted(productCode, productCode, productCode, productCode)))
                .andExpect(status().isCreated());
    }

    private long countForProduct(String sql, String productCode) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void assertCount(String sql, long id, long expected) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expected, rs.getLong(1));
            }
        }
    }
}
