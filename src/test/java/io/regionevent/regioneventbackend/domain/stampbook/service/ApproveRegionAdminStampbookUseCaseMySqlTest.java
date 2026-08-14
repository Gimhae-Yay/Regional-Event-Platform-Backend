package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
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

@SpringBootTest
class ApproveRegionAdminStampbookUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-08-14T03:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2037-12-31T00:00:00Z");

    private final ApproveRegionAdminStampbookUseCase approveRegionAdminStampbookUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    ApproveRegionAdminStampbookUseCaseMySqlTest(
        ApproveRegionAdminStampbookUseCase approveRegionAdminStampbookUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager
    ) {
        this.approveRegionAdminStampbookUseCase = approveRegionAdminStampbookUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void approve_다른지역관리자면권한오류를반환하고상태를유지한다() {
        Fixture fixture = createFixture(false);
        Long otherRegionAdminUserId = createRegionAdminInNewRegion("OTHER-ADMIN", new Region(
            "OTHER-" + System.nanoTime(),
            "다른 지역",
            true
        ));

        assertThatThrownBy(() -> approve(otherRegionAdminUserId, fixture.stampbookId()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        assertPendingReview(fixture.stampbookId());
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE));
    }

    @Test
    void approve_심사대기상태가아니면상태충돌을반환한다() {
        Fixture fixture = createFixture(false);
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
            UPDATE stampbook
            SET status = 'DRAFT'
            WHERE stampbook_id = :stampbookId
            """)
            .setParameter("stampbookId", fixture.stampbookId())
            .executeUpdate());

        assertThatThrownBy(() -> approve(fixture.regionAdminUserId(), fixture.stampbookId()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT)
            );

        assertThat(stampbookRepository.findById(fixture.stampbookId()))
            .hasValueSatisfying(stampbook -> assertThat(stampbook.getStatus())
                .isEqualTo(StampbookStatus.DRAFT));
    }

    @Test
    void approve_대상콘텐츠현재운영자의지역이달라지면재검증에실패한다() {
        Fixture fixture = createFixture(true);

        assertThatThrownBy(() -> approve(fixture.regionAdminUserId(), fixture.stampbookId()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT)
            );

        assertPendingReview(fixture.stampbookId());
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getReasonCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT.code());
            });
    }

    @Test
    @Timeout(20)
    void approve_서로다른지역관리자의동시심사요청은한번만공개하고나머지는상태충돌로수렴한다() throws Exception {
        Fixture fixture = createFixture(false);
        Long anotherRegionAdminUserId = createRegionAdmin(
            "another-region-admin",
            fixture.regionId()
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<ApprovalAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ApprovalAttempt> first = executor.submit(() -> approveAfterStart(
                fixture.regionAdminUserId(),
                fixture.stampbookId(),
                ready,
                start
            ));
            Future<ApprovalAttempt> second = executor.submit(() -> approveAfterStart(
                anotherRegionAdminUserId,
                fixture.stampbookId(),
                ready,
                start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(ApprovalAttempt::successful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.successful())
            .extracting(ApprovalAttempt::errorCode)
            .containsExactly(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        assertThat(stampbookRepository.findById(fixture.stampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PUBLISHED);
                assertThat(stampbook.getPublishedAt()).isNotNull();
            });
        assertThat(auditEventRepository.findAll())
            .extracting(event -> event.getResult())
            .containsExactlyInAnyOrder(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
    }

    private ApprovalAttempt approveAfterStart(
        Long regionAdminUserId,
        Long stampbookId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            approve(regionAdminUserId, stampbookId);
            return new ApprovalAttempt(true, null);
        } catch (BusinessException exception) {
            return new ApprovalAttempt(false, exception.getErrorCode());
        }
    }

    private void approve(Long regionAdminUserId, Long stampbookId) {
        approveRegionAdminStampbookUseCase.approve(
            regionAdminUserId,
            new ApproveRegionAdminStampbookUseCase.ApproveRegionAdminStampbookCommand(
                stampbookId,
                "대상 콘텐츠와 완료 보상 정책을 확인했습니다."
            ),
            UUID.randomUUID()
        );
    }

    private void assertPendingReview(Long stampbookId) {
        assertThat(stampbookRepository.findById(stampbookId))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PENDING_REVIEW);
                assertThat(stampbook.getPublishedAt()).isNull();
            });
    }

    private Fixture createFixture(boolean targetOperatorBelongsToOtherRegion) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("STB-" + suffix, "김해", true));
            AppUser regionAdmin = appUserRepository.save(newUser("region-admin-" + suffix));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                regionAdmin,
                UserRole.REGION_ADMIN,
                region
            ));
            Region targetOperatorRegion = targetOperatorBelongsToOtherRegion
                ? regionRepository.save(new Region("OTHER-" + suffix, "다른 지역", true))
                : region;
            AppUser targetOperator = appUserRepository.save(newUser("operator-" + suffix));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                targetOperator,
                UserRole.OPERATOR,
                targetOperatorRegion
            ));
            Content targetContent = contentRepository.save(newContent(region, targetOperator));
            CouponPolicy rewardCouponPolicy = new CouponPolicy(
                targetContent,
                region,
                "스탬프북 완료 보상",
                null,
                CouponIssuanceType.STAMPBOOK_COMPLETION,
                1_000,
                1_000,
                7,
                BASE_TIME.minusSeconds(3_600),
                ISSUE_ENDS_AT,
                null
            );
            rewardCouponPolicy.publish(BASE_TIME);
            rewardCouponPolicy = couponPolicyRepository.saveAndFlush(rewardCouponPolicy);
            Stampbook stampbook = new Stampbook(region, rewardCouponPolicy);
            stampbook.requestPublication();
            stampbook = stampbookRepository.saveAndFlush(stampbook);
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, targetContent));
            entityManager.clear();
            return new Fixture(
                regionAdmin.getUserId(),
                stampbook.getStampbookId(),
                region.getRegionId()
            );
        });
    }

    private Long createRegionAdmin(
        String loginPrefix,
        Long regionId
    ) {
        return transactionTemplate.execute(status -> {
            Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new IllegalStateException("region must exist"));
            AppUser user = appUserRepository.save(newUser(loginPrefix + "-" + System.nanoTime()));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                user,
                UserRole.REGION_ADMIN,
                region
            ));
            return user.getUserId();
        });
    }

    private Long createRegionAdminInNewRegion(
        String loginPrefix,
        Region region
    ) {
        return transactionTemplate.execute(status -> {
            Region savedRegion = regionRepository.save(region);
            AppUser user = appUserRepository.save(newUser(loginPrefix + "-" + System.nanoTime()));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                user,
                UserRole.REGION_ADMIN,
                savedRegion
            ));
            return user.getUserId();
        });
    }

    private AppUser newUser(String loginPrefix) {
        return new AppUser(
            loginPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }

    private Content newContent(Region region, AppUser operator) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "스탬프 대상 콘텐츠",
            "스탬프 적립 대상 콘텐츠입니다.",
            "김해시",
            "10:00-18:00",
            "055-1234-5678",
            "안내 사항",
            "전체 이용 가능",
            "없음",
            "취소 정책",
            BASE_TIME
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for latch", exception);
        }
    }

    private record Fixture(
        Long regionAdminUserId,
        Long stampbookId,
        Long regionId
    ) {
    }

    private record ApprovalAttempt(
        boolean successful,
        ErrorCode errorCode
    ) {
    }
}
