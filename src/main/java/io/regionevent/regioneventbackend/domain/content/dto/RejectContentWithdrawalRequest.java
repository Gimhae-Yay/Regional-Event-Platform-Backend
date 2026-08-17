package io.regionevent.regioneventbackend.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectContentWithdrawalRequest(
    @NotBlank String reason
) {

    public RejectContentWithdrawalRequest {
        reason = reason == null ? null : reason.strip();
    }
}
