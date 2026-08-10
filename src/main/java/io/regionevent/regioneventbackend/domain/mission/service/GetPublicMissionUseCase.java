package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;

@Service
public class GetPublicMissionUseCase {

    private final MissionService missionService;
    private final MissionParticipationReadService missionParticipationReadService;
    private final Clock clock;

    public GetPublicMissionUseCase(
        MissionService missionService,
        MissionParticipationReadService missionParticipationReadService,
        Clock clock
    ) {
        this.missionService = missionService;
        this.missionParticipationReadService = missionParticipationReadService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicMissionDetailResult get(Long missionId, Long userId) {
        Instant now = Instant.now(clock);
        Mission mission = missionService.findPublicMissionDetail(missionId, now);
        MissionParticipationSummary participation = userId == null
            ? null
            : missionParticipationReadService.findSummaryByMissionIdAndUserId(missionId, userId).orElse(null);
        return new PublicMissionDetailResult(mission, participation);
    }
}
