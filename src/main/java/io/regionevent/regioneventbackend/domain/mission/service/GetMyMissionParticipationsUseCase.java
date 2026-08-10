package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

@Service
public class GetMyMissionParticipationsUseCase {

    private final UserRoleAssignmentService userRoleAssignmentService;
    private final MissionParticipationReadService missionParticipationReadService;

    public GetMyMissionParticipationsUseCase(
        UserRoleAssignmentService userRoleAssignmentService,
        MissionParticipationReadService missionParticipationReadService
    ) {
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.missionParticipationReadService = missionParticipationReadService;
    }

    @Transactional(readOnly = true)
    public MyMissionParticipationListResult get(
        Long userId,
        MissionParticipationStatus status,
        int page,
        int size
    ) {
        UserRoleAssignment visitor = userRoleAssignmentService.findActiveVisitor(userId);
        Page<MissionParticipationSummary> summaries = missionParticipationReadService.findByUserIdAndStatus(
            visitor.getAppUser().getUserId(),
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
