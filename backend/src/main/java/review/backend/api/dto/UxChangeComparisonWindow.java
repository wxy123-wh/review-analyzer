package review.backend.api.dto;

import java.time.LocalDate;

public record UxChangeComparisonWindow(
        LocalDate start,
        LocalDate end
) {
}
