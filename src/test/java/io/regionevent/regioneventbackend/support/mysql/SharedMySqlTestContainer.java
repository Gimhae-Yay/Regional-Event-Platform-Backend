package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class SharedMySqlTestContainer {

    public static final String IMAGE_NAME = "mysql:8.0.42";
    private static final String MYSQL_DATA_DIRECTORY = "/var/lib/mysql";
    private static final String MYSQL_DATA_DIRECTORY_TMPFS_OPTIONS = "rw,size=512m";
    private static final int MAXIMUM_POOL_SIZE = 4;

    private SharedMySqlTestContainer() {
    }

    public static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registerDataSourceProperties(registry, UnaryOperator.identity());
    }

    public static void registerDataSourceProperties(
        DynamicPropertyRegistry registry,
        UnaryOperator<String> jdbcUrlCustomizer
    ) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(jdbcUrlCustomizer, "jdbcUrlCustomizer must not be null");

        registry.add("spring.datasource.url", () -> jdbcUrlCustomizer.apply(container().getJdbcUrl()));
        registry.add("spring.datasource.username", () -> container().getUsername());
        registry.add("spring.datasource.password", () -> container().getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> MAXIMUM_POOL_SIZE);
    }

    public static String getContainerId() {
        return container().getContainerId();
    }

    public static String getJdbcUrl() {
        return container().getJdbcUrl();
    }

    public static void grantLockMonitoringPrivileges() {
        grantLockMonitoringPrivileges(container());
    }

    private static void grantLockMonitoringPrivileges(MySQLContainer mysql) {
        try (
            Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(),
                "root",
                mysql.getPassword()
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute("GRANT SELECT ON performance_schema.data_lock_waits TO 'test'@'%'");
            statement.execute("GRANT SELECT ON performance_schema.data_locks TO 'test'@'%'");
            statement.execute("GRANT SELECT ON performance_schema.metadata_locks TO 'test'@'%'");
            statement.execute("GRANT SELECT ON performance_schema.threads TO 'test'@'%'");
            statement.execute("GRANT PROCESS ON *.* TO 'test'@'%'");
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to grant MySQL lock monitoring privileges", exception);
        }
    }

    static Connection openConnection() throws SQLException {
        MySQLContainer mysql = container();
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private static MySQLContainer container() {
        return ContainerHolder.INSTANCE;
    }

    static MySQLContainer createContainer() {
        MySQLContainer mysql = new MySQLContainer(DockerImageName.parse(IMAGE_NAME));
        mysql.withTmpFs(Map.of(MYSQL_DATA_DIRECTORY, MYSQL_DATA_DIRECTORY_TMPFS_OPTIONS));
        return mysql;
    }

    private static MySQLContainer startContainer() {
        MySQLContainer mysql = createContainer();
        mysql.start();
        grantLockMonitoringPrivileges(mysql);
        return mysql;
    }

    private static final class ContainerHolder {

        private static final MySQLContainer INSTANCE = startContainer();

        private ContainerHolder() {
        }
    }
}
