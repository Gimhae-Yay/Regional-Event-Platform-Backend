package io.regionevent.regioneventbackend.global.performance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
@ConditionalOnProperty(prefix = "performance.fixture", name = "enabled", havingValue = "true")
public class PerformanceFixtureResetService {

    private static final String FIXTURE_VERSION = "k6-response-time-v1";
    private static final String RESET_LOCK_NAME = "regional_event_performance_fixture_reset";
    private static final int RESET_LOCK_TIMEOUT_SECONDS = 30;
    private static final String BASE_FIXTURE_RESOURCE = "performance-fixtures/seed/k6-local.seed.sql";
    private static final String BOOTSTRAP_FIXTURE_RESOURCE = "performance-fixtures/fixtures/api-success-coverage-bootstrap.sql";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final byte[] resetToken;

    public PerformanceFixtureResetService(
        DataSource dataSource,
        Clock clock,
        PerformanceFixtureProperties properties
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.clock = clock;
        this.resetToken = requireResetToken(properties.resetToken());
    }

    @Transactional
    public PerformanceFixtureResetResult reset(String providedToken) {
        validateResetToken(providedToken);
        return jdbcTemplate.execute((ConnectionCallback<PerformanceFixtureResetResult>) connection -> {
            acquireResetLock(connection);
            boolean foreignKeyChecksDisabled = false;
            try {
                setForeignKeyChecks(connection, false);
                foreignKeyChecksDisabled = true;
                executeFixtureSql(connection, BASE_FIXTURE_RESOURCE);
                executeFixtureSql(connection, BOOTSTRAP_FIXTURE_RESOURCE);
                return new PerformanceFixtureResetResult(FIXTURE_VERSION, clock.instant());
            } finally {
                try {
                    if (foreignKeyChecksDisabled) {
                        setForeignKeyChecks(connection, true);
                    }
                } finally {
                    releaseResetLock(connection);
                }
            }
        });
    }

    private static byte[] requireResetToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("performance.fixture.reset-token must not be blank when fixture reset is enabled");
        }
        return token.getBytes(StandardCharsets.UTF_8);
    }

    private void validateResetToken(String providedToken) {
        byte[] providedBytes = providedToken == null
            ? new byte[0]
            : providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(resetToken, providedBytes)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void acquireResetLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, RESET_LOCK_NAME);
            statement.setInt(2, RESET_LOCK_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException("performance fixture reset lock was not acquired");
                }
            }
        }
    }

    private void releaseResetLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, RESET_LOCK_NAME);
            statement.executeQuery();
        }
    }

    private void setForeignKeyChecks(Connection connection, boolean enabled) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = " + (enabled ? 1 : 0));
        }
    }

    private void executeFixtureSql(Connection connection, String resourcePath) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(resourcePath));
        populator.populate(connection);
    }
}
