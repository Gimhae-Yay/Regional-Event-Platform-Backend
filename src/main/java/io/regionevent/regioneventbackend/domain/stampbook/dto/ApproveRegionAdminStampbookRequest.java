package io.regionevent.regioneventbackend.domain.stampbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveRegionAdminStampbookRequest(
    @NotBlank
    @Size(max = 500)
    String reason
) {

    public ApproveRegionAdminStampbookRequest {
        reason = reason == null ? null : reason.strip();
    }
}
