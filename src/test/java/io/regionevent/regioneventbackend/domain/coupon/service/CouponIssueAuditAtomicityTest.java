package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookRewardGrantService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    CouponIssueUseCase.class,
    CouponIssueDuplicateReadService.class,
    CouponPolicyService.class,
    CouponService.class,
    CouponIssuanceService.class,
    CouponStatusHistoryService.class,
    VisitService.class,
    StampbookRewardGrantService.class,
    AppUserService.class,
    CouponIssueAuditAtomicityTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class CouponIssueAuditAtomicityTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final CouponIssueUseCase couponIssueUseCase;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoBean
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    @Autowired
    CouponIssueAuditAtomicityTest(
        CouponIssueUseCase couponIssueUseCase,
        CouponRepository couponRepository,
        CouponIssuanceRepository couponIssuanceRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        CouponPolicyRepository couponPolicyRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.couponIssueUseCase = couponIssueUseCase;
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void 성공_감사_기록에_실패하면_쿠폰_발급을_롤백하고_실패_감사를_요청한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> couponIssueUseCase.issue(
            fixture.user().getUserId(),
            fixture.couponPolicy().getCouponPolicyId(),
            new CouponIssueUseCase.CouponIssueCommand(CouponIssuanceType.VISIT, fixture.visit().getVisitId()),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit storage failure");

        assertThat(couponRepository.count()).isZero();
        assertThat(couponIssuanceRepository.count()).isZero();
        assertThat(couponStatusHistoryRepository.count()).isZero();
        verify(recordFailedAuditEventUseCase).record(any(AuditEventCommand.class));
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
            AppUser user = appUserRepository.saveAndFlush(user("user-" + suffix + "@example.com"));
            AppUser operator = appUserRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
            Content content = contentRepository.saveAndFlush(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "체험", "설명", "주소", "운영시간", "055-000-0000", "안내", "전체", "복장", "정책", NOW
            ));
            ContentSession session = new ContentSession(
                content, region, NOW.plusSeconds(3_600), NOW.plusSeconds(10_800),
                NOW.plusSeconds(1_800), NOW.plusSeconds(9_000), 20
            );
            session.approve(operator, NOW);
            session = contentSessionRepository.saveAndFlush(session);
            CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
                region, session, user, 1, CapacityHoldStatus.CONSUMED, NOW, NOW.plusSeconds(300), null, null
            ));
            Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                "R-" + suffix, "qr-" + suffix, region, hold, session, user,
                ReservationStatus.CONFIRMED, NOW, null, null, null, null
            ));
            Visit visit = visitRepository.saveAndFlush(new Visit(
                region, reservation, user, content, session, operator, CheckinMethod.QR, NOW
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content, region, "방문 쿠폰", null, CouponIssuanceType.VISIT,
                3_000L, 10_000L, 30, NOW.minusSeconds(1), NOW.plusSeconds(3_600), 10L
            ));
            couponPolicy.publish(NOW.minusSeconds(1));
            couponPolicyRepository.saveAndFlush(couponPolicy);
            return new Fixture(user, visit, couponPolicy);
        });
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE);
    }

    private record Fixture(AppUser user, Visit visit, CouponPolicy couponPolicy) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
