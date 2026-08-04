package io.regionevent.regioneventbackend.domain.audit.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionReadProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class QrExceptionReadService {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(90);
    private static final String QR_CHECK_IN_PREFIX = "QR_CHECK_IN_";
    private static final String RESERVATION_LOOKUP_REASON_CODE = "QR_VERIFICATION_FAILED";
    private static final String MANUAL_CHECK_IN_PREFIX = "MANUAL_CHECK_IN_";
    private static final String QR_CHECK_IN_FAILURE = "QR_CHECK_IN_FAILURE";
    private static final String RESERVATION_NUMBER_LOOKUP = "RESERVATION_NUMBER_LOOKUP";
    private static final String MANUAL_CHECK_IN = "MANUAL_CHECK_IN";

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public QrExceptionReadService(
        AuditEventRepository auditEventRepository,
        Clock clock
    ) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QrExceptionPage findAll(
        Long regionId,
        Instant cursorOccurredAt,
        Long cursorAuditEventId,
        int size
    ) {
        Instant cutoff = Instant.now(clock).minus(RETENTION_PERIOD);
        validateCursorOccurredAt(cursorOccurredAt, cutoff);
        List<QrExceptionReadProjection> projections = auditEventRepository.findQrExceptionReadProjections(
            regionId,
            cutoff,
            cursorOccurredAt,
            cursorAuditEventId,
            QR_CHECK_IN_PREFIX,
            RESERVATION_LOOKUP_REASON_CODE,
            MANUAL_CHECK_IN_PREFIX,
            PageRequest.of(0, size + 1)
        );
        boolean hasNext = projections.size() > size;
        List<QrExceptionItem> items = projections.stream()
            .limit(size)
            .map(projection -> toItem(projection, regionId))
            .toList();
        return new QrExceptionPage(items, hasNext);
    }

    private void validateCursorOccurredAt(
        Instant cursorOccurredAt,
        Instant cutoff
    ) {
        if (cursorOccurredAt != null && cursorOccurredAt.isBefore(cutoff)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private QrExceptionItem toItem(
        QrExceptionReadProjection projection,
        Long regionId
    ) {
        String exceptionType = toExceptionType(projection.reasonCode());
        if (projection.targetId() == null) {
            return unresolvedItem(projection, exceptionType);
        }
        if (projection.targetType() == AuditEventTargetType.RESERVATION) {
            return reservationItem(projection, regionId, exceptionType);
        }
        if (projection.targetType() == AuditEventTargetType.VISIT) {
            return visitItem(projection, regionId, exceptionType);
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private QrExceptionItem unresolvedItem(
        QrExceptionReadProjection projection,
        String exceptionType
    ) {
        return new QrExceptionItem(
            projection.auditEventId(),
            exceptionType,
            projection.result().name(),
            projection.reasonCode(),
            false,
            null,
            null,
            null,
            projection.occurredAt()
        );
    }

    private QrExceptionItem reservationItem(
        QrExceptionReadProjection projection,
        Long regionId,
        String exceptionType
    ) {
        validateReservationProjection(projection, regionId);
        return new QrExceptionItem(
            projection.auditEventId(),
            exceptionType,
            projection.result().name(),
            projection.reasonCode(),
            true,
            projection.reservationId(),
            projection.reservationContentId(),
            projection.reservationSessionId(),
            projection.occurredAt()
        );
    }

    private QrExceptionItem visitItem(
        QrExceptionReadProjection projection,
        Long regionId,
        String exceptionType
    ) {
        validateVisitProjection(projection, regionId);
        return new QrExceptionItem(
            projection.auditEventId(),
            exceptionType,
            projection.result().name(),
            projection.reasonCode(),
            true,
            projection.visitReservationId(),
            projection.visitContentId(),
            projection.visitSessionId(),
            projection.occurredAt()
        );
    }

    private void validateReservationProjection(
        QrExceptionReadProjection projection,
        Long regionId
    ) {
        if (!sameId(projection.targetId(), projection.reservationId())
            || !sameId(regionId, projection.auditRegionId())
            || !sameId(regionId, projection.reservationRegionId())
            || !sameId(regionId, projection.reservationSessionRegionId())
            || !sameId(regionId, projection.reservationContentRegionId())
            || projection.reservationSessionId() == null
            || projection.reservationContentId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateVisitProjection(
        QrExceptionReadProjection projection,
        Long regionId
    ) {
        if (!sameId(projection.targetId(), projection.visitId())
            || !sameId(regionId, projection.auditRegionId())
            || !sameId(regionId, projection.visitRegionId())
            || !sameId(regionId, projection.visitReservationRegionId())
            || !sameId(regionId, projection.visitSessionRegionId())
            || !sameId(regionId, projection.visitContentRegionId())
            || projection.visitReservationId() == null
            || projection.visitSessionId() == null
            || projection.visitContentId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toExceptionType(String reasonCode) {
        if (reasonCode == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (reasonCode.startsWith(QR_CHECK_IN_PREFIX)) {
            return QR_CHECK_IN_FAILURE;
        }
        if (RESERVATION_LOOKUP_REASON_CODE.equals(reasonCode)) {
            return RESERVATION_NUMBER_LOOKUP;
        }
        if (reasonCode.startsWith(MANUAL_CHECK_IN_PREFIX)) {
            return MANUAL_CHECK_IN;
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private boolean sameId(Long expected, Long actual) {
        return expected != null && expected.equals(actual);
    }

    public record QrExceptionPage(
        List<QrExceptionItem> items,
        boolean hasNext
    ) {

        public QrExceptionPage {
            items = List.copyOf(items);
        }
    }

    public record QrExceptionItem(
        Long exceptionId,
        String exceptionType,
        String result,
        String reasonCode,
        boolean reservationResolved,
        Long reservationId,
        Long contentId,
        Long sessionId,
        Instant occurredAt
    ) {
    }
}
