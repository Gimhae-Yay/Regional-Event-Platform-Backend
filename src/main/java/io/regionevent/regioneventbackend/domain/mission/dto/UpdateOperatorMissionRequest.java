package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.UniqueElements;

import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateOperatorMissionRequest(
    @JsonDeserialize(using = CreateOperatorMissionRequest.StringValueDeserializer.class)
    String title,

    @JsonDeserialize(using = CreateOperatorMissionRequest.StringValueDeserializer.class)
    @NotBlank
    String conditionType,

    @JsonDeserialize(using = CreateOperatorMissionRequest.IntegerValueDeserializer.class)
    Integer requiredVisitCount,

    @JsonDeserialize(contentUsing = CreateOperatorMissionRequest.StringValueDeserializer.class)
    @UniqueElements
    List<
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String
    > targetContentIds,

    @JsonDeserialize(using = CreateOperatorMissionRequest.StringValueDeserializer.class)
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    String rewardCouponPolicyId,

    @JsonDeserialize(using = CreateOperatorMissionRequest.OffsetDateTimeValueDeserializer.class)
    @NotNull
    OffsetDateTime endsAt
) {
}
