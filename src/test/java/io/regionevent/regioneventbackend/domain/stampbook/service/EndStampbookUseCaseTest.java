package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookProgressRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.EndStampbookUseCase.EndStampbookCommand;
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
    EndStampbookUseCase.class,
    ContentService.class,
    StampbookService.class,
    StampbookContentService.class,
    StampbookProgressService.class,
    OperatorAuthorizationService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    EndStampbookUseCaseTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class EndStampbookUseCaseTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-09T05:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-09T06:00:00Z");

    private final EndStampbookUseCase endStampbookUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final StampbookProgressRepository stampbookProgressRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EndStampbookUseCaseTest(
        EndStampbookUseCase endStampbookUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        StampbookProgressRepository stampbookProgressRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.endStampbookUseCase = endStampbookUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void end_승인된소유운영자가종료하면미완료진행과감사를함께기록한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000550");

        EndStampbookResult result = endStampbookUseCase.end(
            fixture.operator().getUserId(),
            new EndStampbookCommand(fixture.stampbook().getStampbookId(), "  행사 운영을 종료합니다.  "),
            requestId
        );

        assertThat(result).satisfies(ended -> {
            assertThat(ended.stampbookId()).isEqualTo(fixture.stampbook().getStampbookId());
            assertThat(ended.status()).isEqualTo(StampbookStatus.ENDED);
            assertThat(ended.endedAt()).isEqualTo(ENDED_AT);
        });
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.ENDED);
                assertThat(stampbook.getPublishedAt()).isEqualTo(PUBLISHED_AT);
                assertThat(stampbook.getEndedAt()).isEqualTo(ENDED_AT);
            });
        assertThat(stampbookProgressRepository.findById(fixture.inProgress().getStampbookProgressId()))
            .hasValueSatisfying(progress -> {
                assertThat(progress.getStatus()).isEqualTo(StampbookProgressStatus.ENDED_INCOMPLETE);
                assertThat(progress.getCompletedAt()).isNull();
            });
        assertThat(stampbookProgressRepository.findById(fixture.completed().getStampbookProgressId()))
            .hasValueSatisfying(progress -> {
                assertThat(progress.getStatus()).isEqualTo(StampbookProgressStatus.COMPLETED);
                assertThat(progress.getCompletedAt()).isEqualTo(PUBLISHED_AT);
            });
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.stampbook().getStampbookId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isEqualTo(StampbookStatus.PUBLISHED.name());
            assertThat(auditEvent.getNextState()).isEqualTo(StampbookStatus.ENDED.name());
            assertThat(auditEvent.getReason()).isEqualTo("행사 운영을 종료합니다.");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(ENDED_AT);
        });
    }

    @Test
    void end_대상콘텐츠소유자가아니면권한오류를반환하고상태와진행을유지한다() {
        Fixture fixture = createFixture();
        AppUser otherOperator = createOperator(fixture.region(), "other-operator");

        assertThatThrownBy(() -> endStampbookUseCase.end(
            otherOperator.getUserId(),
            new EndStampbookCommand(fixture.stampbook().getStampbookId(), "행사 운영을 종료합니다."),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertPublishedStateAndProgresses(fixture);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_PUBLISHED가아닌스탬프북이면상태충돌을반환하고진행을유지한다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'DRAFT', published_at = NULL WHERE stampbook_id = ?",
            fixture.stampbook().getStampbookId()
        );

        assertThatThrownBy(() -> endStampbookUseCase.end(
            fixture.operator().getUserId(),
            new EndStampbookCommand(fixture.stampbook().getStampbookId(), "행사 운영을 종료합니다."),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT);

        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.DRAFT);
                assertThat(stampbook.getPublishedAt()).isNull();
                assertThat(stampbook.getEndedAt()).isNull();
            });
        assertThat(stampbookProgressRepository.findById(fixture.inProgress().getStampbookProgressId()))
            .hasValueSatisfying(progress -> assertThat(progress.getStatus())
                .isEqualTo(StampbookProgressStatus.IN_PROGRESS));
        assertThat(auditEventRepository.count()).isZero();
    }

    private void assertPublishedStateAndProgresses(Fixture fixture) {
        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PUBLISHED);
                assertThat(stampbook.getPublishedAt()).isEqualTo(PUBLISHED_AT);
                assertThat(stampbook.getEndedAt()).isNull();
            });
        assertThat(stampbookProgressRepository.findById(fixture.inProgress().getStampbookProgressId()))
            .hasValueSatisfying(progress -> assertThat(progress.getStatus())
                .isEqualTo(StampbookProgressStatus.IN_PROGRESS));
        assertThat(stampbookProgressRepository.findById(fixture.completed().getStampbookProgressId()))
            .hasValueSatisfying(progress -> assertThat(progress.getStatus())
                .isEqualTo(StampbookProgressStatus.COMPLETED));
    }

    private AppUser createOperator(
        Region region,
        String emailPrefix
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            AppUser operator = appUserRepository.save(new AppUser(
                emailPrefix + "-" + suffix + "@example.com",
                "password-hash",
                "다른 운영자",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
            return operator;
        });
    }

    private Fixture createFixture() {
        Fixture fixture = transactionTemplate.execute(status -> {
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
                PUBLISHED_AT
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
                PUBLISHED_AT.minusSeconds(3_600),
                PUBLISHED_AT.plusSeconds(3_600),
                null
            ));
            Stampbook stampbook = stampbookRepository.save(new Stampbook(region, couponPolicy));
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));

            AppUser visitor = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "password-hash",
                "방문자",
                "010-0000-0000",
                AppUserStatus.ACTIVE
            ));
            StampbookProgress inProgress = stampbookProgressRepository.save(new StampbookProgress(
                stampbook,
                visitor
            ));
            AppUser completedVisitor = appUserRepository.save(new AppUser(
                "completed-visitor-" + suffix + "@example.com",
                "password-hash",
                "완료 방문자",
                "010-1111-1111",
                AppUserStatus.ACTIVE
            ));
            StampbookProgress completed = new StampbookProgress(stampbook, completedVisitor);
            completed.complete(PUBLISHED_AT);
            completed = stampbookProgressRepository.save(completed);
            return new Fixture(region, operator, stampbook, inProgress, completed);
        });
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'PUBLISHED', published_at = ? WHERE stampbook_id = ?",
            Timestamp.from(PUBLISHED_AT),
            fixture.stampbook().getStampbookId()
        );
        return fixture;
    }

    private record Fixture(
        Region region,
        AppUser operator,
        Stampbook stampbook,
        StampbookProgress inProgress,
        StampbookProgress completed
    ) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(ENDED_AT, ZoneOffset.UTC);
        }
    }
}
