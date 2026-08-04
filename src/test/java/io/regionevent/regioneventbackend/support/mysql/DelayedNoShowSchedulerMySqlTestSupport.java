package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "reservation.no-show-completion.initial-delay=PT24H")
public abstract class DelayedNoShowSchedulerMySqlTestSupport extends NonTransactionalMySqlTestSupport {
}
