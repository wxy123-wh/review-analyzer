package review.backend.api.dto;

import java.util.List;

public record ValidationResponse(
        List<ValidationItem> items,
        String state,
        String notice
) {
    public ValidationResponse(List<ValidationItem> items, String state) {
        this(items, state, null);
    }
}
