package review.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import review.backend.api.dto.UxChangeComparisonRequest;
import review.backend.api.dto.UxChangeComparisonItem;
import review.backend.api.dto.UxChangeComparisonResponse;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.MaterializedUxChangeReviewRecord;
import review.backend.data.ProductRepository;
import review.backend.data.UxChangeComparisonRepository;
import review.backend.data.UxChangeComparisonRepository.UxChangeCheckpoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UxChangeComparisonServiceTest {

    @Mock
    private UxChangeComparisonRepository checkpointRepository;

    @Mock
    private AnalysisMaterializationRepository materializationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReviewAggregationService reviewAggregationService;

    private UxChangeComparisonService service;

    @BeforeEach
    void setUp() {
        service = new UxChangeComparisonService(
                checkpointRepository,
                materializationRepository,
                productRepository,
                reviewAggregationService
        );
    }

    @Test
    void createAndCompareShouldUseDefaultOneMonthWindowAndCalculateEachUxLabel() {
        LocalDate changeDate = LocalDate.parse("2026-06-01");
        UxChangeCheckpoint checkpoint = new UxChangeCheckpoint(
                9L,
                "jd-100127936932",
                changeDate,
                "ONE_MONTH",
                LocalDate.parse("2026-05-01"),
                changeDate,
                changeDate,
                LocalDate.parse("2026-07-01"),
                Instant.parse("2026-06-03T00:00:00Z")
        );

        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(checkpointRepository.save(
                "jd-100127936932",
                changeDate,
                "ONE_MONTH",
                LocalDate.parse("2026-05-01"),
                changeDate,
                changeDate,
                LocalDate.parse("2026-07-01")
        )).thenReturn(checkpoint);
        when(materializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(true);
        when(materializationRepository.findUxChangeReviews(
                "jd-100127936932",
                Instant.parse("2026-04-30T16:00:00Z"),
                Instant.parse("2026-06-30T16:00:00Z")
        )).thenReturn(List.of(
                review("battery", "产品硬件", "电池与续航", "2026-05-10T00:00:00Z", "NEGATIVE"),
                review("battery", "产品硬件", "电池与续航", "2026-05-11T00:00:00Z", "POSITIVE"),
                review("battery", "产品硬件", "电池与续航", "2026-06-01T00:00:00Z", "POSITIVE"),
                review("battery", "产品硬件", "电池与续航", "2026-06-12T00:00:00Z", "POSITIVE"),
                review("bluetooth", "产品硬件", "连接与稳定性", "2026-05-20T00:00:00Z", "POSITIVE"),
                review("bluetooth", "产品硬件", "连接与稳定性", "2026-06-10T00:00:00Z", "NEGATIVE")
        ));
        when(productRepository.findProductName("jd-100127936932")).thenReturn(Optional.of("小米 Buds 5 Pro"));

        UxChangeComparisonResponse response = service.createAndCompare(new UxChangeComparisonRequest(
                "jd-100127936932",
                changeDate,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals("success", response.state());
        assertEquals("9", response.checkpointId());
        assertEquals("小米 Buds 5 Pro", response.productName());
        assertEquals(LocalDate.parse("2026-05-01"), response.beforeWindow().start());
        assertEquals(LocalDate.parse("2026-07-01"), response.afterWindow().end());
        assertEquals(2, response.items().size());
        UxChangeComparisonItem battery = response.items().stream()
                .filter(item -> "电池与续航".equals(item.uxSecondaryLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, battery.beforeMentionCount());
        assertEquals(1, battery.beforeNegativeCount());
        assertEquals(0.5D, battery.beforeNegativeRate());
        assertEquals(2, battery.afterMentionCount());
        assertEquals(0D, battery.afterNegativeRate());
        assertEquals(0.5D, battery.improvementRate());
        assertTrue(battery.summary().contains("样本量不足"));
    }

    @Test
    void createAndCompareShouldAcceptFrontendCustomDayWindows() {
        LocalDate changeDate = LocalDate.parse("2026-06-05");
        UxChangeCheckpoint checkpoint = new UxChangeCheckpoint(
                10L,
                "jd-100127936932",
                changeDate,
                "CUSTOM",
                LocalDate.parse("2026-04-21"),
                changeDate,
                changeDate,
                LocalDate.parse("2026-06-25"),
                Instant.parse("2026-06-03T00:00:00Z")
        );

        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(checkpointRepository.save(
                "jd-100127936932",
                changeDate,
                "CUSTOM",
                LocalDate.parse("2026-04-21"),
                changeDate,
                changeDate,
                LocalDate.parse("2026-06-25")
        )).thenReturn(checkpoint);
        when(materializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(false);

        UxChangeComparisonResponse response = service.createAndCompare(new UxChangeComparisonRequest(
                "jd-100127936932",
                changeDate,
                "CUSTOM",
                45,
                20,
                null,
                null,
                null,
                null
        ));

        assertEquals("empty", response.state());
        assertEquals(LocalDate.parse("2026-04-21"), response.beforeWindow().start());
        assertEquals(LocalDate.parse("2026-06-25"), response.afterWindow().end());
        assertEquals("CUSTOM", response.windowPreset());
    }

    private MaterializedUxChangeReviewRecord review(
            String aspect,
            String uxPrimaryLabel,
            String uxSecondaryLabel,
            String reviewTime,
            String sentimentPolarity
    ) {
        return new MaterializedUxChangeReviewRecord(
                aspect,
                uxPrimaryLabel,
                uxSecondaryLabel,
                Instant.parse(reviewTime),
                sentimentPolarity
        );
    }
}
