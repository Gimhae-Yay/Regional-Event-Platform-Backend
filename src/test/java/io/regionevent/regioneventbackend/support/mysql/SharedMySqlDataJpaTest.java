package io.regionevent.regioneventbackend.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedMySqlDataJpaTest extends TransactionalMySqlTestSupport {

    private final DataSource dataSource;

    @Autowired
    SharedMySqlDataJpaTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry, Map.of("useAffectedRows", "true"));
    }

    @Test
    void DataJpaTest는_같은_MySQL에서_JDBC_옵션과_롤백_격리를_사용한다() throws SQLException {
        String containerId = SharedMySqlTestContainer.getContainerId();

        SharedMySqlContainerUsage.record("DataJpaTest", containerId);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                .isEqualTo(SharedMySqlTestContainer.appendJdbcOptions(
                    SharedMySqlTestContainer.getJdbcUrl(),
                    Map.of("useAffectedRows", "true")
                ));
        }
        assertThat(containerId).isNotBlank();
        assertThat(TestTransaction.isActive()).isTrue();
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();
    }
}
