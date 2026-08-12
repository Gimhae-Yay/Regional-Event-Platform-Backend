package io.regionevent.regioneventbackend.domain.mission.service;

public record EndMissionSystemResult(Status status) {

    public enum Status {
        ENDED,
        SKIPPED
    }

    public static EndMissionSystemResult ended() {
        return new EndMissionSystemResult(Status.ENDED);
    }

    public static EndMissionSystemResult skipped() {
        return new EndMissionSystemResult(Status.SKIPPED);
    }
}
