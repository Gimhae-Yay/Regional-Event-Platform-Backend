package io.regionevent.regioneventbackend.domain.mission.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum MissionEarlyEndReasonCode {
    MISSION_OPERATION_SCHEDULE_CHANGED,
    MISSION_TARGET_CONTENT_UNAVAILABLE,
    MISSION_REWARD_POLICY_UNAVAILABLE,
    MISSION_OPERATION_SAFETY_CONCERN;

    private static final Set<String> SUPPORTED_CODES = Arrays.stream(values())
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());

    public static boolean isSupported(String value) {
        return SUPPORTED_CODES.contains(value);
    }
}
