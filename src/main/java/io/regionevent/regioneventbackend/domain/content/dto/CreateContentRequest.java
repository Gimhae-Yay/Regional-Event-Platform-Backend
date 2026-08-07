package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

public record CreateContentRequest(
    @NotBlank
    @Size(max = 255)
    String title,

    @NotBlank
    String description,

    @NotBlank
    @Size(max = 255)
    String locationText,

    @NotBlank
    String operatingHoursText,

    @NotBlank
    @Size(max = 255)
    String contactText,

    @NotBlank
    String precautions,

    @NotBlank
    @Size(max = 255)
    String ageRequirement,

    @NotBlank
    String materials,

    @NotBlank
    String cancellationPolicyText,

    @NotNull
    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    OffsetDateTime publishAt,

    @NotNull
    JsonNode representativeImageObjectId,

    @NotEmpty
    List<@Valid SessionRequest> sessions
) {

    public record SessionRequest(
        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime startsAt,

        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime endsAt,

        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime checkinOpenAt,

        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime checkinCloseAt,

        @NotNull
        @Positive
        Integer capacity
    ) {
    }
}
