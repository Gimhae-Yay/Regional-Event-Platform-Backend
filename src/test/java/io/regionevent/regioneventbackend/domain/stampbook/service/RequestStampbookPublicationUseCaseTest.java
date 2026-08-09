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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationUseCase.RequestStampbookPublicationCommand;
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
    RequestStampbookPublicationUseCase.class,
    ContentService.class,
    CouponPolicyService.class,
    StampbookService.class,
    StampbookContentService.class,
    OperatorAuthorizationService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    RequestStampbookPublicationUseCaseTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class RequestStampbookPublicationUseCaseTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-09T06:00:00Z");

    private final RequestStampbookPublicationUseCase requestStampbookPublicationUseCase;
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
    RequestStampbookPublicationUseCaseTest(
        RequestStampbookPublicationUseCase requestStampbookPublicationUseCase,
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
        this.requestStampbookPublicationUseCase = requestStampbookPublicationUseCase;
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
    void request_승인된운영자가심사를요청하면상태전이와감사를기록한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000556");

        RequestStampbookPublicationResult result = requestStampbookPublicationUseCase.request(
            fixture.operator().getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "  지역 관리자 공개 심사를 요청합니다.  "
            ),
            requestId
        );

        assertThat(result).satisfies(requested -> {
            assertThat(requested.stampbookId()).isEqualTo(fixture.stampbook().getStampbookId());
            assertThat(requested.status()).isEqualTo(StampbookStatus.PENDING_REVIEW);
            assertThat(requested.requestedAt()).isEqualTo(REQUESTED_AT);
        });
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PENDING_REVIEW);
                assertThat(stampbook.getPublishedAt()).isNull();
                assertThat(stampbook.getEndedAt()).isNull();
            });
        assertThat(couponPolicyRepository.findById(fixture.couponPolicy().getCouponPolicyId()))
            .hasValueSatisfying(couponPolicy -> assertThat(couponPolicy.getStatus())
                .isEqualTo(CouponPolicyStatus.DRAFT));
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.stampbook().getStampbookId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isEqualTo(StampbookStatus.DRAFT.name());
            assertThat(auditEvent.getNextState()).isEqualTo(StampbookStatus.PENDING_REVIEW.name());
            assertThat(auditEvent.getReason()).isEqualTo("지역 관리자 공개 심사를 요청합니다.");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(REQUESTED_AT);
        });
    }

    @Test
    void request_사유가비어있으면_입력오류를반환하고상태와감사를유지한다() {
        Fixture fixture = createFixture();

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            fixture.operator().getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "   "
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        assertStampbookRemainsDraft(fixture.stampbook());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void request_승인된운영자가아니면_권한오류를반환한다() {
        Fixture fixture = createFixture();
        AppUser visitor = transactionTemplate.execute(status -> appUserRepository.save(new AppUser(
            "visitor-" + System.nanoTime() + "@example.com",
            "password-hash",
            "방문자",
            "010-0000-0000",
            AppUserStatus.ACTIVE
        )));

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            visitor.getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "공개 심사를 요청합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertStampbookRemainsDraft(fixture.stampbook());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void request_대상콘텐츠소유자가아니면_권한오류를반환한다() {
        Fixture fixture = createFixture();
        AppUser otherOperator = transactionTemplate.execute(status -> {
            AppUser user = appUserRepository.save(new AppUser(
                "other-operator-" + System.nanoTime() + "@example.com",
                "password-hash",
                "다른 운영자",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                user,
                UserRole.OPERATOR,
                fixture.region()
            ));
            return user;
        });

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            otherOperator.getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "공개 심사를 요청합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertStampbookRemainsDraft(fixture.stampbook());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void request_완료보상정책발급경로가아니면_상태와감사를유지한다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE coupon_policy SET issuance_type = 'VISIT' WHERE coupon_policy_id = ?",
            fixture.couponPolicy().getCouponPolicyId()
        );

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            fixture.operator().getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "공개 심사를 요청합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.NOT_FOUND);

        assertStampbookRemainsDraft(fixture.stampbook());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void request_대상콘텐츠가없으면_입력오류를반환하고상태와감사를유지한다() {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> stampbookContentRepository.deleteByStampbookId(
            fixture.stampbook().getStampbookId()
        ));

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            fixture.operator().getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "공개 심사를 요청합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        assertStampbookRemainsDraft(fixture.stampbook());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void request_DRAFT가아닌스탬프북이면_상태충돌을반환한다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'PENDING_REVIEW' WHERE stampbook_id = ?",
            fixture.stampbook().getStampbookId()
        );

        assertThatThrownBy(() -> requestStampbookPublicationUseCase.request(
            fixture.operator().getUserId(),
            new RequestStampbookPublicationCommand(
                fixture.stampbook().getStampbookId(),
                "공개 심사를 요청합니다."
            ),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT);

        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> assertThat(stampbook.getStatus())
                .isEqualTo(StampbookStatus.PENDING_REVIEW));
        assertThat(auditEventRepository.count()).isZero();
    }

    private void assertStampbookRemainsDraft(Stampbook stampbook) {
        assertThat(stampbookRepository.findById(stampbook.getStampbookId()))
            .hasValueSatisfying(foundStampbook -> {
                assertThat(foundStampbook.getStatus()).isEqualTo(StampbookStatus.DRAFT);
                assertThat(foundStampbook.getPublishedAt()).isNull();
            });
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
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "김해 문화 체험",
                "김해 문화를 체험하는 콘텐츠입니다.",
                "김해시 문화재단",
                "매일 10:00~18:00",
                "055-1234-5678",
                "안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                REQUESTED_AT
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.save(new CouponPolicy(
                content,
                region,
                "스탬프북 완료 쿠폰",
                null,
                CouponIssuanceType.STAMPBOOK_COMPLETION,
                1000,
                1000,
                7,
                REQUESTED_AT.minusSeconds(3_600),
                REQUESTED_AT.plusSeconds(3_600),
                null
            ));
            Stampbook stampbook = stampbookRepository.save(new Stampbook(region, couponPolicy));
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));
            return new Fixture(region, operator, stampbook, couponPolicy);
        });
    }

    private record Fixture(
        Region region,
        AppUser operator,
        Stampbook stampbook,
        CouponPolicy couponPolicy
    ) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(REQUESTED_AT, ZoneOffset.UTC);
        }
    }
}
