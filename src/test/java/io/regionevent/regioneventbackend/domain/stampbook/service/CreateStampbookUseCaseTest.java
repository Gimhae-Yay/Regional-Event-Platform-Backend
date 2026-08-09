package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
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
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookUseCase.CreateStampbookCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    CreateStampbookUseCase.class,
    ContentService.class,
    CouponPolicyService.class,
    StampbookService.class,
    StampbookContentService.class,
    OperatorAuthorizationService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    CreateStampbookUseCaseTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class CreateStampbookUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-09T05:30:00Z");

    private final CreateStampbookUseCase createStampbookUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CreateStampbookUseCaseTest(
        CreateStampbookUseCase createStampbookUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.createStampbookUseCase = createStampbookUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void create_승인된운영자의동일지역콘텐츠와보상정책으로_스탬프북과감사를생성한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000541");

        CreateStampbookResult result = createStampbookUseCase.create(
            fixture.operator().getUserId(),
            command(fixture.region().getRegionId(), fixture.contentIds(), fixture.couponPolicy().getCouponPolicyId()),
            requestId
        );

        assertThat(result).satisfies(created -> {
            assertThat(created.status()).isEqualTo(StampbookStatus.DRAFT);
            assertThat(created.targetCount()).isEqualTo(2);
            assertThat(created.createdAt()).isEqualTo(CREATED_AT);
        });
        assertThat(stampbookRepository.findById(result.stampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
                assertThat(stampbook.getRewardCouponPolicy().getCouponPolicyId())
                    .isEqualTo(fixture.couponPolicy().getCouponPolicyId());
            });
        assertThat(stampbookContentRepository.findAll())
            .extracting(stampbookContent -> stampbookContent.getContent().getContentId())
            .containsExactlyInAnyOrderElementsOf(fixture.contentIds());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
            assertThat(auditEvent.getTargetId()).isEqualTo(result.stampbookId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isNull();
            assertThat(auditEvent.getNextState()).isEqualTo(StampbookStatus.DRAFT.name());
            assertThat(auditEvent.getReason()).isEqualTo("스탬프북을 생성합니다.");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(CREATED_AT);
        });
        AuditEvent auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
            .hasValueSatisfying(actorLink -> assertThat(actorLink.getActor().getUserId())
                .isEqualTo(fixture.operator().getUserId()));
    }

    @Test
    void create_승인된운영자가아니면_권한오류를반환하고생성하지않는다() {
        Fixture fixture = createFixture();
        AppUser visitor = transactionTemplate.execute(status -> appUserRepository.save(new AppUser(
            "visitor-" + System.nanoTime() + "@example.com",
            "password-hash",
            "방문자",
            "010-0000-0000",
            AppUserStatus.ACTIVE
        )));

        assertThatThrownBy(() -> createStampbookUseCase.create(
            visitor.getUserId(),
            command(fixture.region().getRegionId(), fixture.contentIds(), fixture.couponPolicy().getCouponPolicyId()),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(stampbookRepository.count()).isZero();
    }

    @Test
    void create_요청지역이운영자담당지역과다르면_권한오류를반환한다() {
        Fixture fixture = createFixture();
        Region anotherRegion = transactionTemplate.execute(status -> regionRepository.save(new Region(
            "OTHER-" + System.nanoTime(),
            "다른 지역",
            true
        )));

        assertThatThrownBy(() -> createStampbookUseCase.create(
            fixture.operator().getUserId(),
            command(anotherRegion.getRegionId(), fixture.contentIds(), fixture.couponPolicy().getCouponPolicyId()),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(stampbookRepository.count()).isZero();
    }

    @Test
    void create_보상정책이없으면_대상없음오류를반환한다() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> createStampbookUseCase.create(
            fixture.operator().getUserId(),
            command(fixture.region().getRegionId(), fixture.contentIds(), 9_999_999L),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.NOT_FOUND);

        assertThat(stampbookRepository.count()).isZero();
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
            Content firstContent = contentRepository.save(content(region, operator, "첫 번째 콘텐츠"));
            Content secondContent = contentRepository.save(content(region, operator, "두 번째 콘텐츠"));
            CouponPolicy couponPolicy = couponPolicyRepository.save(new CouponPolicy(
                firstContent,
                region,
                "스탬프북 완료 쿠폰",
                null,
                CouponIssuanceType.STAMPBOOK_COMPLETION,
                1000,
                1000,
                7,
                CREATED_AT.minusSeconds(3_600),
                CREATED_AT.plusSeconds(3_600),
                null
            ));
            return new Fixture(region, operator, List.of(firstContent.getContentId(), secondContent.getContentId()), couponPolicy);
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
            CREATED_AT
        );
    }

    private CreateStampbookCommand command(
        Long regionId,
        List<Long> contentIds,
        Long rewardCouponPolicyId
    ) {
        return new CreateStampbookCommand(
            regionId,
            contentIds,
            rewardCouponPolicyId,
            "  스탬프북을 생성합니다.  "
        );
    }

    private record Fixture(
        Region region,
        AppUser operator,
        List<Long> contentIds,
        CouponPolicy couponPolicy
    ) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        }
    }
}
