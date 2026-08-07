package io.regionevent.regioneventbackend.domain.content.service;

public record EndContentReservationsSystemResult(
    Status status,
    long endingDelayMillis
) {

    public static EndContentReservationsSystemResult ended(long endingDelayMillis) {
        return new EndContentReservationsSystemResult(Status.ENDED, endingDelayMillis);
    }

    public static EndContentReservationsSystemResult skipped() {
        return new EndContentReservationsSystemResult(Status.SKIPPED, 0L);
    }

    public enum Status {
        ENDED,
        SKIPPED
    }
}
