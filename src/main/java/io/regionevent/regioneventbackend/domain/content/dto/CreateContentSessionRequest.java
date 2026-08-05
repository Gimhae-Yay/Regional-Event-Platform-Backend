package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CreateContentSessionRequest(
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
