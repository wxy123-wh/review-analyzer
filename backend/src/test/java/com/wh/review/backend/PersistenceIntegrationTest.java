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
    void actionCreationShouldPersistToDatabase() throws Exception {
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
                .andReturn();

        String actionId = JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId");
        long id = Long.parseLong(actionId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM improvement_actions WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1));
            }
        }
    }

    @Test
    void syncAndAnalysisJobsShouldPersistToDatabase() throws Exception {
        String productCode = "demo-analysis-persist-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

        MvcResult syncResult = mockMvc.perform(post("/api/v1/sync/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "aggregator-demo",
                                  "platform": "taobao",
                                  "targetProductCode": "demo-earphone"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult analysisResult = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();

        long syncJobId = Long.parseLong(JsonPath.read(syncResult.getResponse().getContentAsString(), "$.jobId"));
        long analysisJobId = Long.parseLong(JsonPath.read(analysisResult.getResponse().getContentAsString(), "$.jobId"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement syncPs = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sync_jobs WHERE id = ?")) {
                syncPs.setLong(1, syncJobId);
                try (ResultSet rs = syncPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1L, rs.getLong(1));
                }
            }

            try (PreparedStatement analysisPs = connection.prepareStatement(
                    "SELECT COUNT(*) FROM analysis_jobs WHERE id = ?")) {
                analysisPs.setLong(1, analysisJobId);
                try (ResultSet rs = analysisPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1L, rs.getLong(1));
                }
            }

            try (PreparedStatement aspectPs = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM review_aspects ra
                    JOIN reviews_raw r ON r.id = ra.review_id
                    JOIN products p ON p.id = r.product_id
                    WHERE p.product_code = ?
                    """)) {
                aspectPs.setString(1, productCode);
                try (ResultSet rs = aspectPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong(1) > 0L);
                }
            }

            try (PreparedStatement clusterPs = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM issue_clusters ic
                    JOIN products p ON p.id = ic.product_id
                    WHERE p.product_code = ?
                    """)) {
                clusterPs.setString(1, productCode);
                try (ResultSet rs = clusterPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong(1) > 0L);
                }
            }

            try (PreparedStatement scorePs = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM issue_scores s
                    JOIN issue_clusters ic ON ic.id = s.issue_cluster_id
                    JOIN products p ON p.id = ic.product_id
                    WHERE p.product_code = ?
                    """)) {
                scorePs.setString(1, productCode);
                try (ResultSet rs = scorePs.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong(1) > 0L);
                }
            }
        }
    }

    @Test
    void externalSyncShouldPersistTransparentHandoffMetadataAndRawReviewShape() throws Exception {
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
                .andExpect(jsonPath("$.fetchedCount").value(2))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"))
                .andReturn();

        long syncJobId = Long.parseLong(JsonPath.read(syncResult.getResponse().getContentAsString(), "$.jobId"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement syncPs = connection.prepareStatement(
                    "SELECT status, fetched_count, analysis_handoff_status, analysis_handoff_note FROM sync_jobs WHERE id = ?")) {
                syncPs.setLong(1, syncJobId);
                try (ResultSet rs = syncPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("SUCCEEDED", rs.getString("status"));
                    assertEquals(2, rs.getInt("fetched_count"));
                    assertEquals("READY_FOR_ANALYSIS", rs.getString("analysis_handoff_status"));
                    assertTrue(rs.getString("analysis_handoff_note").contains("manual"));
                }
            }

            try (PreparedStatement reviewPs = connection.prepareStatement(
                    """
                    SELECT COUNT(*), MIN(provider), MIN(platform), MIN(external_product_code), MIN(sync_job_id), MIN(external_dedupe_key), MIN(fetch_metadata)
                    FROM reviews_raw
                    WHERE provider = ? AND platform = ? AND external_product_code = ?
                    """)) {
                reviewPs.setString(1, "onebound");
                reviewPs.setString(2, "taobao");
                reviewPs.setString(3, "600530677643");
                try (ResultSet rs = reviewPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2L, rs.getLong(1));
                    assertEquals("onebound", rs.getString(2));
                    assertEquals("taobao", rs.getString(3));
                    assertEquals("600530677643", rs.getString(4));
                    assertEquals(syncJobId, rs.getLong(5));
                    assertNotNull(rs.getString(6));
                    assertTrue(rs.getString(7).contains("sourcePath"));
                }
            }
        }
    }

    @Test
    void failedExternalSyncShouldPersistFailureTransparencyWithoutBlockingControlledDataAnalysis() throws Exception {
        when(oneBoundReviewClient.fetchFirstPage(eq("taobao"), eq("600530677643")))
                .thenThrow(new IllegalStateException("ONEBOUND_API_KEY is not configured"));

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
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("BLOCKED_SYNC_FAILED"))
                .andReturn();

        long syncJobId = Long.parseLong(JsonPath.read(syncResult.getResponse().getContentAsString(), "$.jobId"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement syncPs = connection.prepareStatement(
                     "SELECT status, finished_at, error_message, analysis_handoff_status FROM sync_jobs WHERE id = ?")) {
            syncPs.setLong(1, syncJobId);
            try (ResultSet rs = syncPs.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("FAILED", rs.getString("status"));
                assertNotNull(rs.getTimestamp("finished_at"));
                assertTrue(rs.getString("error_message").contains("ONEBOUND_API_KEY"));
                assertEquals("BLOCKED_SYNC_FAILED", rs.getString("analysis_handoff_status"));
            }
        }

        String productCode = "demo-controlled-after-failure-" + UUID.randomUUID().toString().substring(0, 8);
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
    }

    @Test
    void analysisJobShouldReuseSucceededResultWhenControlledDataIsUnchanged() throws Exception {
        String productCode = "demo-analysis-reuse-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk());

        MvcResult firstRun = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andReturn();

        long firstJobId = Long.parseLong(JsonPath.read(firstRun.getResponse().getContentAsString(), "$.jobId"));
        String firstStatus = JsonPath.read(firstRun.getResponse().getContentAsString(), "$.status");
        assertEquals("SUCCEEDED", firstStatus);

        long aspectCountAfterFirstRun = countForProduct(
                """
                SELECT COUNT(*)
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """,
                productCode
        );
        long clusterCountAfterFirstRun = countForProduct(
                """
                SELECT COUNT(*)
                FROM issue_clusters ic
                JOIN products p ON p.id = ic.product_id
                WHERE p.product_code = ?
                """,
                productCode
        );

        MvcResult secondRun = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andReturn();

        long secondJobId = Long.parseLong(JsonPath.read(secondRun.getResponse().getContentAsString(), "$.jobId"));
        assertEquals(firstJobId, secondJobId);
        assertEquals(aspectCountAfterFirstRun, countForProduct(
                """
                SELECT COUNT(*)
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """,
                productCode
        ));
        assertEquals(clusterCountAfterFirstRun, countForProduct(
                """
                SELECT COUNT(*)
                FROM issue_clusters ic
                JOIN products p ON p.id = ic.product_id
                WHERE p.product_code = ?
                """,
                productCode
        ));
    }

    @Test
    void analysisJobShouldPersistFailureMetadataWhenNoControlledDataExists() throws Exception {
        String productCode = "missing-analysis-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isAccepted())
                .andReturn();

        long jobId = Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.jobId"));
        assertEquals("FAILED", JsonPath.read(result.getResponse().getContentAsString(), "$.status"));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT status, finished_at, error_message FROM analysis_jobs WHERE id = ?")) {
            ps.setLong(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("FAILED", rs.getString("status"));
                assertNotNull(rs.getTimestamp("finished_at"));
                assertTrue(rs.getString("error_message").contains("no reviews found"));
            }
        }
    }

    @Test
    void issuesEndpointShouldReadMaterializedIssueTablesInsteadOfRawReviewAggregation() throws Exception {
        String productCode = "demo-issues-materialized-" + UUID.randomUUID().toString().substring(0, 8);

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

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteScores = connection.prepareStatement(
                    "DELETE FROM issue_scores WHERE issue_cluster_id IN (SELECT ic.id FROM issue_clusters ic JOIN products p ON p.id = ic.product_id WHERE p.product_code = ?)")) {
                deleteScores.setString(1, productCode);
                deleteScores.executeUpdate();
            }
            try (PreparedStatement deleteClusters = connection.prepareStatement(
                    "DELETE FROM issue_clusters WHERE product_id = (SELECT id FROM products WHERE product_code = ?)")) {
                deleteClusters.setString(1, productCode);
                deleteClusters.executeUpdate();
            }
        }

        mockMvc.perform(get("/api/v1/issues")
                        .queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issueId").value("iss-demo-empty"))
                .andExpect(jsonPath("$.items[0].aspect").value("general"))
                .andExpect(jsonPath("$.items[0].evidenceSummary").value(org.hamcrest.Matchers.containsString("当前未识别到高优先级问题")));
    }

    @Test
    void trendAndWordCloudEndpointsShouldDependOnMaterializedAspectRows() throws Exception {
        String productCode = "demo-aspects-materialized-" + UUID.randomUUID().toString().substring(0, 8);

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

        try (Connection connection = dataSource.getConnection();
             PreparedStatement deleteAspects = connection.prepareStatement(
                     "DELETE FROM review_aspects WHERE review_id IN (SELECT r.id FROM reviews_raw r JOIN products p ON p.id = r.product_id WHERE p.product_code = ?)")) {
            deleteAspects.setString(1, productCode);
            deleteAspects.executeUpdate();
        }

        mockMvc.perform(get("/api/v1/trends")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());

        mockMvc.perform(get("/api/v1/wordcloud")
                        .queryParam("productCode", productCode)
                        .queryParam("aspect", "battery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.notice").isNotEmpty());
    }

    @Test
    void validationShouldPersistTraceableMetricsForActionLinkedWindow() throws Exception {
        String productCode = "demo-validation-persist-" + UUID.randomUUID().toString().substring(0, 8);

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

        MvcResult issuesResult = mockMvc.perform(get("/api/v1/issues")
                        .queryParam("productCode", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issueId").isNotEmpty())
                .andReturn();

        String issueId = JsonPath.read(issuesResult.getResponse().getContentAsString(), "$.items[0].issueId");

        MvcResult actionResult = mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "issueId": "%s",
                                  "actionName": "处理：验证持久化",
                                  "actionDesc": "校验 action ↔ validation 闭环"
                                }
                                """.formatted(productCode, issueId)))
                .andExpect(status().isCreated())
                .andReturn();

        long actionId = Long.parseLong(JsonPath.read(actionResult.getResponse().getContentAsString(), "$.actionId"));

        mockMvc.perform(get("/api/v1/validation")
                        .queryParam("actionId", String.valueOf(actionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionId").value(String.valueOf(actionId)))
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty());

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement actionPs = connection.prepareStatement(
                    "SELECT issue_cluster_id, launched_at FROM improvement_actions WHERE id = ?")) {
                actionPs.setLong(1, actionId);
                try (ResultSet rs = actionPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong("issue_cluster_id") > 0L);
                    assertNotNull(rs.getTimestamp("launched_at"));
                }
            }

            try (PreparedStatement metricsPs = connection.prepareStatement(
                    "SELECT window_start, window_end, before_metrics, after_metrics, conclusion FROM validation_metrics WHERE action_id = ?")) {
                metricsPs.setLong(1, actionId);
                try (ResultSet rs = metricsPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertNotNull(rs.getTimestamp("window_start"));
                    assertNotNull(rs.getTimestamp("window_end"));
                    assertTrue(rs.getString("before_metrics").contains("negativeRate"));
                    assertTrue(rs.getString("after_metrics").contains("negativeRate"));
                    assertTrue(rs.getString("conclusion").contains("动作"));
                }
            }
        }
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

    @Test
    void demoCommentSeedShouldBeIdempotent() throws Exception {
        String productCode = "demo-earphone-persist-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult firstSeedResult = mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult secondSeedResult = mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s"
                                }
                                """.formatted(productCode)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(100, (Integer) JsonPath.read(firstSeedResult.getResponse().getContentAsString(), "$.insertedReviewCount"));
        assertEquals(0, (Integer) JsonPath.read(firstSeedResult.getResponse().getContentAsString(), "$.updatedReviewCount"));
        assertEquals(100, (Integer) JsonPath.read(firstSeedResult.getResponse().getContentAsString(), "$.totalReviewCount"));

        assertEquals(0, (Integer) JsonPath.read(secondSeedResult.getResponse().getContentAsString(), "$.insertedReviewCount"));
        assertEquals(100, (Integer) JsonPath.read(secondSeedResult.getResponse().getContentAsString(), "$.updatedReviewCount"));
        assertEquals(100, (Integer) JsonPath.read(secondSeedResult.getResponse().getContentAsString(), "$.totalReviewCount"));

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement reviewPs = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM reviews_raw r
                    JOIN products p ON p.id = r.product_id
                    WHERE p.product_code = ?
                      AND r.source = ?
                    """)) {
                reviewPs.setString(1, productCode);
                reviewPs.setString(2, "demo-seed");
                try (ResultSet rs = reviewPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(100L, rs.getLong(1));
                }
            }

            try (PreparedStatement seedVersionPs = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM demo_seed_versions
                    WHERE seed_key = ?
                      AND product_code = ?
                      AND data_version = ?
                    """)) {
                seedVersionPs.setString(1, "demo-comments");
                seedVersionPs.setString(2, productCode);
                seedVersionPs.setString(3, "demo-comments-v1");
                try (ResultSet rs = seedVersionPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1L, rs.getLong(1));
                }
            }
        }
    }
}
