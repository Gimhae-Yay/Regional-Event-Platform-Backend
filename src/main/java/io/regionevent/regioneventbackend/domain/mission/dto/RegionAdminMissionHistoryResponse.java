package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.audit.service.MissionHistoryReadResult;

public record RegionAdminMissionHistoryResponse(
    String missionId,
    List<HistoryResponse> histories
) {

    public RegionAdminMissionHistoryResponse {
        histories = List.copyOf(histories);
    }

    public static RegionAdminMissionHistoryResponse from(
        Long missionId,
        List<MissionHistoryReadResult> histories
    ) {
        return new RegionAdminMissionHistoryResponse(
            missionId.toString(),
            histories.stream()
                .map(HistoryResponse::from)
                .toList()
        );
    }

    public record HistoryResponse(
        String auditEventId,
        String action,
        String previousStatus,
        String nextStatus,
        String result,
        String reasonCode,
        String actorKind,
        String actorUserId,
        Instant recordedAt
    ) {

        private static HistoryResponse from(MissionHistoryReadResult history) {
            return new HistoryResponse(
                history.auditEventId().toString(),
                history.action(),
                history.previousStatus(),
                history.nextStatus(),
                history.result().name(),
                history.reasonCode(),
                history.actorKind(),
                history.actorUserId() == null ? null : history.actorUserId().toString(),
                history.recordedAt()
            );
        }
    }
}
