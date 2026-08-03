package io.regionevent.regioneventbackend.domain.visit.service;

import io.regionevent.regioneventbackend.domain.visit.dto.CheckInResponse;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public record CheckInResult(
    CheckInResponse response,
    ErrorCode errorCode
) {

    public static CheckInResult success(CheckInResponse response) {
        return new CheckInResult(response, null);
    }

    public static CheckInResult failure(ErrorCode errorCode) {
        return new CheckInResult(null, errorCode);
    }

    public boolean isSuccessful() {
        return errorCode == null;
    }
}
