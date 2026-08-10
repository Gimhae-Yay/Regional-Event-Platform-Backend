package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
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
}
