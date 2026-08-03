package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.service.SuspendContentResult;
import io.regionevent.regioneventbackend.domain.content.service.SuspendContentUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.ExpireOrInvalidateCapacityHoldsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.HoldTerminationResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(SuspendContentControllerMySqlIntegrationTest.HoldTerminationContentSuspensionConcurrencyConfig.class)
class SuspendContentControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String SUSPEND_PATH = "/api/v1/region-admin/contents/{contentId}/suspend";
    private static final int SESSION_CAPACITY = 10;

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final SuspendContentUseCase suspendContentUseCase;
    private final ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase;
    private final BlockingContentSessionService blockingContentSessionService;

    @Autowired
    SuspendContentControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        SuspendContentUseCase suspendContentUseCase,
        ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase,
        BlockingContentSessionService blockingContentSessionService
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.suspendContentUseCase = suspendContentUseCase;
        this.expireOrInvalidateCapacityHoldsUseCase = expireOrInvalidateCapacityHoldsUseCase;
        this.blockingContentSessionService = blockingContentSessionService;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void suspendContent_withActiveHoldAndConfirmedReservation_suspendsAndRestoresOnlyActiveHoldCapacity()
        throws Exception {
        Fixture fixture = createPublishedFixture(true, true);

        performSuspend(fixture.admin(), fixture.content().getContentId().toString(), "  기상 악화  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 운영 중단에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.suspendedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.suspensionReason").value("기상 악화"));

        assertThat(contentRepository.findById(fixture.content().getContentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.SUSPENDED));
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        );
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.SUSPENDED);
        ContentLog suspendedLog = logs.getLast();
        assertThat(suspendedLog.getReason()).isEqualTo("기상 악화");
        assertThat(suspendedLog.getActor().getUserId()).isEqualTo(fixture.admin().getUserId());

        assertThat(capacityHoldRepository.findById(fixture.activeHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getInvalidationReason()).isEqualTo("CONTENT_SUSPENDED");
                assertThat(hold.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(capacityHoldRepository.findById(fixture.consumedHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(9));
        assertThat(reservationRepository.findById(fixture.reservationId()))
            .hasValueSatisfying(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED)
            );

        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PUBLISHED");
            assertThat(auditEvent.getNextState()).isEqualTo("SUSPENDED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getOccurredAt()).isEqualTo(suspendedLog.getDate());
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    @Test
    void suspendContent_withInvalidInput_returnsContractErrorsWithoutChanges() throws Exception {
        Fixture fixture = createPublishedFixture(false, false);
        String contentId = fixture.content().getContentId().toString();

        performSuspend(fixture.admin(), contentId, "   ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post(SUSPEND_PATH, contentId)
                .header("Authorization", bearerToken(fixture.admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        performSuspend(fixture.admin(), "01", "운영 중단")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performSuspend(fixture.admin(), "not-a-number", "운영 중단")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertContentState(fixture.content().getContentId(), ContentStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void suspendContent_withoutAuthentication_returnsUnauthenticatedWithoutChanges() throws Exception {
        Fixture fixture = createPublishedFixture(false, false);

        mockMvc.perform(post(SUSPEND_PATH, fixture.content().getContentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 중단\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertContentState(fixture.content().getContentId(), ContentStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void suspendContent_byVisitorOrOtherRegionAdmin_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createPublishedFixture(false, false);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);
        Region otherRegion = saveRegion("OTHER");
        AppUser otherRegionAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherRegionAdmin, otherRegion);

        performSuspend(visitor, fixture.content().getContentId().toString(), "운영 중단")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performSuspend(otherRegionAdmin, fixture.content().getContentId().toString(), "운영 중단")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertContentState(fixture.content().getContentId(), ContentStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void suspendContent_whenTargetIsMissing_returnsNotFoundWithoutChanges() throws Exception {
        Fixture fixture = createPublishedFixture(false, false);

        performSuspend(fixture.admin(), "999999999", "운영 중단")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertContentState(fixture.content().getContentId(), ContentStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void suspendContent_whenContentIsNotPublished_returnsConflictAndRecordsNonPersonalFailureAudit()
        throws Exception {
        Fixture fixture = createFixture(ContentStatus.APPROVED, false, false);

        performSuspend(fixture.admin(), fixture.content().getContentId().toString(), "운영 중단")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_SUSPEND_CONFLICT"));

        assertContentState(fixture.content().getContentId(), ContentStatus.APPROVED);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).extracting(ContentLog::getStatus).containsExactly(ContentLogStatus.APPROVED);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("APPROVED");
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("CONTENT_SUSPEND_CONFLICT");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
        });
    }

    @Test
    @Timeout(10)
    void 중단과_홀드만료가_경합해도_교착없이_홀드를_한번만_종결한다() throws Exception {
        Fixture fixture = createPublishedFixture(true, false);
        jdbcTemplate.update(
            "UPDATE capacity_hold SET expires_at = CURRENT_TIMESTAMP - INTERVAL 1 SECOND WHERE hold_id = ?",
            fixture.activeHoldId()
        );
        blockingContentSessionService.prepareSuspensionBlock();
        blockingContentSessionService.prepareTerminationSessionLockWait(fixture.sessionId());

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SuspendContentResult> suspension = executorService.submit(() -> suspendContentUseCase.suspend(
                fixture.admin().getUserId(),
                fixture.content().getContentId(),
                "기상 악화",
                UUID.randomUUID()
            ));
            assertThat(blockingContentSessionService.awaitSuspensionSessionLock()).isTrue();

            Future<HoldTerminationResult> termination = executorService.submit(
                expireOrInvalidateCapacityHoldsUseCase::execute
            );
            assertThat(blockingContentSessionService.awaitTerminationSessionLockAttempt()).isTrue();
            assertThat(blockingContentSessionService.awaitTerminationSessionLockWait()).isTrue();
            blockingContentSessionService.releaseSuspension();

            assertThat(suspension.get(5, TimeUnit.SECONDS).status()).isEqualTo(ContentStatus.SUSPENDED);
            assertThat(termination.get(5, TimeUnit.SECONDS).failedHoldCount()).isZero();
        } finally {
            blockingContentSessionService.releaseSuspension();
            blockingContentSessionService.stopTerminationSessionLockTracking();
        }

        assertThat(capacityHoldRepository.findById(fixture.activeHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus())
                .isIn(CapacityHoldStatus.EXPIRED, CapacityHoldStatus.INVALIDATED));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertContentState(fixture.content().getContentId(), ContentStatus.SUSPENDED);
    }

    private ResultActions performSuspend(
        AppUser user,
        String contentId,
        String reason
    ) throws Exception {
        return mockMvc.perform(post(SUSPEND_PATH, contentId)
            .header("Authorization", bearerToken(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + reason + "\"}"));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private Fixture createPublishedFixture(boolean createActiveHold, boolean createConfirmedReservation) {
        return createFixture(ContentStatus.PUBLISHED, createActiveHold, createConfirmedReservation);
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        boolean createActiveHold,
        boolean createConfirmedReservation
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRegionAdmin(admin, region);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        AppUser visitor = saveUser("visitor-" + suffix, AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(86_400)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.valueOf(contentStatus.name()),
            null,
            now.minusSeconds(86_400)
        ));
        if (contentStatus != ContentStatus.PUBLISHED) {
            return new Fixture(region, admin, content, null, null, null, null);
        }

        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(3_600),
            now.plusSeconds(14_400),
            now.plusSeconds(1_800),
            now.plusSeconds(12_600),
            SESSION_CAPACITY
        );
        session.approve(admin, now);
        session = contentSessionRepository.saveAndFlush(session);

        Long activeHoldId = null;
        if (createActiveHold) {
            activeHoldId = saveHold(region, session, visitor, 2, CapacityHoldStatus.ACTIVE, now)
                .getHoldId();
        }

        Long consumedHoldId = null;
        Long reservationId = null;
        if (createConfirmedReservation) {
            CapacityHold consumedHold = saveHold(
                region,
                session,
                visitor,
                1,
                CapacityHoldStatus.CONSUMED,
                now
            );
            Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                "reservation-" + suffix,
                "qr-" + suffix,
                region,
                consumedHold,
                session,
                visitor,
                ReservationStatus.CONFIRMED,
                now,
                null,
                null,
                null,
                null
            ));
            consumedHoldId = consumedHold.getHoldId();
            reservationId = reservation.getReservationId();
        }
        return new Fixture(
            region,
            admin,
            content,
            session.getSessionId(),
            activeHoldId,
            consumedHoldId,
            reservationId
        );
    }

    private CapacityHold saveHold(
        Region region,
        ContentSession session,
        AppUser visitor,
        int quantity,
        CapacityHoldStatus status,
        Instant now
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.getSessionId()
        );
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            quantity,
            status,
            now.plusSeconds(600),
            status == CapacityHoldStatus.CONSUMED ? now : null,
            null,
            null,
            now
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + " 지역", true));
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assignRegionAdmin(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.REGION_ADMIN, region));
    }

    private void assertContentState(Long contentId, ContentStatus expectedStatus) {
        assertThat(contentRepository.findById(contentId))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(expectedStatus));
    }

    private record Fixture(
        Region region,
        AppUser admin,
        Content content,
        Long sessionId,
        Long activeHoldId,
        Long consumedHoldId,
        Long reservationId
    ) {
    }

    @TestConfiguration
    static class HoldTerminationContentSuspensionConcurrencyConfig {

        @Bean
        @Primary
        BlockingContentSessionService blockingContentSessionService(
            ContentSessionRepository contentSessionRepository,
            JdbcTemplate jdbcTemplate
        ) {
            return new BlockingContentSessionService(contentSessionRepository, jdbcTemplate);
        }
    }

    static class BlockingContentSessionService extends ContentSessionService {

        private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
        private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

        private final JdbcTemplate jdbcTemplate;
        private volatile boolean blockSuspension;
        private volatile CountDownLatch suspensionSessionLocked = new CountDownLatch(1);
        private volatile CountDownLatch continueSuspension = new CountDownLatch(1);
        private volatile Long suspensionConnectionId;
        private volatile Long terminationTargetSessionId;
        private volatile Long terminationConnectionId;
        private volatile CountDownLatch terminationSessionLockAttempted = new CountDownLatch(1);

        BlockingContentSessionService(
            ContentSessionRepository contentSessionRepository,
            JdbcTemplate jdbcTemplate
        ) {
            super(contentSessionRepository);
            this.jdbcTemplate = jdbcTemplate;
        }

        void prepareSuspensionBlock() {
            blockSuspension = true;
            suspensionSessionLocked = new CountDownLatch(1);
            continueSuspension = new CountDownLatch(1);
            suspensionConnectionId = null;
        }

        void prepareTerminationSessionLockWait(Long sessionId) {
            terminationTargetSessionId = sessionId;
            terminationConnectionId = null;
            terminationSessionLockAttempted = new CountDownLatch(1);
        }

        boolean awaitSuspensionSessionLock() throws InterruptedException {
            return suspensionSessionLocked.await(3, TimeUnit.SECONDS);
        }

        void releaseSuspension() {
            blockSuspension = false;
            continueSuspension.countDown();
        }

        void stopTerminationSessionLockTracking() {
            terminationTargetSessionId = null;
        }

        boolean awaitTerminationSessionLockAttempt() throws InterruptedException {
            return terminationSessionLockAttempted.await(3, TimeUnit.SECONDS);
        }

        boolean awaitTerminationSessionLockWait() {
            for (int attempt = 0; attempt < LOCK_WAIT_CONFIRMATION_ATTEMPTS; attempt++) {
                if (isTerminationSessionLockWaitingForSuspension()) {
                    return true;
                }
                awaitLockWaitConfirmationInterval();
            }
            return false;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void lockSuspendTargetsForUpdate(Long contentId) {
            super.lockSuspendTargetsForUpdate(contentId);
            if (!blockSuspension) {
                return;
            }
            suspensionConnectionId = findCurrentConnectionId();
            suspensionSessionLocked.countDown();
            await(continueSuspension);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void lockForUpdate(Long sessionId) {
            if (sessionId.equals(terminationTargetSessionId)) {
                terminationConnectionId = findCurrentConnectionId();
                terminationSessionLockAttempted.countDown();
            }
            super.lockForUpdate(sessionId);
        }

        private boolean isTerminationSessionLockWaitingForSuspension() {
            Long currentTerminationConnectionId = terminationConnectionId;
            Long currentSuspensionConnectionId = suspensionConnectionId;
            if (currentTerminationConnectionId == null || currentSuspensionConnectionId == null) {
                return false;
            }
            Integer waitingLockCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits AS lock_wait
                    JOIN performance_schema.threads AS requesting_thread
                        ON requesting_thread.thread_id = lock_wait.requesting_thread_id
                    JOIN performance_schema.threads AS blocking_thread
                        ON blocking_thread.thread_id = lock_wait.blocking_thread_id
                    JOIN performance_schema.data_locks AS requested_lock
                        ON requested_lock.engine = lock_wait.engine
                        AND requested_lock.engine_lock_id = lock_wait.requesting_engine_lock_id
                    WHERE requesting_thread.processlist_id = ?
                        AND blocking_thread.processlist_id = ?
                        AND requested_lock.object_schema = DATABASE()
                        AND requested_lock.object_name = 'content_session'
                        AND requested_lock.index_name = 'PRIMARY'
                        AND requested_lock.lock_type = 'RECORD'
                        AND requested_lock.lock_status = 'WAITING'
                    """,
                Integer.class,
                currentTerminationConnectionId,
                currentSuspensionConnectionId
            );
            return waitingLockCount != null && waitingLockCount > 0;
        }

        private Long findCurrentConnectionId() {
            Long connectionId = jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
            if (connectionId == null) {
                throw new IllegalStateException("MySQL connection id does not exist");
            }
            return connectionId;
        }

        private void awaitLockWaitConfirmationInterval() {
            try {
                TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("lock wait confirmation was interrupted", exception);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test synchronization interrupted", exception);
        }
    }
}
