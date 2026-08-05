package io.regionevent.regioneventbackend.support.jpa;

import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailureAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyProperties;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.operator.service.ApproveOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.domain.operator.service.OperatorApplicationService;
import io.regionevent.regioneventbackend.domain.operator.service.RejectOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationHasher;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationParticipantMasker;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadIntegrityValidator;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchOperatorReservationByNumberUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchRegionAdminReservationByNumberUseCase;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

@TestConfiguration
@Import({
    ContentAtomicityJpaTestConfiguration.class,
    ApproveOperatorApplicationUseCase.class,
    RejectOperatorApplicationUseCase.class,
    SearchOperatorReservationByNumberUseCase.class,
    SearchRegionAdminReservationByNumberUseCase.class,
    ReservationCancellationUseCase.class,
    ReservationConfirmationUseCase.class,
    OperatorApplicationService.class,
    AppUserService.class,
    UserRoleAssignmentService.class,
    RegionAdminAuthorizationService.class,
    OperatorAuthorizationService.class,
    ReservationReadService.class,
    ReservationReadIntegrityValidator.class,
    ReservationParticipantMasker.class,
    ReservationService.class,
    ContentService.class,
    ContentSessionService.class,
    CapacityHoldService.class,
    IdempotencyService.class,
    ReservationConfirmationHasher.class,
    RecordAuditEventUseCase.class,
    RecordFailedAuditEventUseCase.class,
    RecordFailureAuditEventUseCase.class,
    AuditEventService.class,
    AuditEventActorLinkService.class
})
public class OperatorReservationAtomicityJpaTestConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return mock(PasswordEncoder.class);
    }

    @Bean
    IdempotencyProperties idempotencyProperties() {
        return new IdempotencyProperties(
            Duration.ofHours(24),
            Duration.ofHours(1),
            Duration.ofHours(1),
            3
        );
    }
}
