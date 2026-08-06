package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateSessionRevisionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CreateSessionRevisionUseCase createSessionRevisionUseCase;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final SessionRevisionRepository sessionRevisionRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CreateSessionRevisionUseCaseMySqlTest(
        CreateSessionRevisionUseCase createSessionRevisionUseCase,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        SessionRevisionRepository sessionRevisionRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.createSessionRevisionUseCase = createSessionRevisionUseCase;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.sessionRevisionRepository = sessionRevisionRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(10)
    void 같은_회차_수정_요청이_동시에_들어오면_하나만_저장하고_나머지는_충돌로_거절한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(() -> createAfterStart(fixture, start));
            Future<Attempt> second = executorService.submit(() -> createAfterStart(fixture, start));
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(
                    new Attempt(null),
                    new Attempt(ErrorCode.SESSION_STATE_CONFLICT)
                );
        }

        assertMySqlUniqueConstraint(fixture);
    }

    private Attempt createAfterStart(Fixture fixture, CountDownLatch start) {
        await(start);
        try {
            createSessionRevisionUseCase.create(
                fixture.operatorId(),
                fixture.sessionId(),
                request(),
                UUID.randomUUID()
            );
            return new Attempt(null);
        } catch (BusinessException exception) {
            return new Attempt(exception.getErrorCode());
        }
    }

    private void assertMySqlUniqueConstraint(Fixture fixture) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            ContentSession targetSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
            AppUser operator = appUserRepository.findById(fixture.operatorId()).orElseThrow();
            sessionRevisionRepository.saveAndFlush(new SessionRevision(
                targetSession.getContent(),
                targetSession.getRegion(),
                targetSession,
                targetSession.getVersionNo(),
                request().startsAt().toInstant(),
                request().endsAt().toInstant(),
                request().checkinOpenAt().toInstant(),
                request().checkinCloseAt().toInstant(),
                request().capacity(),
                SessionRevisionStatus.PENDING,
                operator,
                Instant.now(),
                null,
                null,
                null
            ));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                now.minusSeconds(86_400)
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                now.plusSeconds(604_800),
                now.plusSeconds(612_000),
                now.plusSeconds(602_200),
                now.plusSeconds(610_200),
                30
            );
            session.approve(admin, now);
            session = contentSessionRepository.save(session);
            return new Fixture(operator.getUserId(), session.getSessionId());
        });
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private CreateContentSessionRequest request() {
        Instant startsAt = Instant.now().plusSeconds(1_209_600);
        return new CreateContentSessionRequest(
            OffsetDateTime.ofInstant(startsAt, ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.plusSeconds(7_200), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.minusSeconds(1_800), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.plusSeconds(5_400), ZoneOffset.ofHours(9)),
            30
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent session revision request did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent session revision request was interrupted", exception);
        }
    }

    private record Fixture(Long operatorId, Long sessionId) {
    }

    private record Attempt(ErrorCode errorCode) {
    }
}
