package review.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import review.backend.api.dto.CrawlImportResponse;
import review.backend.data.AnalysisJobRepository;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.DataQualityRepository;
import review.backend.data.ExternalReviewRawRepository;
import review.backend.data.SyncJobRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewImportServiceTest {

    @Mock
    private ExternalReviewRawRepository externalReviewRawRepository;

    @Mock
    private AnalysisMaterializationRepository analysisMaterializationRepository;

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private DataQualityRepository dataQualityRepository;

    @Mock
    private SyncJobRepository syncJobRepository;

    @TempDir
    private Path tempDir;

    @Test
    void cleanJsonlFileShouldReturnCleaningArtifactsWithoutPersisting() throws Exception {
        String productCode = "jd-clean-unit";
        Path rawJsonl = tempDir.resolve("raw_reviews_" + productCode + ".jsonl");
        Files.writeString(
                rawJsonl,
                """
                {"source":"jd","sourceReviewId":"u1","productCode":"jd-other","productName":"单元测试耳机","rating":2,"content":"<p>蓝牙&nbsp;偶尔断连</p>","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"u1","productCode":"jd-other","productName":"单元测试耳机","rating":2,"content":"重复评论","reviewTime":"2026-06-01 10:20:00"}
                {"source":"jd","sourceReviewId":"u2","productCode":"jd-other","productName":"单元测试耳机","rating":5,"content":"此用户未及时填写评价内容","reviewTime":"2026-06-01 11:20:00"}
                {"source":"jd","sourceReviewId":"u3","productCode":"jd-other","productName":"单元测试耳机","rating":1,"content":"此用户未及时填写评价内容 | [追评]: 蓝牙经常断连","reviewTime":"2026-06-01 12:20:00"}
                """,
                StandardCharsets.UTF_8
        );
        ReviewImportService service = new ReviewImportService(
                externalReviewRawRepository,
                analysisMaterializationRepository,
                analysisJobRepository,
                dataQualityRepository,
                syncJobRepository,
                new ObjectMapper()
        );

        CrawlImportResponse response = service.cleanJsonlFile(
                "manual-jsonl-clean",
                productCode,
                "单元测试耳机",
                "jd",
                rawJsonl
        );

        assertEquals("manual-jsonl-clean", response.jobId());
        assertEquals(null, response.importJobId());
        assertEquals(productCode, response.productCode());
        assertEquals("单元测试耳机", response.productName());
        assertEquals("READY_FOR_TAXONOMY_BINDING", response.analysisHandoffStatus());
        assertEquals(2, response.receivedCount());
        assertEquals(0, response.insertedReviewCount());
        assertEquals(0, response.updatedReviewCount());
        assertEquals(0, response.totalReviewCount());
        assertEquals(4, response.cleaningSummary().get("rawCount"));
        assertEquals(2, response.cleaningSummary().get("cleanedCount"));
        assertEquals(1, response.cleaningSummary().get("exactDuplicateCount"));
        assertEquals(2, response.cleaningSummary().get("placeholderContentCount"));
        assertEquals("蓝牙 偶尔断连", response.sampleReviews().getFirst().content());
        assertTrue(Files.exists(Path.of(response.cleanedOutputPath())));
        assertTrue(Files.exists(Path.of(response.removedOutputPath())));
        assertTrue(Files.exists(Path.of(response.cleaningSummaryPath())));
        verifyNoInteractions(
                externalReviewRawRepository,
                analysisMaterializationRepository,
                analysisJobRepository,
                dataQualityRepository,
                syncJobRepository
        );
    }

    @Test
    void replaceExistingImportShouldBeBlockedWhileAnalysisJobIsActive() {
        ReviewImportService service = new ReviewImportService(
                externalReviewRawRepository,
                analysisMaterializationRepository,
                analysisJobRepository,
                dataQualityRepository,
                syncJobRepository,
                new ObjectMapper()
        );
        when(analysisJobRepository.hasActiveJobForProduct("jd-active-unit")).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.importJsonlFile(
                "manual-jsonl",
                "jd-active-unit",
                "分析中耳机",
                "jd",
                tempDir.resolve("cleaned_reviews_jd-active-unit.jsonl"),
                true
        ));

        assertTrue(error.getMessage().contains("仍有 LLM 分析任务正在运行"));
        verify(analysisJobRepository).hasActiveJobForProduct("jd-active-unit");
        verifyNoInteractions(externalReviewRawRepository, analysisMaterializationRepository, dataQualityRepository, syncJobRepository);
    }
}
