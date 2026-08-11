package io.regionevent.regioneventbackend.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlDatabaseCleanerIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String CHILD_TABLE = "test_cleanup_child";
    private static final String PARENT_TABLE = "test_cleanup_parent";

    private final JdbcTemplate jdbcTemplate;
    private final MySqlDatabaseCleaner databaseCleaner;

    @Autowired
    MySqlDatabaseCleanerIntegrationTest(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        databaseCleaner = new MySqlDatabaseCleaner(dataSource);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @BeforeAll
    static void grantLockMonitoringPrivileges() {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
    }

    @Test
    @Timeout(10)
    void 비트랜잭션_정리는_FK를_복원하고_Flyway_이력을_보존한다() {
        int migrationCount = countRows("flyway_schema_history");
        createFixtureTables();
        try {
            jdbcTemplate.update("INSERT INTO " + PARENT_TABLE + " (id) VALUES (1)");
            jdbcTemplate.update("INSERT INTO " + CHILD_TABLE + " (id, parent_id) VALUES (1, 1)");

            databaseCleaner.clean();

            assertThat(countRows(PARENT_TABLE)).isZero();
            assertThat(countRows(CHILD_TABLE)).isZero();
            assertThat(countRows("flyway_schema_history")).isEqualTo(migrationCount);
            assertThat(findForeignKeyChecks()).isOne();
        } finally {
            dropFixtureTables();
        }
    }

    @Test
    @Timeout(15)
    void 다른_MySQL_연결이_정리를_막으면_제한시간과_진단을_남긴다() throws Exception {
        createFixtureTables();
        jdbcTemplate.update("INSERT INTO " + PARENT_TABLE + " (id) VALUES (1)");

        try (Connection blockingConnection = openBlockingConnection()) {
            blockParentTableCleanup(blockingConnection);

            assertThatThrownBy(databaseCleaner::clean)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("table=" + PARENT_TABLE)
                .hasMessageMatching("(?s).*connectionId=\\d+.*")
                .hasMessageContaining("timeoutDiagnostics={")
                .hasMessageContaining("dataLockWaits=")
                .hasMessageContaining("metadataLocks=")
                .hasMessageContaining("activeTransactions=")
                .satisfies(exception -> assertThat(exception.getMessage())
                    .doesNotContain("metadataLocks=unavailable(")
                    .doesNotContain("activeTransactions=unavailable("));
        } finally {
            dropFixtureTables();
        }
    }

    private void createFixtureTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + CHILD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + PARENT_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + PARENT_TABLE + " (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("""
            CREATE TABLE test_cleanup_child (
                id BIGINT PRIMARY KEY,
                parent_id BIGINT NOT NULL,
                CONSTRAINT fk_test_cleanup_parent
                    FOREIGN KEY (parent_id) REFERENCES test_cleanup_parent (id)
            )
            """);
    }

    private void dropFixtureTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + CHILD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + PARENT_TABLE);
    }

    private Connection openBlockingConnection() throws Exception {
        return SharedMySqlTestContainer.openConnection();
    }

    private void blockParentTableCleanup(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM " + PARENT_TABLE + " WHERE id = ? FOR UPDATE"
        )) {
            statement.setLong(1, 1L);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private int countRows(String tableName) {
        return Objects.requireNonNull(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class)
        );
    }

    private int findForeignKeyChecks() {
        return Objects.requireNonNull(
            jdbcTemplate.queryForObject("SELECT @@SESSION.FOREIGN_KEY_CHECKS", Integer.class)
        );
    }
}
