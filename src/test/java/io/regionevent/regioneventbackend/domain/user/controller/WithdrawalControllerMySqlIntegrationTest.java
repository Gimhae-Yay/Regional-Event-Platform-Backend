package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CreateReservationHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationQrUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(WithdrawalControllerMySqlIntegrationTest.WithdrawalMySqlTestConfiguration.class)
class WithdrawalControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String WITHDRAWAL_PATH = "/api/v1/auth/delete";
    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final WithdrawUserUseCase withdrawUserUseCase;
    private final RefreshTokenStore refreshTokenStore;
    private final CreateReservationHoldUseCase createReservationHoldUseCase;
    private final ReservationConfirmationUseCase reservationConfirmationUseCase;
    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final GetMyReservationQrUseCase getMyReservationQrUseCase;

    @Autowired
    WithdrawalControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        WithdrawUserUseCase withdrawUserUseCase,
        RefreshTokenStore refreshTokenStore,
        CreateReservationHoldUseCase createReservationHoldUseCase,
        ReservationConfirmationUseCase reservationConfirmationUseCase,
        ReservationCancellationUseCase reservationCancellationUseCase,
        GetMyReservationQrUseCase getMyReservationQrUseCase
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.withdrawUserUseCase = withdrawUserUseCase;
        this.refreshTokenStore = refreshTokenStore;
        this.createReservationHoldUseCase = createReservationHoldUseCase;
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
        this.reservationCancellationUseCase = reservationCancellationUseCase;
        this.getMyReservationQrUseCase = getMyReservationQrUseCase;
    }

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore);
    }

    @Test
    void withdraw_withActiveHoldAndConfirmedReservation_terminatesBothAndRestoresCapacity() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId())))
            .andExpect(status().isOk());

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getUser()).isNull();
                assertThat(hold.getInvalidationReason()).isEqualTo("USER_WITHDRAWAL");
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getUser()).isNull();
                assertThat(reservation.getCancellationReason()).isEqualTo("USER_WITHDRAWAL");
                assertThat(reservation.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
    }

    @Test
    @Timeout(10)
    void withdraw_whenConcurrentRequestWaits_returnsUnauthenticatedAfterFirstRequestDeletesAccount() throws Exception {
        AppUser user = saveUser("visitor-" + Long.toUnsignedString(System.nanoTime()) + "@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        CountDownLatch firstRequestEnteredRedis = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstRequestEnteredRedis.countDown();
            await(releaseFirstRequest);
            return null;
        }).when(refreshTokenStore).revokeAllFamilies(anyLong());

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> firstRequest = executorService.submit(() -> withdrawUserUseCase.withdraw(user.getUserId()));
            assertThat(firstRequestEnteredRedis.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> secondRequest = executorService.submit(() -> withdraw(user.getUserId()));
            assertThat(secondRequest.isDone()).isFalse();

            releaseFirstRequest.countDown();
            firstRequest.get(3, TimeUnit.SECONDS);
            assertThat(secondRequest.get(3, TimeUnit.SECONDS)).isEqualTo(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationHoldCreationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> createReservationHoldUseCase.create(
                fixture.user().getUserId(),
                new CreateReservationHoldRequest(fixture.session().getSessionId().toString(), 1)
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationConfirmationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> reservationConfirmationUseCase.confirm(
                fixture.user().getUserId(),
                fixture.activeHold().getHoldId().toString(),
                "withdrawal-confirm-" + fixture.user().getUserId(),
                UUID.randomUUID()
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationCancellationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> reservationCancellationUseCase.cancel(
                fixture.user().getUserId(),
                fixture.reservation().getReservationId(),
                UUID.randomUUID()
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationQrIssuanceUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> getMyReservationQrUseCase.get(
                fixture.user().getUserId(),
                fixture.reservation().getReservationId()
            )
        );
    }

    private Fixture createFixture() {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser user = saveUser("visitor-" + suffix + "@example.com");
        AppUser owner = saveUser("owner-" + suffix + "@example.com");
        AppUser reviewer = saveUser("reviewer-" + suffix + "@example.com");
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));

        Content content = contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 행사",
            "행사 설명",
            "김해시",
            "10:00-18:00",
            "01012345678",
            "주의사항",
            "전체 이용가",
            "준비물 없음",
            "취소 정책",
            now
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(86_400),
            now.plusSeconds(90_000),
            now.plusSeconds(85_200),
            now.plusSeconds(89_000),
            10
        );
        session.approve(reviewer, now);
        session = contentSessionRepository.saveAndFlush(session);

        CapacityHold activeHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(3_600),
            null,
            null,
            null,
            now
        ));
        CapacityHold consumedHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(3_600),
            now,
            null,
            null,
            now
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "reservation-" + suffix,
            "qr-" + suffix,
            region,
            consumedHold,
            session,
            user,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        jdbcTemplate.update("UPDATE content_session SET remaining_capacity = 8 WHERE session_id = ?", session.getSessionId());
        return new Fixture(user, session, activeHold, reservation);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "password-hash",
            "홍길동",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private ErrorCode withdraw(Long userId) {
        try {
            withdrawUserUseCase.withdraw(userId);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private void assertUserCommandWaitsForWithdrawalAndIsRejected(
        Long userId,
        Runnable userCommand
    ) throws Exception {
        CountDownLatch withdrawalEnteredRedis = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);
        CountDownLatch commandStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            withdrawalEnteredRedis.countDown();
            await(releaseWithdrawal);
            return null;
        }).when(refreshTokenStore).revokeAllFamilies(userId);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executorService.submit(() -> withdrawUserUseCase.withdraw(userId));
            assertThat(withdrawalEnteredRedis.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> command = executorService.submit(() -> {
                commandStarted.countDown();
                return execute(userCommand);
            });
            assertThat(commandStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(command.isDone()).isFalse();

            releaseWithdrawal.countDown();
            withdrawal.get(3, TimeUnit.SECONDS);
            assertThat(command.get(3, TimeUnit.SECONDS)).isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    private ErrorCode execute(Runnable userCommand) {
        try {
            userCommand.run();
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("withdrawal synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("withdrawal synchronization interrupted", exception);
        }
    }

    private record Fixture(
        AppUser user,
        ContentSession session,
        CapacityHold activeHold,
        Reservation reservation
    ) {
    }

    @TestConfiguration
    static class WithdrawalMySqlTestConfiguration {

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }
}
