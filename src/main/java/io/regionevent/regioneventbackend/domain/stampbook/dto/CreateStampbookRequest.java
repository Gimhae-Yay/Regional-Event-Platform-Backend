package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.UniqueElements;

public record CreateStampbookRequest(
    @NotBlank
    @Size(max = 100)
    String title,

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    String regionId,

    @NotEmpty
    @UniqueElements
    List<
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String
    > contentIds,

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    String rewardCouponPolicyId,

    @NotBlank
    @Size(max = 500)
    String reason
) {

    public CreateStampbookRequest {
        title = title == null ? null : title.strip();
        reason = reason == null ? null : reason.strip();
    }
}
