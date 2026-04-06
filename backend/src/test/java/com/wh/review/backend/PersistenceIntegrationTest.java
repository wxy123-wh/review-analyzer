package com.wh.review.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
