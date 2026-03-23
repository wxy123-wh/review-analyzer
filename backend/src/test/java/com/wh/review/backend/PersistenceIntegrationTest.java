package com.wh.review.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                                  "productCode": "demo-earphone"
                                }
                                """))
                .andExpect(status().isAccepted())
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
        }
    }

    @Test
    void demoCommentSeedShouldBeIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "demo-earphone"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult secondSeedResult = mockMvc.perform(post("/api/v1/demo-data/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "demo-earphone"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

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
                reviewPs.setString(1, "demo-earphone");
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
                seedVersionPs.setString(2, "demo-earphone");
                seedVersionPs.setString(3, "demo-comments-v1");
                try (ResultSet rs = seedVersionPs.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1L, rs.getLong(1));
                }
            }
        }
    }
}
