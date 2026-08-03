package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class SharedMySqlTestContainer {

    public static final String IMAGE_NAME = "mysql:8.0.42";

    private SharedMySqlTestContainer() {
    }

    public static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registerDataSourceProperties(registry, Map.of());
    }

    public static void registerDataSourceProperties(
        DynamicPropertyRegistry registry,
        Map<String, String> jdbcOptions
    ) {
        Objects.requireNonNull(registry, "registry must not be null");
        Map<String, String> options = validateJdbcOptions(jdbcOptions);

        registry.add("spring.datasource.url", () -> appendJdbcOptions(container().getJdbcUrl(), options));
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

    private static Map<String, String> validateJdbcOptions(Map<String, String> jdbcOptions) {
        Objects.requireNonNull(jdbcOptions, "jdbcOptions must not be null");
        Map<String, String> validatedOptions = new LinkedHashMap<>();
        jdbcOptions.forEach((name, value) -> {
            if (name == null || !name.matches("[A-Za-z][A-Za-z0-9._-]*")) {
                throw new IllegalArgumentException("invalid JDBC option name: " + name);
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("JDBC option value must not be blank: " + name);
            }
            validatedOptions.put(name, value);
        });
        return validatedOptions;
    }

    static String appendJdbcOptions(String jdbcUrl, Map<String, String> jdbcOptions) {
        if (jdbcOptions.isEmpty()) {
            return jdbcUrl;
        }
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        String query = jdbcOptions.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
        return jdbcUrl + separator + query;
    }

    private static final class ContainerHolder {

        private static final MySQLContainer INSTANCE = startContainer();

        private ContainerHolder() {
        }
    }
}
