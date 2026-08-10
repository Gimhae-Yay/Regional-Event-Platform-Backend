package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class MissionListIndexMigrationMySqlTest {

    private static final int MISSION_COUNT = 10_000;
    private static final int REGION_COUNT = 50;
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-12-31T23:59:59Z");
    private static final String REGION_INDEX_NAME = "idx_mission_region_mission_id";
    private static final String REGION_STATUS_INDEX_NAME = "idx_mission_region_status_mission_id";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    @Test
    void migration_createsIndexesUsedByRegionMissionListQueries() {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        migrate(jdbcTemplate);
        insertMissions(jdbcTemplate);
        jdbcTemplate.execute("ANALYZE TABLE mission");

        Map<String, Object> regionPlan = jdbcTemplate.queryForMap(
            """
            EXPLAIN
            SELECT mission_id, status
            FROM mission
            WHERE region_id = 1
            ORDER BY mission_id DESC
            LIMIT 20
            """
        );
        Map<String, Object> regionStatusPlan = jdbcTemplate.queryForMap(
            """
            EXPLAIN
            SELECT mission_id, status
            FROM mission
            WHERE region_id = 1
              AND status = 'PENDING_REVIEW'
            ORDER BY mission_id DESC
            LIMIT 20
            """
        );

        assertIndexPlan(regionPlan, REGION_INDEX_NAME);
        assertIndexPlan(regionStatusPlan, REGION_STATUS_INDEX_NAME);
    }

    private JdbcTemplate createJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        ));
    }

    private void migrate(JdbcTemplate jdbcTemplate) {
        Flyway.configure()
            .dataSource(jdbcTemplate.getDataSource())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void insertMissions(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO mission (
                    mission_id,
                    region_id,
                    condition_type,
                    required_visit_count,
                    reward_coupon_policy_id,
                    status,
                    ends_at,
                    published_at,
                    ended_at
                ) VALUES (?, ?, 'VISIT_COUNT', 1, 1, ?, ?, NULL, NULL)
                """
            )) {
                for (int missionId = 1; missionId <= MISSION_COUNT; missionId++) {
                    statement.setLong(1, missionId);
                    statement.setLong(2, missionId % REGION_COUNT + 1L);
                    statement.setString(3, missionId / REGION_COUNT % 2 == 0 ? "DRAFT" : "PENDING_REVIEW");
                    statement.setTimestamp(4, Timestamp.from(MISSION_ENDS_AT));
                    statement.addBatch();
                }
                statement.executeBatch();
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void assertIndexPlan(
        Map<String, Object> plan,
        String indexName
    ) {
        assertThat(plan.get("key")).isEqualTo(indexName);
        assertThat(String.valueOf(plan.get("Extra"))).doesNotContain("Using filesort");
    }
}
