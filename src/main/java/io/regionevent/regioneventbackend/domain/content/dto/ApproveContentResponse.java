package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.ApproveContentResult;

public record ApproveContentResponse(
    Long contentId,
    String status,
    Instant publishAt,
    Instant approvedAt
) {

    public static ApproveContentResponse from(ApproveContentResult result) {
        return new ApproveContentResponse(
            result.contentId(),
            result.status().name(),
            result.publishAt(),
            result.approvedAt()
        );
    }
}
