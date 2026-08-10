package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

@Service
public class GetMyStampbooksUseCase {

    private final UserRoleAssignmentService userRoleAssignmentService;
    private final StampbookReadService stampbookReadService;

    public GetMyStampbooksUseCase(
        UserRoleAssignmentService userRoleAssignmentService,
        StampbookReadService stampbookReadService
    ) {
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.stampbookReadService = stampbookReadService;
    }

    @Transactional(readOnly = true)
    public List<MyStampbookListResult> findAll(Long userId) {
        userRoleAssignmentService.findActiveVisitor(userId);
        return stampbookReadService.findMyStampbooks(userId);
    }
}
