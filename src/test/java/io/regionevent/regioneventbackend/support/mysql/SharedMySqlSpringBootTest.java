package io.regionevent.regioneventbackend.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

@SpringBootTest
class SharedMySqlSpringBootTest extends TransactionalMySqlTestSupport {

    private final DataSource dataSource;

    @Autowired
    SharedMySqlSpringBootTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void SpringBootTest는_JVM_공유_MySQL과_롤백_격리를_사용한다() throws SQLException {
        String containerId = SharedMySqlTestContainer.getContainerId();

        SharedMySqlContainerUsage.record("SpringBootTest", containerId);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(SharedMySqlTestContainer.getJdbcUrl());
        }
        assertThat(containerId).isNotBlank();
        assertThat(TestTransaction.isActive()).isTrue();
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();
    }
}
