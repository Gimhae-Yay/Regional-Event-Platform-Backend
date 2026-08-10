package io.regionevent.regioneventbackend.domain.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponResult;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
class CouponIssuanceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponRepository couponRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final EntityManager entityManager;

    @Autowired
    CouponIssuanceRepositoryTest(
        CouponIssuanceRepository couponIssuanceRepository,
        CouponRepository couponRepository,
        CouponPolicyRepository couponPolicyRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        EntityManager entityManager
    ) {
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.couponRepository = couponRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 쿠폰_식별자로_발급_이력을_조회하면_방문_근거와_상세_필드를_반환한다() {
        Fixture fixture = createFixture();
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            fixture.couponPolicy(), fixture.user(), NOW, NOW.plusSeconds(2_592_000)
        ));
        couponIssuanceRepository.saveAndFlush(new CouponIssuance(
            coupon,
            fixture.couponPolicy(),
            fixture.user(),
            fixture.visit(),
            null,
            null,
            "visit-" + coupon.getCouponId(),
            NOW
        ));
        entityManager.clear();

        AppUserService appUserService = mock(AppUserService.class);
        when(appUserService.findActiveUser(fixture.user().getUserId())).thenReturn(fixture.user());
        GetMyCouponUseCase getMyCouponUseCase = new GetMyCouponUseCase(
            appUserService,
            new CouponIssuanceService(couponIssuanceRepository)
        );
        GetMyCouponResult result = getMyCouponUseCase.find(fixture.user().getUserId(), coupon.getCouponId());

        assertThat(result.couponId()).isEqualTo(coupon.getCouponId());
        assertThat(result.couponPolicyId()).isEqualTo(fixture.couponPolicy().getCouponPolicyId());
        assertThat(result.sourceId()).isEqualTo(fixture.visit().getVisitId());
        assertThat(result.contentId()).isEqualTo(fixture.content().getContentId());
        assertThat(result.regionId()).isEqualTo(fixture.region().getRegionId());
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser user = appUserRepository.saveAndFlush(user("user-" + suffix + "@example.com"));
        AppUser operator = appUserRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
        Content content = contentRepository.saveAndFlush(new Content(
            region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
            "체험", "설명", "주소", "운영시간", "055-000-0000", "안내", "연령", "복장", "취소", NOW
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
        return new Fixture(region, user, content, visit, couponPolicyRepository.saveAndFlush(couponPolicy));
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE);
    }

    private record Fixture(
        Region region,
        AppUser user,
        Content content,
        Visit visit,
        CouponPolicy couponPolicy
    ) {
    }
}
