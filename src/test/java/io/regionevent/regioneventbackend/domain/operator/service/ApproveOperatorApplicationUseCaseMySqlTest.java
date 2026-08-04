package io.regionevent.regioneventbackend.domain.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.operator.dto.ApproveOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApproveOperatorApplicationUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ApproveOperatorApplicationUseCase approveOperatorApplicationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ApproveOperatorApplicationUseCaseMySqlTest(
        ApproveOperatorApplicationUseCase approveOperatorApplicationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.approveOperatorApplicationUseCase = approveOperatorApplicationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @Timeout(10)
    void 같은_신청을_동시에_승인해도_역할과_감사를_한번만_기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<ApproveOperatorApplicationResponse> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApproveOperatorApplicationResponse> first = executorService.submit(
                () -> approveAfterStart(fixture, ready, start)
            );
            Future<ApproveOperatorApplicationResponse> second = executorService.submit(
                () -> approveAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(
                first.get(5, TimeUnit.SECONDS),
                second.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(results).extracting(ApproveOperatorApplicationResponse::status).containsOnly("APPROVED");
        assertThat(results).extracting(ApproveOperatorApplicationResponse::processedAt)
            .containsOnly(results.getFirst().processedAt());
        assertThat(operatorApplicationRepository.findById(fixture.applicationId()))
            .hasValueSatisfying(application ->
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.APPROVED)
            );
        assertThat(userRoleAssignmentRepository.findByIdUserIdAndIdRoleAndAppUserStatus(
            fixture.applicantId(), UserRole.OPERATOR, AppUserStatus.ACTIVE
        )).isPresent();
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> fixture.applicationId().equals(event.getTargetId()))
            .hasSize(1);
    }

    private ApproveOperatorApplicationResponse approveAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return approveOperatorApplicationUseCase.approve(
            fixture.adminId(),
            fixture.applicationId(),
            UUID.randomUUID()
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "테스트 지역", true));
            AppUser admin = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "관리자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser applicant = appUserRepository.save(new AppUser(
                "applicant-" + suffix + "@example.com",
                "hashed-password",
                "신청자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            OperatorApplication application = operatorApplicationRepository.save(new OperatorApplication(
                applicant,
                region,
                "사업자 정보",
                OperatorApplicationStatus.PENDING,
                null,
                null
            ));
            return new Fixture(admin.getUserId(), applicant.getUserId(), application.getOperatorApplicationId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent approval did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent approval was interrupted", exception);
        }
    }

    private record Fixture(
        Long adminId,
        Long applicantId,
        Long applicationId
    ) {
    }
}
