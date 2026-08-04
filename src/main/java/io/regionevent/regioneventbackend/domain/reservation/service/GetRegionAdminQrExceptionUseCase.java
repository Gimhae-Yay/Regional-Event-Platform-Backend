package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionAuditProjection;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetRegionAdminQrExceptionUseCase {

    private final AuditEventRepository auditEventRepository;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ReservationReadService reservationReadService;
    private final ReservationParticipantMasker reservationParticipantMasker;
    private final VisitService visitService;

    public GetRegionAdminQrExceptionUseCase(
        AuditEventRepository auditEventRepository,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ReservationReadService reservationReadService,
        ReservationParticipantMasker reservationParticipantMasker,
        VisitService visitService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.reservationReadService = reservationReadService;
        this.reservationParticipantMasker = reservationParticipantMasker;
        this.visitService = visitService;
    }

    @Transactional(readOnly = true)
    public QrExceptionDetailResult get(Long userId, Long exceptionId) {
        validateId(userId);
        validateId(exceptionId);

        Long authorizedRegionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        QrExceptionAuditProjection audit = auditEventRepository.findQrExceptionAuditProjectionById(exceptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        QrExceptionType exceptionType = QrExceptionType.findByReasonCode(audit.reasonCode())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (audit.regionId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(authorizedRegionId, audit.regionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long reservationId = findReservationId(audit);
        if (reservationId == null) {
            return QrExceptionDetailResult.unresolved(
                audit.exceptionId(),
                exceptionType,
                audit.result(),
                audit.reasonCode(),
                audit.occurredAt()
            );
        }

        ReservationReadResult readResult = findReservationReadResult(reservationId);
        ReservationReadSnapshot snapshot = readResult.snapshot();
        validateSameRegion(audit, snapshot);
        return new QrExceptionDetailResult(
            audit.exceptionId(),
            exceptionType,
            audit.result(),
            audit.reasonCode(),
            audit.occurredAt(),
            true,
            new QrExceptionDetailResult.ReservationInfo(
                snapshot.reservation(),
                snapshot.session(),
                snapshot.content(),
                snapshot.participant().userId() != null,
                reservationParticipantMasker.mask(snapshot.participant()),
                readResult.checkIn()
            )
        );
    }

    private Long findReservationId(QrExceptionAuditProjection audit) {
        if (audit.targetId() == null) {
            return null;
        }
        if (audit.targetType() == AuditEventTargetType.RESERVATION) {
            return audit.targetId();
        }
        if (audit.targetType() == AuditEventTargetType.VISIT) {
            return visitService.findReservationIdByVisitId(audit.targetId())
                .orElseThrow(() -> new IllegalStateException("qr exception visit target does not exist"));
        }
        throw new IllegalStateException("qr exception target type is inconsistent");
    }

    private ReservationReadResult findReservationReadResult(Long reservationId) {
        try {
            return reservationReadService.findByReservationId(reservationId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.NOT_FOUND) {
                throw new IllegalStateException("qr exception reservation target does not exist", exception);
            }
            throw exception;
        }
    }

    private void validateSameRegion(
        QrExceptionAuditProjection audit,
        ReservationReadSnapshot snapshot
    ) {
        if (!Objects.equals(audit.regionId(), snapshot.reservation().regionId())
            || !Objects.equals(audit.regionId(), snapshot.session().regionId())
            || !Objects.equals(audit.regionId(), snapshot.content().regionId())) {
            throw new IllegalStateException("qr exception reservation relation is inconsistent");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
