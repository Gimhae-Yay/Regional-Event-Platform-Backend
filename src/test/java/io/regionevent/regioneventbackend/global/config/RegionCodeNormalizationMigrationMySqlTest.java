package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("deprecation")
class RegionCodeNormalizationMigrationMySqlTest {

    private static final int CHECK_CONSTRAINT_VIOLATION_ERROR_CODE = 3_819;
    private static final String NORMALIZED_REGION_CODE_CONSTRAINT_NAME = "ck_region_region_code_normalized";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0.42")
    );

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void 기존_소문자_지역_코드를_대문자로_이관하고_정규형_CHECK_제약을_강제한다() {
        migrateTo("29");
        insertRegion("gyeonggi-do");

        migrateToLatest();

        assertThat(findRegionCode()).isEqualTo("GYEONGGI-DO");
        assertThatThrownBy(() -> insertRegion("busan"))
            .isInstanceOfSatisfying(UncategorizedSQLException.class, exception -> {
                assertThat(exception.getSQLException().getErrorCode())
                    .isEqualTo(CHECK_CONSTRAINT_VIOLATION_ERROR_CODE);
                assertThat(exception.getSQLException().getMessage())
                    .contains(NORMALIZED_REGION_CODE_CONSTRAINT_NAME);
            });
    }

    @Test
    void 정규화_결과가_충돌하는_기존_데이터가_있으면_이관을_중단한다() {
        migrateTo("29");
        jdbcTemplate.execute(
            "ALTER TABLE region MODIFY region_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL"
        );
        insertRegion("seoul");
        insertRegion("SEOUL");

        assertThatThrownBy(this::migrateToLatest)
            .hasMessageContaining("ck_region_code_normalization_validation");
        assertThat(jdbcTemplate.queryForList(
            "SELECT region_code FROM region ORDER BY region_id",
            String.class
        )).containsExactly("seoul", "SEOUL");
    }

    @Test
    void 정규화할수없는_기존_지역_코드가_있으면_데이터_변경_전에_이관을_중단한다() {
        migrateTo("29");
        insertRegion("seoul_city");

        assertThatThrownBy(this::migrateToLatest)
            .hasMessageContaining("ck_region_code_normalization_validation");
        assertThat(findRegionCode()).isEqualTo("seoul_city");
    }

    private void cleanDatabase() {
        Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()
            .clean();
    }

    private void migrateTo(String targetVersion) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(targetVersion))
            .load()
            .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private void insertRegion(String regionCode) {
        jdbcTemplate.update(
            """
            INSERT INTO region (region_code, name, is_public, created_at, updated_at)
            VALUES (?, '테스트 지역', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """,
            regionCode
        );
    }

    private String findRegionCode() {
        return jdbcTemplate.queryForObject(
            "SELECT region_code FROM region",
            String.class
        );
    }
}
