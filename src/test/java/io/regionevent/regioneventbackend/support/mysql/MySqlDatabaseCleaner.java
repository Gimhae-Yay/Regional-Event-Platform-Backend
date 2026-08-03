package io.regionevent.regioneventbackend.support.mysql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

public final class MySqlDatabaseCleaner {

    private static final String FLYWAY_SCHEMA_HISTORY = "flyway_schema_history";

    private final ConnectionProvider connectionProvider;

    public MySqlDatabaseCleaner(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    private MySqlDatabaseCleaner(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void clean() {
        try (Connection connection = connectionProvider.open()) {
            clean(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("MySQL test database cleanup failed", exception);
        }
    }

    static MySqlDatabaseCleaner forSharedContainer() {
        return new MySqlDatabaseCleaner(SharedMySqlTestContainer::openConnection);
    }

    private void clean(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        int originalForeignKeyChecks = findForeignKeyChecks(connection);
        try {
            if (!originalAutoCommit) {
                connection.setAutoCommit(true);
            }
            setForeignKeyChecks(connection, 0);
            for (String tableName : findApplicationTables(connection)) {
                truncate(connection, tableName);
            }
        } finally {
            setForeignKeyChecks(connection, originalForeignKeyChecks);
            if (!originalAutoCommit) {
                connection.setAutoCommit(false);
            }
        }
    }

    private int findForeignKeyChecks(Connection connection) throws SQLException {
        try (
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT @@SESSION.FOREIGN_KEY_CHECKS")
        ) {
            if (!resultSet.next()) {
                throw new SQLException("FOREIGN_KEY_CHECKS query returned no result");
            }
            return resultSet.getInt(1);
        }
    }

    private void setForeignKeyChecks(Connection connection, int enabled) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION FOREIGN_KEY_CHECKS = " + enabled);
        }
    }

    private List<String> findApplicationTables(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> tableNames = new ArrayList<>();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (!FLYWAY_SCHEMA_HISTORY.equalsIgnoreCase(tableName)) {
                    tableNames.add(tableName);
                }
            }
        }
        tableNames.sort(Comparator.naturalOrder());
        return tableNames;
    }

    private void truncate(Connection connection, String tableName) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        String escapedTableName = tableName.replace(quote, quote + quote);
        try (Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE " + quote + escapedTableName + quote);
        }
    }

    @FunctionalInterface
    private interface ConnectionProvider {

        Connection open() throws SQLException;
    }
}
