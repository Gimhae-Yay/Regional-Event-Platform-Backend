package io.regionevent.regioneventbackend.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVisitReviewRequest(
    @NotNull
    @Min(1)
    @Max(5)
    Integer rating,
    @NotBlank
    @Size(max = 2_000)
    String reviewText
) {

    public CreateVisitReviewRequest {
        if (reviewText != null) {
            reviewText = reviewText.strip();
        }
    }
}
