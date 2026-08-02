package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

public record CreateContentRevisionRequest(
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

    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    OffsetDateTime publishAt,

    JsonNode representativeImageObjectId
) {
}
