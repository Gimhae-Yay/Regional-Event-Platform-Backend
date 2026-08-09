package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookUseCase.UpdateStampbookCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    UpdateStampbookUseCase.class,
    ContentService.class,
    CouponPolicyService.class,
    StampbookService.class,
    StampbookContentService.class,
    OperatorAuthorizationService.class,
    UpdateStampbookAuditAtomicityTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class UpdateStampbookAuditAtomicityTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T05:30:00Z");

    private final UpdateStampbookUseCase updateStampbookUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    UpdateStampbookAuditAtomicityTest(
        UpdateStampbookUseCase updateStampbookUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.updateStampbookUseCase = updateStampbookUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void update_성공감사기록이실패하면대상연결과보상정책변경을모두롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                List.of(fixture.replacementContent().getContentId()),
                fixture.replacementCouponPolicy().getCouponPolicyId(),
                "대상 콘텐츠와 보상 정책을 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.originalContent().getContentId());
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> assertThat(stampbook.getRewardCouponPolicy().getCouponPolicyId())
                .isEqualTo(fixture.originalCouponPolicy().getCouponPolicyId()));
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("STB-" + suffix, "김해시", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "password-hash",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content originalContent = contentRepository.save(content(region, operator, "기존 콘텐츠"));
            Content replacementContent = contentRepository.save(content(region, operator, "교체 콘텐츠"));
            CouponPolicy originalCouponPolicy = couponPolicyRepository.save(couponPolicy(
                originalContent,
                region,
                "기존 완료 보상"
            ));
            CouponPolicy replacementCouponPolicy = couponPolicyRepository.save(couponPolicy(
                replacementContent,
                region,
                "교체 완료 보상"
            ));
            Stampbook stampbook = stampbookRepository.save(new Stampbook(region, originalCouponPolicy));
            stampbookContentRepository.save(new StampbookContent(
                stampbook,
                originalContent
            ));
            return new Fixture(
                operator,
                stampbook,
                originalContent,
                replacementContent,
                originalCouponPolicy,
                replacementCouponPolicy
            );
        });
    }

    private Content content(
        Region region,
        AppUser operator,
        String title
    ) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            title,
            "김해 문화를 체험하는 콘텐츠입니다.",
            "김해시 문화재단",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            UPDATED_AT
        );
    }

    private CouponPolicy couponPolicy(
        Content content,
        Region region,
        String name
    ) {
        return new CouponPolicy(
            content,
            region,
            name,
            null,
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            1000,
            1000,
            7,
            UPDATED_AT.minusSeconds(3_600),
            UPDATED_AT.plusSeconds(3_600),
            null
        );
    }

    private record Fixture(
        AppUser operator,
        Stampbook stampbook,
        Content originalContent,
        Content replacementContent,
        CouponPolicy originalCouponPolicy,
        CouponPolicy replacementCouponPolicy
    ) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(UPDATED_AT, ZoneOffset.UTC);
        }
    }
}
