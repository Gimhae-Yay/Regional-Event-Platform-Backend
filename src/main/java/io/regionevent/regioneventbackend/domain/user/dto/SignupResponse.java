package io.regionevent.regioneventbackend.domain.user.dto;

public record SignupResponse(
    String userId,
    String requestedRole,
    String assignedRole,
    String operatorApplicationStatus
) {
}
