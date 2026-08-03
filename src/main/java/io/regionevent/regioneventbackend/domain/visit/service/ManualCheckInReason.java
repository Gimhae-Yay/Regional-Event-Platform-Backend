package io.regionevent.regioneventbackend.domain.visit.service;

import java.util.Arrays;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public enum ManualCheckInReason {
    QR_NOT_AVAILABLE,
    QR_SCAN_FAILED;

    public static ManualCheckInReason from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return Arrays.stream(values())
            .filter(reason -> reason.name().equals(value))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }
}
