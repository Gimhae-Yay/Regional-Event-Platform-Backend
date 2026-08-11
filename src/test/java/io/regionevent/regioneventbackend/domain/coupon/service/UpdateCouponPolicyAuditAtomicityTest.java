package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
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
    UpdateCouponPolicyAuditAtomicityTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class UpdateCouponPolicyAuditAtomicityTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T05:30:00Z");

    private final UpdateCouponPolicyUseCase updateCouponPolicyUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoBean
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    @Autowired
    UpdateCouponPolicyAuditAtomicityTest(
        UpdateCouponPolicyUseCase updateCouponPolicyUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.updateCouponPolicyUseCase = updateCouponPolicyUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponPolicyUpdateHistoryRepository = couponPolicyUpdateHistoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void update_성공_감사_기록에_실패하면_정책_수정과_이력을_모두_롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> updateCouponPolicyUseCase.update(
            fixture.operator().getUserId(),
            fixture.couponPolicy().getCouponPolicyId(),
            request("수정 쿠폰", "할인 금액 조정"),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit storage failure");

        assertThat(couponPolicyRepository.findById(fixture.couponPolicy().getCouponPolicyId()))
            .hasValueSatisfying(policy -> {
                assertThat(policy.getName()).isEqualTo("기존 쿠폰");
                assertThat(policy.getUpdatedAt()).isNotEqualTo(UPDATED_AT);
            });
        assertThat(couponPolicyUpdateHistoryRepository.count()).isZero();
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
        String reason
    ) {
        return new UpdateCouponPolicyRequest(
            JsonNodeFactory.instance.stringNode(name),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
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
