package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class SharedMySqlTestContainer {

    public static final String IMAGE_NAME = "mysql:8.0.42";

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
    }

    public static String getContainerId() {
        return container().getContainerId();
    }

    public static String getJdbcUrl() {
        return container().getJdbcUrl();
    }

    static Connection openConnection() throws SQLException {
        MySQLContainer mysql = container();
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private static MySQLContainer container() {
        return ContainerHolder.INSTANCE;
    }

    private static MySQLContainer startContainer() {
        MySQLContainer mysql = new MySQLContainer(DockerImageName.parse(IMAGE_NAME));
        mysql.start();
        return mysql;
    }

    private static final class ContainerHolder {

        private static final MySQLContainer INSTANCE = startContainer();

        private ContainerHolder() {
        }
    }
}
