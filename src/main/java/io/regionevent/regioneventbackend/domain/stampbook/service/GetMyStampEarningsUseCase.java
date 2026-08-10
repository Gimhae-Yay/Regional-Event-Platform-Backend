package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

@Service
public class GetMyStampEarningsUseCase {

    private final UserRoleAssignmentService userRoleAssignmentService;
    private final StampbookReadService stampbookReadService;

    public GetMyStampEarningsUseCase(
        UserRoleAssignmentService userRoleAssignmentService,
        StampbookReadService stampbookReadService
    ) {
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.stampbookReadService = stampbookReadService;
    }

    @Transactional(readOnly = true)
    public MyStampEarningsResult find(
        Long userId,
        Long stampbookId
    ) {
        userRoleAssignmentService.findActiveVisitor(userId);
        return stampbookReadService.findMyStampEarnings(userId, stampbookId);
    }
}
