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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    UpdateStampbookUseCase.class,
    ContentService.class,
    CouponPolicyService.class,
    StampbookService.class,
    StampbookContentService.class,
    OperatorAuthorizationService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    UpdateStampbookUseCaseTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class UpdateStampbookUseCaseTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T05:30:00Z");

    private final UpdateStampbookUseCase updateStampbookUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    UpdateStampbookUseCaseTest(
        UpdateStampbookUseCase updateStampbookUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.updateStampbookUseCase = updateStampbookUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void update_승인된운영자가대상과보상정책을교체하면수정감사를기록한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000555");

        UpdateStampbookResult result = updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                "  수정 제목  ",
                List.of(fixture.replacementContent().getContentId()),
                fixture.replacementCouponPolicy().getCouponPolicyId(),
                "  대상 콘텐츠와 보상 정책을 수정합니다.  "
            ),
            requestId
        );

        assertThat(result).satisfies(updated -> {
            assertThat(updated.status()).isEqualTo(StampbookStatus.DRAFT);
            assertThat(updated.targetCount()).isOne();
            assertThat(updated.updatedAt()).isEqualTo(UPDATED_AT);
        });
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getTitle()).isEqualTo("수정 제목");
                assertThat(stampbook.getRewardCouponPolicy().getCouponPolicyId())
                    .isEqualTo(fixture.replacementCouponPolicy().getCouponPolicyId());
            });
        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.replacementContent().getContentId());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.stampbook().getStampbookId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isEqualTo(StampbookStatus.DRAFT.name());
            assertThat(auditEvent.getNextState()).isEqualTo(StampbookStatus.DRAFT.name());
            assertThat(auditEvent.getReason()).isEqualTo("대상 콘텐츠와 보상 정책을 수정합니다.");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(UPDATED_AT);
        });
    }

    @Test
    void update_수정필드가없으면_입력오류를반환하고기존스탬프북을유지한다() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                null,
                null,
                null,
                "스탬프북을 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.originalContent().getContentId());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void update_초안스탬프북의제목만수정하면_공백제거와감사를함께기록한다() {
        Fixture fixture = createFixture();

        UpdateStampbookResult result = updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                "  김해 가야 문화 완주 코스  ",
                null,
                null,
                "제목을 수정합니다."
            ),
            UUID.randomUUID()
        );

        assertThat(result.targetCount()).isOne();
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> assertThat(stampbook.getTitle())
                .isEqualTo("김해 가야 문화 완주 코스"));
        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.originalContent().getContentId());
        assertThat(auditEventRepository.count()).isOne();
    }

    @Test
    void update_공백이거나101자제목이면_입력오류를반환한다() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                "   ",
                null,
                null,
                "제목을 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                "가".repeat(101),
                null,
                null,
                "제목을 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> assertThat(stampbook.getTitle()).isEqualTo("기존 제목"));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void update_승인된운영자가아니면_권한오류를반환한다() {
        Fixture fixture = createFixture();
        AppUser visitor = transactionTemplate.execute(status -> appUserRepository.save(new AppUser(
            "visitor-" + System.nanoTime() + "@example.com",
            "password-hash",
            "방문자",
            "010-0000-0000",
            AppUserStatus.ACTIVE
        )));

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            visitor.getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                null,
                List.of(fixture.replacementContent().getContentId()),
                null,
                "대상 콘텐츠를 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.originalContent().getContentId());
    }

    @Test
    void update_DRAFT가아닌스탬프북이면_상태충돌을반환한다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'PENDING_REVIEW' WHERE stampbook_id = ?",
            fixture.stampbook().getStampbookId()
        );

        assertThatThrownBy(() -> updateStampbookUseCase.update(
            fixture.operator().getUserId(),
            new UpdateStampbookCommand(
                fixture.stampbook().getStampbookId(),
                "수정 제목",
                null,
                null,
                "대상 콘텐츠를 수정합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT);

        assertThat(stampbookContentRepository.findContentIdsByStampbookId(fixture.stampbook().getStampbookId()))
            .containsExactly(fixture.originalContent().getContentId());
        assertThat(auditEventRepository.count()).isZero();
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
            Stampbook stampbook = stampbookRepository.save(new Stampbook(
                region,
                originalCouponPolicy,
                "기존 제목"
            ));
            stampbookContentRepository.saveAllAndFlush(List.of(new StampbookContent(
                stampbook,
                originalContent
            )));
            return new Fixture(
                operator,
                stampbook,
                originalContent,
                replacementContent,
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
