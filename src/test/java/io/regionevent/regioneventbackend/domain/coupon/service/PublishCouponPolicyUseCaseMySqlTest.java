package io.regionevent.regioneventbackend.domain.coupon.service;

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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PublishCouponPolicyUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final PublishCouponPolicyUseCase publishCouponPolicyUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PublishCouponPolicyUseCaseMySqlTest(
        PublishCouponPolicyUseCase publishCouponPolicyUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.publishCouponPolicyUseCase = publishCouponPolicyUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void publish_최초_공개면_정책과_감사_이력을_같이_저장한다() {
        Fixture fixture = createFixture();

        PublishCouponPolicyResult result = publish(fixture);

        assertThat(result.status()).isEqualTo(CouponPolicyStatus.PUBLISHED);
        assertThat(couponPolicyRepository.findById(fixture.couponPolicyId()))
            .hasValueSatisfying(policy -> {
                assertThat(policy.getStatus()).isEqualTo(CouponPolicyStatus.PUBLISHED);
                assertThat(policy.getPublishedAt()).isEqualTo(result.publishedAt());
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.COUPON_POLICY)
            .singleElement()
            .satisfies(event -> assertAuditEvent(event, fixture, result));
    }

    @Test
    @Timeout(15)
    void publish_동시_요청이면_공개와_감사_이력을_한번만_기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<PublishCouponPolicyResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<PublishCouponPolicyResult> first = executorService.submit(
                () -> publishAfterStart(fixture, ready, start)
            );
            Future<PublishCouponPolicyResult> second = executorService.submit(
                () -> publishAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(PublishCouponPolicyResult::status)
            .containsOnly(CouponPolicyStatus.PUBLISHED);
        assertThat(couponPolicyRepository.findById(fixture.couponPolicyId()))
            .hasValueSatisfying(policy -> assertThat(policy.getStatus()).isEqualTo(CouponPolicyStatus.PUBLISHED));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.COUPON_POLICY)
            .singleElement();
        assertThat(auditEventActorLinkRepository.findAll()).singleElement()
            .satisfies(link -> assertThat(link.getActor().getUserId()).isEqualTo(fixture.operatorId()));
    }

    @Test
    void publish_종료된_정책이면_실패_감사_이력을_기록하고_상태충돌을_반환한다() {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> {
            CouponPolicy couponPolicy = couponPolicyRepository.findById(fixture.couponPolicyId()).orElseThrow();
            Instant now = Instant.now();
            couponPolicy.publish(now);
            couponPolicy.end(now.plusSeconds(1));
        });
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> publishCouponPolicyUseCase.publish(
            fixture.operatorId(),
            fixture.couponPolicyId(),
            "종료된 정책 공개 시도",
            requestId
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT)
        );

        assertThat(couponPolicyRepository.findById(fixture.couponPolicyId()))
            .hasValueSatisfying(policy -> assertThat(policy.getStatus()).isEqualTo(CouponPolicyStatus.ENDED));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.COUPON_POLICY)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.couponPolicyId());
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getPreviousState()).isEqualTo(CouponPolicyStatus.ENDED.name());
                assertThat(event.getNextState()).isNull();
                assertThat(event.getReasonCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT.code());
                assertThat(event.getRequestId()).isEqualTo(requestId);
                assertThat(auditEventActorLinkRepository.findById(event.getAuditEventId()))
                    .hasValueSatisfying(link ->
                        assertThat(link.getActor().getUserId()).isEqualTo(fixture.operatorId())
                    );
            });
    }

    private PublishCouponPolicyResult publishAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return publish(fixture);
    }

    private PublishCouponPolicyResult publish(Fixture fixture) {
        return publishCouponPolicyUseCase.publish(
            fixture.operatorId(),
            fixture.couponPolicyId(),
            "검토 완료 후 공개",
            UUID.randomUUID()
        );
    }

    private void assertAuditEvent(
        AuditEvent event,
        Fixture fixture,
        PublishCouponPolicyResult result
    ) {
        assertThat(event.getTargetId()).isEqualTo(fixture.couponPolicyId());
        assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(event.getPreviousState()).isEqualTo(CouponPolicyStatus.DRAFT.name());
        assertThat(event.getNextState()).isEqualTo(CouponPolicyStatus.PUBLISHED.name());
        assertThat(event.getReason()).isEqualTo("검토 완료 후 공개");
        assertThat(event.getOccurredAt()).isEqualTo(result.publishedAt());
        assertThat(auditEventActorLinkRepository.findById(event.getAuditEventId()))
            .hasValueSatisfying(link ->
                assertThat(link.getActor().getUserId()).isEqualTo(fixture.operatorId())
            );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("COUPON-" + suffix, "김해시", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "coupon-operator-" + suffix + "@example.com",
                "hashed-password",
                "쿠폰 운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "김해 문화 체험",
                "김해 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                now
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content,
                region,
                "재방문 할인",
                "방문 혜택",
                CouponIssuanceType.VISIT,
                3_000L,
                10_000L,
                30,
                now.minusSeconds(3_600),
                now.plusSeconds(3_600),
                1_000L
            ));
            return new Fixture(operator.getUserId(), couponPolicy.getCouponPolicyId());
        });
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

    private record Fixture(
        Long operatorId,
        Long couponPolicyId
    ) {
    }
}
