package io.regionevent.regioneventbackend.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestContentWithdrawalRequest(
    @NotBlank String reason
) {

    public RequestContentWithdrawalRequest {
        reason = reason == null ? null : reason.strip();
    }
}
