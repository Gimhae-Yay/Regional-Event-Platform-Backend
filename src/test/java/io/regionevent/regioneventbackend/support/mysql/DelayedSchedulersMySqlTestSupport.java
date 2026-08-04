package io.regionevent.regioneventbackend.support.mysql;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
public abstract class DelayedSchedulersMySqlTestSupport extends NonTransactionalMySqlTestSupport {
}
