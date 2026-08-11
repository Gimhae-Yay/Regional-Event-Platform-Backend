package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V35__ExpandPaymentDiscrepancyActionEvidenceReference extends BaseJavaMigration {

    private static final String MYSQL_PRODUCT_NAME = "MySQL";
    private static final String H2_ALTER_SQL = """
        ALTER TABLE payment_discrepancy_action
            ALTER COLUMN evidence_reference VARCHAR(500)
        """;
    private static final String MYSQL_ALTER_SQL = """
        ALTER TABLE payment_discrepancy_action
            MODIFY evidence_reference VARCHAR(500)
        """;

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        String alterSql = MYSQL_PRODUCT_NAME.equals(connection.getMetaData().getDatabaseProductName())
            ? MYSQL_ALTER_SQL
            : H2_ALTER_SQL;
        try (Statement statement = connection.createStatement()) {
            statement.execute(alterSql);
        }
    }
}
