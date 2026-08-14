package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChangeRegionAdminRoleUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String EVIDENCE_REFERENCE = "OPS-2026-0814-781";
    private static final String REVOCATION_REASON_CODE = "REGION_ADMIN_REVOCATION";
    private static final String REASSIGNMENT_REASON_CODE = "REGION_ADMIN_REASSIGNMENT";

    private final ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ChangeRegionAdminRoleUseCaseMySqlTest(
        ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        RegionRepository regionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.changeRegionAdminRoleUseCase = changeRegionAdminRoleUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.regionRepository = regionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 두_지역관리자_회수가_경합하면_한_요청만_성공하고_기존_지역에_한_명이_남는다() throws Exception {
        Fixture fixture = createFixture();

        List<String> outcomes = race(
            () -> revoke(fixture, fixture.firstAdminId()),
            () -> revoke(fixture, fixture.secondAdminId())
        );

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertOnlyOneAdminRemainsWithoutSuccessAudit(fixture);
    }

    @Test
    @Timeout(15)
    void 회수와_다른_지역_재배정이_경합하면_한_요청만_성공하고_기존_지역에_한_명이_남는다() throws Exception {
        Fixture fixture = createFixture();

        List<String> outcomes = race(
            () -> reassign(fixture, fixture.firstAdminId()),
            () -> revoke(fixture, fixture.secondAdminId())
        );

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertOnlyOneAdminRemainsWithoutSuccessAudit(fixture);
    }

    private List<String> race(ConcurrentAction firstAction, ConcurrentAction secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<String> first = executorService.submit(() -> executeAfterStart(firstAction, ready, start));
            Future<String> second = executorService.submit(() -> executeAfterStart(secondAction, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private String executeAfterStart(
        ConcurrentAction action,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            action.execute();
            return "SUCCESS";
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.ROLE_ASSIGNMENT_CONFLICT) {
                return "CONFLICT";
            }
            throw exception;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent role changes did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent role change was interrupted", exception);
        }
    }

    private void revoke(Fixture fixture, Long targetUserId) {
        changeRegionAdminRoleUseCase.change(
            fixture.actorId(),
            targetUserId,
            RegionAdminRoleChange.NONE,
            null,
            REVOCATION_REASON_CODE,
            EVIDENCE_REFERENCE,
            UUID.randomUUID()
        );
    }

    private void reassign(Fixture fixture, Long targetUserId) {
        changeRegionAdminRoleUseCase.change(
            fixture.actorId(),
            targetUserId,
            RegionAdminRoleChange.REGION_ADMIN,
            fixture.requestedRegionId(),
            REASSIGNMENT_REASON_CODE,
            EVIDENCE_REFERENCE,
            UUID.randomUUID()
        );
    }

    private void assertOnlyOneAdminRemainsWithoutSuccessAudit(Fixture fixture) {
        List<UserRoleAssignment> activeAssignments = List.of(
            fixture.firstAdminId(),
            fixture.secondAdminId()
        ).stream()
            .flatMap(userId -> userRoleAssignmentRepository
                .findAllByAppUserUserIdAndStatus(userId, UserRoleAssignmentStatus.ACTIVE)
                .stream()
            )
            .filter(assignment -> assignment.getRole() == UserRole.REGION_ADMIN)
            .filter(assignment -> assignment.getRegion().getRegionId().equals(fixture.previousRegionId()))
            .toList();

        assertThat(activeAssignments).singleElement().satisfies(activeAssignment ->
            assertThat(auditEventRepository.findAll())
                .filteredOn(auditEvent -> isSuccessAuditFor(auditEvent, activeAssignment))
                .isEmpty()
        );
    }

    private boolean isSuccessAuditFor(AuditEvent auditEvent, UserRoleAssignment assignment) {
        return auditEvent.getTargetType() == AuditEventTargetType.USER_ROLE_ASSIGNMENT
            && auditEvent.getTargetId().equals(assignment.getRoleAssignmentId());
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region previousRegion = regionRepository.save(new Region("R" + suffix, "기존 지역", true));
            Region requestedRegion = regionRepository.save(new Region("N" + suffix, "재배정 지역", true));
            AppUser actor = appUserRepository.save(new AppUser(
                "platform-admin-" + suffix + "@example.com",
                "hashed-password",
                "전체관리자",
                "010-1234-5678",
                AppUserAccountKind.PRIVILEGED,
                AppUserStatus.ACTIVE
            ));
            platformAdminAssignmentRepository.save(new PlatformAdminAssignment(
                actor,
                PlatformAdminGrade.PLATFORM_ADMIN
            ));
            AppUser firstAdmin = appUserRepository.save(ordinaryUser("first-admin-" + suffix));
            AppUser secondAdmin = appUserRepository.save(ordinaryUser("second-admin-" + suffix));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                firstAdmin,
                UserRole.REGION_ADMIN,
                previousRegion
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                secondAdmin,
                UserRole.REGION_ADMIN,
                previousRegion
            ));
            contentRepository.save(new Content(
                previousRegion,
                actor,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "동시성 보호 콘텐츠",
                "마지막 지역관리자 보호를 검증하는 콘텐츠입니다.",
                "김해시",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전 수칙을 지켜주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                Instant.parse("2026-08-14T00:00:00Z")
            ));
            userRoleAssignmentRepository.flush();
            contentRepository.flush();
            return new Fixture(
                actor.getUserId(),
                firstAdmin.getUserId(),
                secondAdmin.getUserId(),
                previousRegion.getRegionId(),
                requestedRegion.getRegionId()
            );
        });
    }

    private AppUser ordinaryUser(String emailPrefix) {
        return new AppUser(
            emailPrefix + "@example.com",
            "hashed-password",
            "지역관리자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        );
    }

    @FunctionalInterface
    private interface ConcurrentAction {

        void execute();
    }

    private record Fixture(
        Long actorId,
        Long firstAdminId,
        Long secondAdminId,
        Long previousRegionId,
        Long requestedRegionId
    ) {
    }
}
