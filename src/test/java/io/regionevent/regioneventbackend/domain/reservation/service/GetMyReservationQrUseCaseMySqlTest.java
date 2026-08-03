package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GetMyReservationQrUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final GetMyReservationQrUseCase getMyReservationQrUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    GetMyReservationQrUseCaseMySqlTest(
        GetMyReservationQrUseCase getMyReservationQrUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.getMyReservationQrUseCase = getMyReservationQrUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void QR_발급_시각은_MySQL_현재_시각을_사용한다() {
        Fixture fixture = createFixture();
        Instant checkinCloseAt = contentSessionRepository.findById(fixture.session().getSessionId())
            .orElseThrow()
            .getCheckinCloseAt();
        Instant before = currentTimestamp();

        MyReservationQrResult result = getMyReservationQrUseCase.get(
            fixture.user().getUserId(),
            fixture.reservation().getReservationId()
        );

        Instant after = currentTimestamp();
        assertThat(result.issuedAt()).isBetween(before, after);
        assertThat(result.expiresAt()).isEqualTo(checkinCloseAt);
        assertThat(result.qrToken()).startsWith("v1.qr-test-key.");
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        AppUser user = saveUser("visitor-" + suffix, AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.SUSPENDED,
            "QR 조회 콘텐츠",
            "QR 조회 콘텐츠 설명",
            "김해시",
            "10:00-18:00",
            "055-123-4567",
            "안내",
            "만 7세 이상",
            "편한 복장",
            "취소 정책",
            now.minusSeconds(60)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(120),
            10
        );
        session.approve(operator, now.minusSeconds(300));
        session = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now,
            now,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            user,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        return new Fixture(user, session, reservation);
    }

    private AppUser saveUser(String loginIdentifier, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier + "@example.com",
            "hashed-password",
            "방문자",
            "010-1234-5678",
            status
        ));
    }

    private Instant currentTimestamp() {
        BigDecimal epochSeconds = jdbcTemplate.queryForObject(
            "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))",
            BigDecimal.class
        );
        long seconds = epochSeconds.longValue();
        return Instant.ofEpochSecond(seconds, epochSeconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    private record Fixture(AppUser user, ContentSession session, Reservation reservation) {
    }
}
