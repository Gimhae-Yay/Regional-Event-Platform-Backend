package io.regionevent.regioneventbackend.support.mysql;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class SharedMySqlTestContainerTest {

    @Test
    void 새_MySQL_컨테이너는_데이터_디렉터리를_tmpfs로_구성한다() {
        MySQLContainer mysql = SharedMySqlTestContainer.createContainer();

        assertThat(mysql.getTmpFsMapping()).containsExactly(entry("/var/lib/mysql", "rw,size=512m"));
    }
}
