package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateContentSessionRequest(
    @NotNull
    @JsonDeserialize(using = SeoulOffsetDateTimeDeserializer.class)
    OffsetDateTime startsAt,

    @NotNull
    @JsonDeserialize(using = SeoulOffsetDateTimeDeserializer.class)
    OffsetDateTime endsAt,

    @NotNull
    @JsonDeserialize(using = SeoulOffsetDateTimeDeserializer.class)
    OffsetDateTime checkinOpenAt,

    @NotNull
    @JsonDeserialize(using = SeoulOffsetDateTimeDeserializer.class)
    OffsetDateTime checkinCloseAt,

    @NotNull
    @Positive
    Integer capacity
) {
}
