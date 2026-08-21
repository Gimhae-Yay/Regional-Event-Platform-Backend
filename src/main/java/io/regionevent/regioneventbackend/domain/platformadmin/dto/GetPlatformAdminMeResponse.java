package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;

public record GetPlatformAdminMeResponse(
    String userId,
    String grade
) {

    public static GetPlatformAdminMeResponse from(Long userId, PlatformAdminGrade grade) {
        return new GetPlatformAdminMeResponse(userId.toString(), grade.name());
    }
}
