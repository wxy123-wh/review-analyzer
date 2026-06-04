package review.backend.api.dto;

import java.time.LocalDate;

public record UxChangeComparisonRequest(
        String productCode,
        LocalDate changeDate,
        String windowPreset,
        Integer customBeforeDays,
        Integer customAfterDays,
        LocalDate beforeStart,
        LocalDate beforeEnd,
        LocalDate afterStart,
        LocalDate afterEnd
) {
}
