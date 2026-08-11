package io.regionevent.regioneventbackend.domain.mission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

public record EndOperatorMissionRequest(
    @JsonDeserialize(using = CreateOperatorMissionRequest.StringValueDeserializer.class)
    @NotNull
    @Size(max = 64)
    String reasonCode
) {
}
