package io.regionevent.regioneventbackend.support.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SharedMySqlTestContainerTest {

    @Test
    void JDBC_옵션을_기존_쿼리_뒤에_추가한다() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("useAffectedRows", "true");
        options.put("sessionVariables", "innodb_lock_wait_timeout=1");

        String jdbcUrl = SharedMySqlTestContainer.appendJdbcOptions(
            "jdbc:mysql://localhost/test?useSSL=false",
            options
        );

        assertThat(jdbcUrl).isEqualTo(
            "jdbc:mysql://localhost/test?useSSL=false"
                + "&useAffectedRows=true&sessionVariables=innodb_lock_wait_timeout=1"
        );
    }

    @Test
    void JDBC_옵션이_없으면_URL을_변경하지_않는다() {
        String jdbcUrl = "jdbc:mysql://localhost/test";

        assertThat(SharedMySqlTestContainer.appendJdbcOptions(jdbcUrl, Map.of())).isEqualTo(jdbcUrl);
    }

    @Test
    void JDBC_옵션_이름과_값을_검증한다() {
        assertThatThrownBy(() -> SharedMySqlTestContainer.registerDataSourceProperties(
            (name, supplier) -> {
            },
            Map.of("invalid option", "true")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SharedMySqlTestContainer.registerDataSourceProperties(
            (name, supplier) -> {
            },
            Map.of("useAffectedRows", " ")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
