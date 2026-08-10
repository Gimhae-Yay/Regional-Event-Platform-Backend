package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.UniqueElements;

public record CreateOperatorMissionRequest(
    @NotBlank
    String conditionType,

    Integer requiredVisitCount,

    @UniqueElements
    List<
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String
    > targetContentIds,

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    String rewardCouponPolicyId,

    @NotNull
    OffsetDateTime endsAt
) {
}
