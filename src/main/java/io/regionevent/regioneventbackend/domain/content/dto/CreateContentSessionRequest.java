package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CreateContentSessionRequest(
    @NotNull
    @JsonFormat(
        pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]XXX",
        without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    )
    OffsetDateTime startsAt,

    @NotNull
    @JsonFormat(
        pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]XXX",
        without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    )
    OffsetDateTime endsAt,

    @NotNull
    @JsonFormat(
        pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]XXX",
        without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    )
    OffsetDateTime checkinOpenAt,

    @NotNull
    @JsonFormat(
        pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]XXX",
        without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    )
    OffsetDateTime checkinCloseAt,

    @NotNull
    @Positive
    Integer capacity
) {
}
