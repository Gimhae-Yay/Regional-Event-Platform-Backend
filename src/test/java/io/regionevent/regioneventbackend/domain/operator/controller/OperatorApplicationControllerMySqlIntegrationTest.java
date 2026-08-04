package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OperatorApplicationControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String OPERATOR_REQUEST_PATH = "/api/v1/operator/operator-requests";

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final RegionRepository regionRepository;
    private final JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    OperatorApplicationControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        RegionRepository regionRepository,
        JwtAccessTokenService jwtAccessTokenService
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.regionRepository = regionRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
    }

    @Test
    @Timeout(10)
    void reapplyConcurrently_createsOnePendingApplicationAndReturnsPendingConflict() throws Exception {
        Region region = saveRegion();
        AppUser applicant = saveUser();
        createRejectedApplication(applicant, region);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> firstRequest = submitReapplication(executorService, ready, start, applicant, region);
            Future<MvcResult> secondRequest = submitReapplication(executorService, ready, start, applicant, region);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = List.of(
                firstRequest.get(5, TimeUnit.SECONDS),
                secondRequest.get(5, TimeUnit.SECONDS)
            );

            assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(201, 409);
            assertThat(results)
                .filteredOn(result -> result.getResponse().getStatus() == 409)
                .singleElement()
                .satisfies(result -> assertThat(result.getResponse().getContentAsString())
                    .contains("OPERATOR_APPLICATION_PENDING"));
            assertThat(operatorApplicationRepository.findAll())
                .filteredOn(application -> application.getApplicant().getUserId().equals(applicant.getUserId()))
                .filteredOn(application -> application.getStatus() == OperatorApplicationStatus.PENDING)
                .singleElement();
        } finally {
            start.countDown();
            operatorApplicationRepository.deleteAllInBatch();
            appUserRepository.deleteAllInBatch();
            regionRepository.deleteAllInBatch();
        }
    }

    private Future<MvcResult> submitReapplication(
        ExecutorService executorService,
        CountDownLatch ready,
        CountDownLatch start,
        AppUser applicant,
        Region region
    ) {
        return executorService.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post(OPERATOR_REQUEST_PATH)
                    .header("Authorization", bearerToken(applicant))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "requestedRegionId": %d,
                          "businessInformation": "Concurrent business information"
                        }
                        """.formatted(region.getRegionId())))
                .andReturn();
        });
    }

    private Region saveRegion() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region("R" + suffix, "Test region", true));
    }

    private AppUser saveUser() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "Operator Applicant",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void createRejectedApplication(AppUser applicant, Region region) {
        AppUser inspector = saveUser();
        operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "Previous business information",
            OperatorApplicationStatus.REJECTED,
            inspector,
            "Rejected"
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }
}
