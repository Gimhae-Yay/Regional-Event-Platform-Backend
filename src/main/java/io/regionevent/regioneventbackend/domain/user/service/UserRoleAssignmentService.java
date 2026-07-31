package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@Service
public class UserRoleAssignmentService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public UserRoleAssignmentService(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public void assignVisitor(AppUser user) {
        userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
    }
}
