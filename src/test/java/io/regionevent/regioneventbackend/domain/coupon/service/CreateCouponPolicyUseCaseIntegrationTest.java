package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
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
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@DataJpaTest
@Import({
    CreateCouponPolicyUseCase.class,
    CouponPolicyService.class,
    ContentService.class,
    AppUserService.class,
    OperatorAuthorizationService.class,
    CreateCouponPolicyUseCaseIntegrationTest.TestConfig.class
})
class CreateCouponPolicyUseCaseIntegrationTest {

    private final CreateCouponPolicyUseCase createCouponPolicyUseCase;
    private final CouponPolicyRepository couponPolicyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;

    @Autowired
    CreateCouponPolicyUseCaseIntegrationTest(
        CreateCouponPolicyUseCase createCouponPolicyUseCase,
        CouponPolicyRepository couponPolicyRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository
    ) {
        this.createCouponPolicyUseCase = createCouponPolicyUseCase;
        this.couponPolicyRepository = couponPolicyRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
    }

    @Test
    void create_본인_지역의_콘텐츠면_DRAFT_정책을_저장한다() {
        Fixture fixture = createFixture();

        CreateCouponPolicyResult result = createCouponPolicyUseCase.create(
            fixture.operator().getUserId(),
            fixture.content().getContentId(),
            request()
        );

        assertThat(couponPolicyRepository.findById(result.couponPolicyId()))
            .hasValueSatisfying(couponPolicy -> {
                assertThat(couponPolicy.getContent().getContentId()).isEqualTo(fixture.content().getContentId());
                assertThat(couponPolicy.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
                assertThat(couponPolicy.getStatus()).isEqualTo(CouponPolicyStatus.DRAFT);
                assertThat(couponPolicy.getIssuanceType()).isEqualTo(CouponIssuanceType.VISIT);
            });
    }

    @Test
    void create_다른_운영자의_콘텐츠면_FORBIDDEN을_반환하고_저장하지_않는다() {
        Fixture fixture = createFixture();
        AppUser anotherOperator = saveOperator(fixture.region(), "another-operator@example.com");

        assertThatThrownBy(() -> createCouponPolicyUseCase.create(
            anotherOperator.getUserId(),
            fixture.content().getContentId(),
            request()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        assertThat(couponPolicyRepository.findAll()).isEmpty();
    }

    private Fixture createFixture() {
        Region region = regionRepository.save(new Region("GIMHAE", "김해시", true));
        AppUser operator = saveOperator(region, "operator@example.com");
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
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        return new Fixture(region, operator, content);
    }

    private AppUser saveOperator(Region region, String loginIdentifier) {
        AppUser operator = appUserRepository.save(new AppUser(
            loginIdentifier,
            "hashed-password",
            "콘텐츠 운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private CreateCouponPolicyRequest request() {
        return new CreateCouponPolicyRequest(
            "1",
            "재방문 할인",
            "방문 혜택",
            "VISIT",
            3_000L,
            10_000L,
            30,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z"),
            1_000L
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), java.time.ZoneOffset.UTC);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }

    private record Fixture(Region region, AppUser operator, Content content) {
    }
}
