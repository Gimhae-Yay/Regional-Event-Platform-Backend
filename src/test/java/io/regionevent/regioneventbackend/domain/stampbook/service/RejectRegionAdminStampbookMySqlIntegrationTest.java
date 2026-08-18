package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookUseCase.RejectRegionAdminStampbookCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
class RejectRegionAdminStampbookMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-08-14T03:00:00Z");
    private static final String REJECTION_REASON = "완료 보상 쿠폰 정책을 공개 상태로 전환한 뒤 다시 요청해 주세요.";

    private final RejectRegionAdminStampbookUseCase rejectRegionAdminStampbookUseCase;
    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final StampbookRepository stampbookRepository;
    private final StampbookContentRepository stampbookContentRepository;
    private final AuditEventRepository auditEventRepository;

    @Autowired
    RejectRegionAdminStampbookMySqlIntegrationTest(
        RejectRegionAdminStampbookUseCase rejectRegionAdminStampbookUseCase,
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        StampbookRepository stampbookRepository,
        StampbookContentRepository stampbookContentRepository,
        AuditEventRepository auditEventRepository
    ) {
        this.rejectRegionAdminStampbookUseCase = rejectRegionAdminStampbookUseCase;
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.stampbookRepository = stampbookRepository;
        this.stampbookContentRepository = stampbookContentRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void reject_다른지역관리자면권한오류와실패감사를기록한다() throws Exception {
        Fixture fixture = createFixture();
        AppUser otherRegionAdmin = createRegionAdmin(
            createRegion("OTHER-" + Long.toUnsignedString(System.nanoTime()))
        );

        mockMvc.perform(post("/api/v1/region-admin/stampbooks/{stampbookId}/reject", fixture.stampbookId())
                .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(otherRegionAdmin.getUserId()))
                .contentType("application/json")
                .content(requestBody(REJECTION_REASON)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.code()));

        assertThat(stampbookRepository.findById(fixture.stampbookId()))
            .hasValueSatisfying(stampbook ->
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PENDING_REVIEW)
            );
        assertThat(findAudits(fixture.stampbookId())).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getPreviousState()).isEqualTo(StampbookStatus.PENDING_REVIEW.name());
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getReasonCode()).isEqualTo(ErrorCode.FORBIDDEN.code());
        });
    }

    @Test
    void reject_반려사유가공백이면입력오류를반환하고감사를기록하지않는다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/region-admin/stampbooks/{stampbookId}/reject", fixture.stampbookId())
                .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.regionAdminUserId()))
                .contentType("application/json")
                .content(requestBody("   ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.code()));

        assertThat(stampbookRepository.findById(fixture.stampbookId()))
            .hasValueSatisfying(stampbook ->
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PENDING_REVIEW)
            );
        assertThat(findAudits(fixture.stampbookId())).isEmpty();
    }

    @Test
    void reject_심사대기상태가아니면상태충돌과실패감사를기록한다() throws Exception {
        Fixture fixture = createFixture();
        Stampbook stampbook = stampbookRepository.findById(fixture.stampbookId()).orElseThrow();
        stampbook.reject();
        stampbookRepository.saveAndFlush(stampbook);

        mockMvc.perform(post("/api/v1/region-admin/stampbooks/{stampbookId}/reject", fixture.stampbookId())
                .header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.regionAdminUserId()))
                .contentType("application/json")
                .content(requestBody(REJECTION_REASON)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.STAMPBOOK_STATE_CONFLICT.code()));

        assertThat(findAudits(fixture.stampbookId())).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getPreviousState()).isEqualTo(StampbookStatus.DRAFT.name());
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getReasonCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT.code());
        });
    }

    @Test
    @Timeout(20)
    void concurrentRejections_하나만성공하고나머지는상태충돌과실패감사를기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<RejectionAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<RejectionAttempt> first = executorService.submit(
                () -> rejectAfterStart(fixture, ready, start)
            );
            Future<RejectionAttempt> second = executorService.submit(
                () -> rejectAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(RejectionAttempt::successful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.successful())
            .extracting(RejectionAttempt::errorCode)
            .containsExactly(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        assertThat(stampbookRepository.findById(fixture.stampbookId()))
            .hasValueSatisfying(stampbook ->
                assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.DRAFT)
            );
        assertThat(findAudits(fixture.stampbookId()))
            .extracting(auditEvent -> auditEvent.getResult())
            .containsExactlyInAnyOrder(AuditEventResult.SUCCESS, AuditEventResult.FAILURE);
    }

    private RejectionAttempt rejectAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            rejectRegionAdminStampbookUseCase.reject(
                fixture.regionAdminUserId(),
                new RejectRegionAdminStampbookCommand(fixture.stampbookId(), REJECTION_REASON),
                UUID.randomUUID()
            );
            return new RejectionAttempt(true, null);
        } catch (BusinessException exception) {
            return new RejectionAttempt(false, exception.getErrorCode());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for latch", exception);
        }
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = createRegion("STB-" + suffix);
        AppUser regionAdmin = createRegionAdmin(region);
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
            BASE_TIME
        ));
        CouponPolicy couponPolicy = couponPolicyRepository.save(new CouponPolicy(
            content,
            region,
            "스탬프북 완료 쿠폰",
            null,
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            1_000,
            1_000,
            7,
            BASE_TIME.minusSeconds(3_600),
            BASE_TIME.plusSeconds(3_600),
            null
        ));
        Stampbook stampbook = stampbookRepository.save(new Stampbook(region, couponPolicy, "스탬프북 제목"));
        stampbookContentRepository.saveAndFlush(new StampbookContent(stampbook, content));
        stampbook.requestPublication();
        stampbookRepository.saveAndFlush(stampbook);
        return new Fixture(regionAdmin.getUserId(), stampbook.getStampbookId());
    }

    private Region createRegion(String regionCode) {
        return regionRepository.save(new Region(regionCode, "김해시", true));
    }

    private AppUser createRegionAdmin(Region region) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser regionAdmin = appUserRepository.save(new AppUser(
            "region-admin-" + suffix + "@example.com",
            "password-hash",
            "지역 관리자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.save(new UserRoleAssignment(
            regionAdmin,
            UserRole.REGION_ADMIN,
            region
        ));
        return regionAdmin;
    }

    private List<AuditEvent> findAudits(Long stampbookId) {
        return auditEventRepository.findAll().stream()
            .filter(auditEvent -> auditEvent.getTargetId().equals(stampbookId))
            .toList();
    }

    private String requestBody(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    private record Fixture(
        Long regionAdminUserId,
        Long stampbookId
    ) {
    }

    private record RejectionAttempt(
        boolean successful,
        ErrorCode errorCode
    ) {
    }
}
