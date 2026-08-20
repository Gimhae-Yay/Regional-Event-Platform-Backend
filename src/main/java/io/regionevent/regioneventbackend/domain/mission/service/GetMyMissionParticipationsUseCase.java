package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyMissionParticipationsUseCase {

    private final AppUserService appUserService;
    private final MissionParticipationReadService missionParticipationReadService;

    public GetMyMissionParticipationsUseCase(
        AppUserService appUserService,
        MissionParticipationReadService missionParticipationReadService
    ) {
        this.appUserService = appUserService;
        this.missionParticipationReadService = missionParticipationReadService;
    }

    @Transactional(readOnly = true)
    public MyMissionParticipationListResult get(
        Long userId,
        MissionParticipationStatus status,
        int page,
        int size
    ) {
        appUserService.findActiveOrdinaryUser(userId);
        Page<MissionParticipationSummary> summaries = missionParticipationReadService.findByUserIdAndStatus(
            userId,
            status,
            PageRequest.of(page, size)
        );
        List<MyMissionParticipationListResult.Participation> content = summaries.getContent().stream()
            .map(MyMissionParticipationListResult.Participation::from)
            .toList();
        return new MyMissionParticipationListResult(
            content,
            summaries.getNumber(),
            summaries.getSize(),
            summaries.getTotalElements(),
            summaries.getTotalPages()
        );
    }
}
