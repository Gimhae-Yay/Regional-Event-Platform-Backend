package io.regionevent.regioneventbackend.support.jpa;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class H2DatabaseCleanupTestExecutionListener extends AbstractTestExecutionListener {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    @Override
    public void afterTestMethod(TestContext testContext) {
        JdbcTemplate jdbcTemplate = testContext.getApplicationContext().getBean(JdbcTemplate.class);
        List<String> tableNames = jdbcTemplate.queryForList("""
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'PUBLIC'
              AND TABLE_TYPE = 'BASE TABLE'
            """, String.class);

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tableNames.stream()
                .filter(tableName -> !tableName.equalsIgnoreCase("flyway_schema_history"))
                .forEach(tableName -> truncate(jdbcTemplate, tableName));
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private void truncate(JdbcTemplate jdbcTemplate, String tableName) {
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalStateException("unexpected H2 table name: " + tableName);
        }
        jdbcTemplate.execute("TRUNCATE TABLE \"" + tableName + "\" RESTART IDENTITY");
    }
}
