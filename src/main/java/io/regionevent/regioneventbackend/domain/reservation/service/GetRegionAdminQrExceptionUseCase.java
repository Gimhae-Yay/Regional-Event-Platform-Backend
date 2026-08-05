package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionAuditProjection;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetRegionAdminQrExceptionUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetRegionAdminQrExceptionUseCase.class);
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

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
        Long authorizedRegionId = null;
        try {
            validateId(userId);
            validateId(exceptionId);

            authorizedRegionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
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

            Long reservationId = findReservationId(audit, exceptionType);
            if (reservationId == null) {
                QrExceptionDetailResult result = QrExceptionDetailResult.unresolved(
                    audit.exceptionId(),
                    audit.regionId(),
                    exceptionType,
                    audit.result(),
                    audit.reasonCode(),
                    audit.occurredAt()
                );
                logResult(authorizedRegionId, exceptionId, SUCCESS_RESULT_CODE);
                return result;
            }

            ReservationReadResult readResult = findReservationReadResult(reservationId);
            ReservationReadSnapshot snapshot = readResult.snapshot();
            validateSameRegion(audit, snapshot);
            QrExceptionDetailResult result = new QrExceptionDetailResult(
                audit.exceptionId(),
                audit.regionId(),
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
            logResult(authorizedRegionId, exceptionId, SUCCESS_RESULT_CODE);
            return result;
        } catch (BusinessException exception) {
            logResult(authorizedRegionId, exceptionId, exception.getErrorCode().code());
            throw exception;
        }
    }

    private Long findReservationId(
        QrExceptionAuditProjection audit,
        QrExceptionType exceptionType
    ) {
        validateAuditTargetContract(audit, exceptionType);
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

    private void validateAuditTargetContract(
        QrExceptionAuditProjection audit,
        QrExceptionType exceptionType
    ) {
        if (exceptionType == QrExceptionType.MANUAL_CHECK_IN) {
            validateManualCheckInTargetContract(audit);
            return;
        }
        validateReservationTargetContract(audit);
    }

    private void validateManualCheckInTargetContract(QrExceptionAuditProjection audit) {
        if (audit.result() == AuditEventResult.SUCCESS) {
            validateVisitTargetContract(audit);
            return;
        }
        validateReservationTargetContract(audit);
    }

    private void validateReservationTargetContract(QrExceptionAuditProjection audit) {
        if (audit.targetType() != AuditEventTargetType.RESERVATION) {
            throw new IllegalStateException("qr exception audit target contract is inconsistent");
        }
    }

    private void validateVisitTargetContract(QrExceptionAuditProjection audit) {
        if (audit.targetType() != AuditEventTargetType.VISIT || audit.targetId() == null) {
            throw new IllegalStateException("qr exception audit target contract is inconsistent");
        }
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

    private void logResult(
        Long regionId,
        Long exceptionId,
        String resultCode
    ) {
        log.info(
            "QR exception detail read. requestId={}, regionId={}, exceptionId={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            exceptionId,
            resultCode
        );
    }
}
