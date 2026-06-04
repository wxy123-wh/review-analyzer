package review.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.Materialization;
import review.backend.data.AnalysisMaterializationRepository.ReviewAspectRecord;
import review.backend.data.ReviewSemanticLabelRepository.SemanticLabelRecord;
import review.backend.data.ExternalReviewRawRepository.ExternalRawReview;
import review.backend.application.OneBoundReviewClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

    @Autowired
    private AnalysisMaterializationRepository analysisMaterializationRepository;

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
    void analysisJobShouldReanalyzeWhenImportedDataOrTaxonomyMayHaveChanged() throws Exception {
        String productCode = "jd-reuse-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode);
        long firstJobId = analyzeAndReadJobId(productCode);
        long secondJobId = analyzeAndReadJobId(productCode);
        assertNotEquals(firstJobId, secondJobId);
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
    void crawlJobImportShouldCleanJsonlAndPersistReviews() throws Exception {
        String productCode = "jd-crawl-" + UUID.randomUUID().toString().substring(0, 8);
        Path rawJsonl = Files.createTempFile("raw-reviews-" + productCode, ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"c1","productCode":"%s","productName":"小米 Buds 5 Pro","rating":2,"content":"<p>蓝牙&nbsp;偶尔断连</p>","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"c1","productCode":"%s","productName":"小米 Buds 5 Pro","rating":2,"content":"重复评论","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"c2","productCode":"%s","productName":"小米 Buds 5 Pro","rating":5,"content":"此用户未及时填写评价内容","reviewTime":"2026-06-01 11:20:00"}
                {"source":"jd","sourceReviewId":"c3","productCode":"%s","productName":"小米 Buds 5 Pro","rating":1,"content":"此用户未及时填写评价内容 | [追评]: 蓝牙经常断连","reviewTime":"2026-06-01 12:20:00"}
                {not-json}

                """.formatted(productCode, productCode, productCode, productCode),
                StandardCharsets.UTF_8
        );
        long crawlJobId = insertCrawlJob(productCode, rawJsonl);

        mockMvc.perform(post("/api/v1/crawl/jobs/{id}/import", String.valueOf(crawlJobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.productName").value("小米 Buds 5 Pro"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.cleaningSummary.rawCount").value(6))
                .andExpect(jsonPath("$.cleaningSummary.cleanedCount").value(2))
                .andExpect(jsonPath("$.cleaningSummary.exactDuplicateCount").value(1))
                .andExpect(jsonPath("$.cleaningSummary.placeholderContentCount").value(2))
                .andExpect(jsonPath("$.cleanedOutputPath").isNotEmpty())
                .andExpect(jsonPath("$.sampleReviews[0].content").value("蓝牙 偶尔断连"));

        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode) >= 1L);
        assertEquals("小米 Buds 5 Pro", productNameForProduct(productCode));
    }

    @Test
    void jsonlFileImportShouldCleanAndPersistReviewsWithoutCrawlJob() throws Exception {
        String productCode = "jd-jsonl-" + UUID.randomUUID().toString().substring(0, 8);
        Path rawJsonl = Files.createTempFile("manual-raw-reviews-" + productCode, ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"m1","productCode":"%s","productName":"OPPO Enco Free4","rating":2,"content":"<p>蓝牙&nbsp;偶尔断连</p>","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"m1","productCode":"%s","productName":"OPPO Enco Free4","rating":2,"content":"重复评论","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"m2","productCode":"%s","productName":"OPPO Enco Free4","rating":5,"content":"此用户未及时填写评价内容","reviewTime":"2026-06-01 11:20:00"}
                {"source":"jd","sourceReviewId":"m3","productCode":"%s","productName":"OPPO Enco Free4","rating":1,"content":"此用户未及时填写评价内容 | [追评]: 蓝牙经常断连","reviewTime":"2026-06-01 12:20:00"}
                """.formatted(productCode, productCode, productCode, productCode),
                StandardCharsets.UTF_8
        );
        String inputPath = rawJsonl.toString().replace("\\", "\\\\");

        mockMvc.perform(post("/api/v1/reviews/import-jsonl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "OPPO Enco Free4",
                                  "platform": "jd",
                                  "inputPath": "%s"
                                }
                                """.formatted(productCode, inputPath)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value("manual-jsonl"))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.productName").value("OPPO Enco Free4"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.cleaningSummary.rawCount").value(4))
                .andExpect(jsonPath("$.cleaningSummary.cleanedCount").value(2))
                .andExpect(jsonPath("$.cleaningSummary.exactDuplicateCount").value(1))
                .andExpect(jsonPath("$.cleaningSummary.placeholderContentCount").value(2))
                .andExpect(jsonPath("$.sampleReviews[0].content").value("蓝牙 偶尔断连"));

        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode) >= 1L);
        assertEquals("OPPO Enco Free4", productNameForProduct(productCode));
        assertEquals(0L, countForProductContent(productCode, "此用户未及时填写评价内容"));
    }

    @Test
    void jsonlFileCleanShouldReturnOutputsWithoutPersistingUntilCleanedFileIsImported() throws Exception {
        String productCode = "jd-clean-" + UUID.randomUUID().toString().substring(0, 8);
        Path rawJsonl = Files.createTempFile("manual-clean-raw-reviews-" + productCode, ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"cl1","productCode":"%s","productName":"清洗测试耳机","rating":2,"content":"<p>蓝牙&nbsp;偶尔断连</p>","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"cl1","productCode":"%s","productName":"清洗测试耳机","rating":2,"content":"重复评论","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"cl2","productCode":"%s","productName":"清洗测试耳机","rating":5,"content":"此用户未及时填写评价内容","reviewTime":"2026-06-01 11:20:00"}
                {"source":"jd","sourceReviewId":"cl3","productCode":"%s","productName":"清洗测试耳机","rating":1,"content":"此用户未及时填写评价内容 | [追评]: 蓝牙经常断连","reviewTime":"2026-06-01 12:20:00"}
                """.formatted(productCode, productCode, productCode, productCode),
                StandardCharsets.UTF_8
        );
        String inputPath = rawJsonl.toString().replace("\\", "\\\\");

        MvcResult result = mockMvc.perform(post("/api/v1/reviews/clean-jsonl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "清洗测试耳机",
                                  "platform": "jd",
                                  "inputPath": "%s"
                                }
                                """.formatted(productCode, inputPath)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value("manual-jsonl-clean"))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.productName").value("清洗测试耳机"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_TAXONOMY_BINDING"))
                .andExpect(jsonPath("$.cleaningSummary.rawCount").value(4))
                .andExpect(jsonPath("$.cleaningSummary.cleanedCount").value(2))
                .andExpect(jsonPath("$.cleaningSummary.exactDuplicateCount").value(1))
                .andExpect(jsonPath("$.cleaningSummary.placeholderContentCount").value(2))
                .andExpect(jsonPath("$.receivedCount").value(2))
                .andExpect(jsonPath("$.insertedReviewCount").value(0))
                .andExpect(jsonPath("$.updatedReviewCount").value(0))
                .andExpect(jsonPath("$.totalReviewCount").value(0))
                .andExpect(jsonPath("$.cleanedOutputPath").isNotEmpty())
                .andExpect(jsonPath("$.removedOutputPath").isNotEmpty())
                .andExpect(jsonPath("$.cleaningSummaryPath").isNotEmpty())
                .andExpect(jsonPath("$.sampleReviews[0].content").value("蓝牙 偶尔断连"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String cleanedOutputPath = JsonPath.read(body, "$.cleanedOutputPath");
        String removedOutputPath = JsonPath.read(body, "$.removedOutputPath");
        String cleaningSummaryPath = JsonPath.read(body, "$.cleaningSummaryPath");
        assertTrue(Files.exists(Path.of(cleanedOutputPath)));
        assertTrue(Files.exists(Path.of(removedOutputPath)));
        assertTrue(Files.exists(Path.of(cleaningSummaryPath)));
        assertEquals(0L, countForProduct("SELECT COUNT(*) FROM products WHERE product_code = ?", productCode));
        assertEquals(0L, countForProduct("""
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode));
        assertEquals(0L, countForProduct("SELECT COUNT(*) FROM sync_jobs WHERE target_product_code = ?", productCode));
        assertEquals(0L, countForProduct("SELECT COUNT(*) FROM data_quality_runs WHERE product_code = ?", productCode));

        String cleanedInputPath = cleanedOutputPath.replace("\\", "\\\\");
        MvcResult importResult = mockMvc.perform(post("/api/v1/reviews/import-jsonl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "清洗测试耳机",
                                  "platform": "jd",
                                  "inputPath": "%s"
                                }
                                """.formatted(productCode, cleanedInputPath)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value("manual-jsonl"))
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.productName").value("清洗测试耳机"))
                .andExpect(jsonPath("$.analysisHandoffStatus").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.cleaningSummary.cleanedCount").value(2))
                .andExpect(jsonPath("$.cleaningSummary.placeholderContentCount").value(2))
                .andExpect(jsonPath("$.sampleReviews[0].content").value("蓝牙 偶尔断连"))
                .andReturn();

        String importBody = importResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String importedCleanedOutputPath = JsonPath.read(importBody, "$.cleanedOutputPath");
        assertEquals(cleanedOutputPath, importedCleanedOutputPath);
        assertTrue(countForProduct("""
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """, productCode) >= 1L);
        assertEquals("清洗测试耳机", productNameForProduct(productCode));
        assertEquals(0L, countForProductContent(productCode, "此用户未及时填写评价内容"));
    }

    @Test
    void intakeStatusShouldReflectJsonlFilesDatabaseReviewsAndLatestAnalysisJob() throws Exception {
        String productCode = "jd-status-" + UUID.randomUUID().toString().substring(0, 8);
        Path rawJsonl = Files.createTempFile("manual-status-raw-reviews-" + productCode, ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"st1","productCode":"%s","productName":"状态测试耳机","rating":2,"content":"<p>蓝牙&nbsp;偶尔断连</p>","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"st2","productCode":"%s","productName":"状态测试耳机","rating":5,"content":"佩戴舒适，重量轻","reviewTime":"2026-06-01 11:20:00"}
                """.formatted(productCode, productCode),
                StandardCharsets.UTF_8
        );
        String inputPath = rawJsonl.toString().replace("\\", "\\\\");

        MvcResult cleanResult = mockMvc.perform(post("/api/v1/reviews/clean-jsonl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "状态测试耳机",
                                  "platform": "jd",
                                  "inputPath": "%s"
                                }
                                """.formatted(productCode, inputPath)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/v1/reviews/intake-status")
                        .queryParam("productCode", productCode)
                        .queryParam("inputPath", rawJsonl.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(productCode))
                .andExpect(jsonPath("$.rawJsonlExists").value(true))
                .andExpect(jsonPath("$.cleanedJsonlExists").value(true))
                .andExpect(jsonPath("$.cleaningSummary.cleanedCount").value(2))
                .andExpect(jsonPath("$.importedReviewCount").value(0))
                .andExpect(jsonPath("$.recentReviews[0].content").value("蓝牙 偶尔断连"))
                .andExpect(jsonPath("$.stage").value("CLEANED"));

        String cleanedOutputPath = JsonPath.read(cleanResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.cleanedOutputPath");
        String cleanedInputPath = cleanedOutputPath.replace("\\", "\\\\");
        mockMvc.perform(post("/api/v1/reviews/import-jsonl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "状态测试耳机",
                                  "platform": "jd",
                                  "inputPath": "%s"
                                }
                                """.formatted(productCode, cleanedInputPath)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalReviewCount").value(2));

        MvcResult analysisResult = mockMvc.perform(post("/api/v1/analysis/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"%s\"}".formatted(productCode)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        String jobId = JsonPath.read(analysisResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.jobId");

        mockMvc.perform(get("/api/v1/reviews/intake-status")
                        .queryParam("productCode", productCode)
                        .queryParam("inputPath", rawJsonl.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedReviewCount").value(2))
                .andExpect(jsonPath("$.analyzedReviewCount").value(2))
                .andExpect(jsonPath("$.latestAnalysisJob.jobId").value(jobId))
                .andExpect(jsonPath("$.latestAnalysisJob.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latestAnalysisJob.progressPercent").value(100))
                .andExpect(jsonPath("$.recentReviews[0].content").value("佩戴舒适，重量轻"))
                .andExpect(jsonPath("$.stage").value("ANALYZED"));
    }

    @Test
    void jsonlFileDiscoveryShouldListRawFilesWithProductMetadata() throws Exception {
        String productCode = "jd-discovery-" + UUID.randomUUID().toString().substring(0, 8);
        Path outputDirectory = Path.of("crawler", "output");
        Files.createDirectories(outputDirectory);
        Path rawJsonl = outputDirectory.resolve("raw_reviews_" + productCode + ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"d1","productCode":"%s","productName":"发现测试耳机","rating":5,"content":"续航不错","reviewTime":"2026-06-01 10:20:00"}
                """.formatted(productCode),
                StandardCharsets.UTF_8
        );

        try {
            MvcResult result = mockMvc.perform(get("/api/v1/reviews/jsonl-files"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("raw_reviews_" + productCode + ".jsonl"));
            assertTrue(body.contains(productCode));
            assertTrue(body.contains("发现测试耳机"));
        } finally {
            Files.deleteIfExists(rawJsonl);
        }
    }

    @Test
    void directImportShouldFilterPlaceholderReviewsBeforePersisting() throws Exception {
        String productCode = "jd-direct-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/reviews/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "local-jsonl",
                                  "platform": "jd",
                                  "productCode": "%s",
                                  "reviews": [
                                    {
                                      "sourceReviewId": "direct-001",
                                      "rating": 5,
                                      "content": "此用户未及时填写评价内容",
                                      "reviewTime": "2026-06-01T10:20:00Z"
                                    },
                                    {
                                      "sourceReviewId": "direct-002",
                                      "rating": 2,
                                      "content": "此用户未及时填写评价内容 | [追评]: 连接经常断开",
                                      "reviewTime": "2026-06-02T10:20:00Z"
                                    }
                                  ]
                                }
                                """.formatted(productCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receivedCount").value(1));

        assertEquals(0L, countForProductContent(productCode, "此用户未及时填写评价内容"));
        assertEquals(1L, countForProductContent(productCode, "[追评]: 连接经常断开"));
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
    void materializationShouldReportStaleReviewIdsBeforeForeignKeyFailure() throws Exception {
        String productCode = "jd-stale-" + UUID.randomUUID().toString().substring(0, 8);
        importReviews(productCode);
        long staleReviewId = firstReviewIdForProduct(productCode);
        deleteRawReviewsForProduct(productCode);

        Materialization materialization = new Materialization(
                List.of(new ReviewAspectRecord(
                        staleReviewId,
                        "bluetooth",
                        "产品硬件",
                        "连接与稳定性",
                        "NEGATIVE",
                        new BigDecimal("0.1500"),
                        new BigDecimal("0.9000")
                )),
                List.of(new SemanticLabelRecord(
                        staleReviewId,
                        "bluetooth",
                        "NEGATIVE",
                        new BigDecimal("0.9000"),
                        "产品硬件",
                        "连接与稳定性",
                        "蓝牙断连",
                        "蓝牙连接偶尔断开",
                        3,
                        1L,
                        1
                )),
                List.of()
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> analysisMaterializationRepository.appendOutputs(productCode, materialization, materialization)
        );

        assertTrue(error.getMessage().contains(AnalysisMaterializationRepository.STALE_REVIEW_DATA_MESSAGE));
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
                                      "content": "佩戴舒适，重量轻，日常通勤很满意。",
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

    private long insertCrawlJob(String productCode, Path rawJsonl) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     """
                     INSERT INTO sync_jobs (
                       provider,
                       platform,
                       target_product_code,
                       status,
                       fetched_count,
                       analysis_handoff_status,
                       analysis_handoff_note,
                       source_url,
                       output_path,
                       captured_packet_count
                     )
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, "browser-crawler");
            ps.setString(2, "jd");
            ps.setString(3, productCode);
            ps.setString(4, "SUCCEEDED");
            ps.setInt(5, 1);
            ps.setString(6, "READY_FOR_IMPORT");
            ps.setString(7, "raw JSONL ready");
            ps.setString(8, "https://item.jd.com/100127936932.html");
            ps.setString(9, rawJsonl.toString());
            ps.setInt(10, 1);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
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

    private String productNameForProduct(String productCode) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT product_name FROM products WHERE product_code = ?")) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private long firstReviewIdForProduct(String productCode) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT r.id
                     FROM reviews_raw r
                     JOIN products p ON p.id = r.product_id
                     WHERE p.product_code = ?
                     ORDER BY r.id ASC
                     LIMIT 1
                     """)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void deleteRawReviewsForProduct(String productCode) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     DELETE FROM reviews_raw
                     WHERE product_id IN (
                         SELECT id FROM products WHERE product_code = ?
                     )
                     """)) {
            ps.setString(1, productCode);
            ps.executeUpdate();
        }
    }

    private long countForProductContent(String productCode, String content) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM reviews_raw r
                     JOIN products p ON p.id = r.product_id
                     WHERE p.product_code = ? AND r.content = ?
                     """)) {
            ps.setString(1, productCode);
            ps.setString(2, content);
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
