package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

public record UpdateContentRevisionRequest(
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
    @PositiveOrZero
    Integer reservationPrice,

    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    OffsetDateTime publishAt,

    JsonNode representativeImageObjectId
) {

    public UpdateContentRevisionRequest(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        OffsetDateTime publishAt,
        JsonNode representativeImageObjectId
    ) {
        this(
            title, description, locationText, operatingHoursText, contactText, precautions,
            ageRequirement, materials, cancellationPolicyText, 0, publishAt,
            representativeImageObjectId
        );
    }
}
