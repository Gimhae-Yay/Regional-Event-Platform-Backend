package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAccountListProjection;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;

@Service
public class PlatformAdminAssignmentService {

    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;

    public PlatformAdminAssignmentService(PlatformAdminAssignmentRepository platformAdminAssignmentRepository) {
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
    }

    public PlatformAdminAssignment createActiveAssignment(
        AppUser appUser,
        PlatformAdminGrade grade
    ) {
        return platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(appUser, grade));
    }

    public Optional<PlatformAdminAssignment> findAssignmentForUpdate(Long userId) {
        return platformAdminAssignmentRepository.findByAppUserUserId(userId);
    }

    public List<PlatformAdminAssignment> findActiveSuperAdminsForUpdate() {
        return platformAdminAssignmentRepository.findByGradeAndStatus(
            PlatformAdminGrade.SUPER_ADMIN,
            PlatformAdminAssignmentStatus.ACTIVE
        );
    }

    public List<PlatformAdminAccountListProjection> findPlatformAdminAccountList() {
        return platformAdminAssignmentRepository.findPlatformAdminAccountList();
    }
}
