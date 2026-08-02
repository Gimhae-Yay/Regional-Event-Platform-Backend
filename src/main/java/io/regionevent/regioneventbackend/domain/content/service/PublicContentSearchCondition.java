package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record PublicContentSearchCondition(
    Long regionId,
    ContentType contentType,
    Boolean reservationAvailable
) {
}
