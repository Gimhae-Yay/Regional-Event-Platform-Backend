package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
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
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    EndStampbookUseCase.class,
    ContentService.class,
    StampbookService.class,
    StampbookContentService.class,
    StampbookProgressService.class,
    OperatorAuthorizationService.class,
    EndStampbookAuditAtomicityTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class EndStampbookAuditAtomicityTest {

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

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    EndStampbookAuditAtomicityTest(
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
    void end_성공감사기록이실패하면상태와진행전이를롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> endStampbookUseCase.end(
            fixture.operator().getUserId(),
            new EndStampbookCommand(fixture.stampbook().getStampbookId(), "행사 운영을 종료합니다."),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(stampbookRepository.findById(fixture.stampbook().getStampbookId()))
            .hasValueSatisfying(stampbook -> {
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PUBLISHED);
                assertThat(stampbook.getPublishedAt()).isEqualTo(PUBLISHED_AT);
                assertThat(stampbook.getEndedAt()).isNull();
            });
        assertThat(stampbookProgressRepository.findById(fixture.progress().getStampbookProgressId()))
            .hasValueSatisfying(progress -> assertThat(progress.getStatus())
                .isEqualTo(StampbookProgressStatus.IN_PROGRESS));
        assertThat(auditEventRepository.count()).isZero();
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
            Stampbook stampbook = stampbookRepository.save(new Stampbook(region, couponPolicy, "스탬프북 제목"));
            stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));
            AppUser visitor = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "password-hash",
                "방문자",
                "010-0000-0000",
                AppUserStatus.ACTIVE
            ));
            StampbookProgress progress = stampbookProgressRepository.save(new StampbookProgress(
                stampbook,
                visitor
            ));
            return new Fixture(operator, stampbook, progress);
        });
        jdbcTemplate.update(
            "UPDATE stampbook SET status = 'PUBLISHED', published_at = ? WHERE stampbook_id = ?",
            Timestamp.from(PUBLISHED_AT),
            fixture.stampbook().getStampbookId()
        );
        return fixture;
    }

    private record Fixture(
        AppUser operator,
        Stampbook stampbook,
        StampbookProgress progress
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
