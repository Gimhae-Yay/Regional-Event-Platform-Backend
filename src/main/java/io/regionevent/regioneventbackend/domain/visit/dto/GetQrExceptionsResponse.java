package io.regionevent.regioneventbackend.domain.visit.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.audit.service.QrExceptionReadService.QrExceptionItem;
import io.regionevent.regioneventbackend.domain.audit.service.QrExceptionReadService.QrExceptionPage;

public record GetQrExceptionsResponse(
    List<QrExceptionResponse> exceptions,
    String nextCursor,
    boolean hasNext
) {

    public GetQrExceptionsResponse {
        exceptions = List.copyOf(exceptions);
    }

    public static GetQrExceptionsResponse from(
        QrExceptionPage page,
        String nextCursor
    ) {
        return new GetQrExceptionsResponse(
            page.items().stream()
                .map(QrExceptionResponse::from)
                .toList(),
            nextCursor,
            page.hasNext()
        );
    }

    public record QrExceptionResponse(
        String exceptionId,
        String exceptionType,
        String result,
        String reasonCode,
        boolean reservationResolved,
        String reservationId,
        String contentId,
        String sessionId,
        Instant occurredAt
    ) {

        private static QrExceptionResponse from(QrExceptionItem item) {
            return new QrExceptionResponse(
                item.exceptionId().toString(),
                item.exceptionType(),
                item.result(),
                item.reasonCode(),
                item.reservationResolved(),
                toStringOrNull(item.reservationId()),
                toStringOrNull(item.contentId()),
                toStringOrNull(item.sessionId()),
                item.occurredAt()
            );
        }

        private static String toStringOrNull(Long value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }
    }
}
