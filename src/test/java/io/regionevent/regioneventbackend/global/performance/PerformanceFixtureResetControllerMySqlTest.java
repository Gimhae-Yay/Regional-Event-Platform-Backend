package io.regionevent.regioneventbackend.global.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "performance.fixture.enabled=true",
    "performance.fixture.reset-token=test-fixture-token",
    "portone.fake.enabled=true",
    "storage.fake.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerformanceFixtureResetControllerMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String RESET_URL = "/internal/performance/fixtures/reset";
    private static final String FIXTURE_TOKEN_HEADER = "X-Performance-Fixture-Token";
    private static final String FIXTURE_TOKEN = "test-fixture-token";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PerformanceFixtureResetControllerMySqlTest(
        MockMvc mockMvc,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void reset_withValidToken_recreatesTheFixedFixtureAndReturnsItsVersion() throws Exception {
        performReset()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.fixtureVersion").value("k6-response-time-v1"))
            .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        assertThat(fixtureUserCount()).isEqualTo(15);

        performReset().andExpect(status().isOk());

        assertThat(fixtureUserCount()).isEqualTo(15);
    }

    @Test
    void reset_withoutToken_returnsForbiddenBeforeMutatingTheDatabase() throws Exception {
        int fixtureUserCountBeforeReset = fixtureUserCount();

        mockMvc.perform(post(RESET_URL))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(fixtureUserCount()).isEqualTo(fixtureUserCountBeforeReset);
    }

    private org.springframework.test.web.servlet.ResultActions performReset() throws Exception {
        return mockMvc.perform(post(RESET_URL).header(FIXTURE_TOKEN_HEADER, FIXTURE_TOKEN));
    }

    private int fixtureUserCount() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE user_id BETWEEN 900001 AND 900015",
            Integer.class
        );
        return count == null ? 0 : count;
    }
}
