package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyUpdateHistoryRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    UpdateCouponPolicyUseCase.class,
    AppUserService.class,
    OperatorAuthorizationService.class,
    CouponPolicyService.class,
    CouponPolicyUpdateHistoryService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    UpdateCouponPolicyHistoryPersistenceTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class UpdateCouponPolicyHistoryPersistenceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T05:30:00Z");

    private final UpdateCouponPolicyUseCase updateCouponPolicyUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    @Autowired
    UpdateCouponPolicyHistoryPersistenceTest(
        UpdateCouponPolicyUseCase updateCouponPolicyUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.updateCouponPolicyUseCase = updateCouponPolicyUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponPolicyUpdateHistoryRepository = couponPolicyUpdateHistoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void update_수정정책과_감사이벤트와_전후값이력을_함께_저장한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.randomUUID();

        UpdateCouponPolicyResult result = updateCouponPolicyUseCase.update(
            fixture.operator().getUserId(),
            fixture.couponPolicy().getCouponPolicyId(),
            request("수정 쿠폰", "수정 설명", 4_000L, 12_000L, 45, 200L, "할인 금액 조정"),
            requestId
        );

        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(couponPolicyRepository.findById(fixture.couponPolicy().getCouponPolicyId()))
            .hasValueSatisfying(policy -> {
                assertThat(policy.getName()).isEqualTo("수정 쿠폰");
                assertThat(policy.getDescription()).isEqualTo("수정 설명");
                assertThat(policy.getDiscountAmount()).isEqualTo(4_000L);
                assertThat(policy.getMinimumPaymentAmount()).isEqualTo(12_000L);
                assertThat(policy.getValidDays()).isEqualTo(45);
                assertThat(policy.getTotalIssueLimit()).isEqualTo(200L);
                assertThat(policy.getUpdatedAt()).isEqualTo(UPDATED_AT);
            });
        assertThat(couponPolicyUpdateHistoryRepository.findAll()).singleElement()
            .satisfies(history -> assertHistory(history, requestId, fixture.couponPolicy().getCouponPolicyId()));
        assertThat(auditEventRepository.findAll()).singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.COUPON_POLICY);
                assertThat(auditEvent.getTargetId()).isEqualTo(fixture.couponPolicy().getCouponPolicyId());
                assertThat(auditEvent.getPreviousState()).isEqualTo(CouponPolicyStatus.DRAFT.name());
                assertThat(auditEvent.getNextState()).isEqualTo(CouponPolicyStatus.DRAFT.name());
                assertThat(auditEvent.getActorKind()).isEqualTo("USER");
                assertThat(auditEvent.getActorRole()).isEqualTo(UserRole.OPERATOR.name());
                assertThat(auditEvent.getReason()).isEqualTo("할인 금액 조정");
                assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
                assertThat(auditEvent.getOccurredAt()).isEqualTo(UPDATED_AT);
            });
    }

    private void assertHistory(
        CouponPolicyUpdateHistory history,
        UUID requestId,
        Long couponPolicyId
    ) {
        assertThat(history.getCouponPolicy().getCouponPolicyId()).isEqualTo(couponPolicyId);
        assertThat(history.getAuditEvent().getAuditEventId()).isNotNull();
        assertThat(history.getActorKind()).isEqualTo("USER");
        assertThat(history.getActorRole()).isEqualTo(UserRole.OPERATOR.name());
        assertThat(history.getReason()).isEqualTo("할인 금액 조정");
        assertThat(history.getRequestId()).isEqualTo(requestId.toString());
        assertThat(history.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(history.getPreviousName()).isEqualTo("기존 쿠폰");
        assertThat(history.getNextName()).isEqualTo("수정 쿠폰");
        assertThat(history.getPreviousDescription()).isEqualTo("기존 설명");
        assertThat(history.getNextDescription()).isEqualTo("수정 설명");
        assertThat(history.getPreviousDiscountAmount()).isEqualTo(3_000L);
        assertThat(history.getNextDiscountAmount()).isEqualTo(4_000L);
        assertThat(history.getPreviousMinimumPaymentAmount()).isEqualTo(10_000L);
        assertThat(history.getNextMinimumPaymentAmount()).isEqualTo(12_000L);
        assertThat(history.getPreviousValidDays()).isEqualTo(30);
        assertThat(history.getNextValidDays()).isEqualTo(45);
        assertThat(history.getPreviousIssueStartsAt()).isEqualTo(UPDATED_AT.minusSeconds(3_600));
        assertThat(history.getNextIssueStartsAt()).isEqualTo(UPDATED_AT.minusSeconds(1_800));
        assertThat(history.getPreviousIssueEndsAt()).isEqualTo(UPDATED_AT.plusSeconds(3_600));
        assertThat(history.getNextIssueEndsAt()).isEqualTo(UPDATED_AT.plusSeconds(7_200));
        assertThat(history.getPreviousTotalIssueLimit()).isEqualTo(100L);
        assertThat(history.getNextTotalIssueLimit()).isEqualTo(200L);
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("CPU-" + suffix, "김해시", true));
            AppUser operator = appUserRepository.saveAndFlush(new AppUser(
                "operator-" + suffix + "@example.com",
                "password-hash",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region,
                UPDATED_AT.minusSeconds(60)
            ));
            Content content = contentRepository.saveAndFlush(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "쿠폰 콘텐츠",
                "쿠폰 정책을 위한 콘텐츠입니다.",
                "김해시 주소",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안내",
                "전체",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                UPDATED_AT
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content,
                region,
                "기존 쿠폰",
                "기존 설명",
                CouponIssuanceType.VISIT,
                3_000L,
                10_000L,
                30,
                UPDATED_AT.minusSeconds(3_600),
                UPDATED_AT.plusSeconds(3_600),
                100L
            ));
            return new Fixture(operator, couponPolicy);
        });
    }

    private UpdateCouponPolicyRequest request(
        String name,
        String description,
        long discountAmount,
        long minimumPaymentAmount,
        int validDaysAfterIssue,
        long totalIssueLimit,
        String reason
    ) {
        return new UpdateCouponPolicyRequest(
            JsonNodeFactory.instance.stringNode(name),
            JsonNodeFactory.instance.stringNode(description),
            JsonNodeFactory.instance.numberNode(discountAmount),
            JsonNodeFactory.instance.numberNode(minimumPaymentAmount),
            JsonNodeFactory.instance.numberNode(validDaysAfterIssue),
            JsonNodeFactory.instance.stringNode(UPDATED_AT.minusSeconds(1_800).toString()),
            JsonNodeFactory.instance.stringNode(UPDATED_AT.plusSeconds(7_200).toString()),
            JsonNodeFactory.instance.numberNode(totalIssueLimit),
            JsonNodeFactory.instance.stringNode(reason)
        );
    }

    private record Fixture(AppUser operator, CouponPolicy couponPolicy) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(UPDATED_AT, ZoneOffset.UTC);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
