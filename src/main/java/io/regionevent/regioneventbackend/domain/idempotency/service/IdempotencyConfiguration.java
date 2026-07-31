package io.regionevent.regioneventbackend.domain.idempotency.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfiguration {
}
