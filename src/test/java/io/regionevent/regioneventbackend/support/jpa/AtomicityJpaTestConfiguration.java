package io.regionevent.regioneventbackend.support.jpa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(WithdrawalAtomicityJpaTestConfiguration.class)
public class AtomicityJpaTestConfiguration {
}
