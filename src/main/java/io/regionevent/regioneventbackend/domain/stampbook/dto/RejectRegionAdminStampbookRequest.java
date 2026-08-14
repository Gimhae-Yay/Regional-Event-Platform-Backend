package io.regionevent.regioneventbackend.domain.stampbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRegionAdminStampbookRequest(
    @NotBlank
    @Size(max = 500)
    String reason
) {

    public RejectRegionAdminStampbookRequest {
        reason = reason == null ? null : reason.strip();
    }
}
