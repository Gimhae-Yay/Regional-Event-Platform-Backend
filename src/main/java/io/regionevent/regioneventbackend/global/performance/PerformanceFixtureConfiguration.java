package io.regionevent.regioneventbackend.global.performance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PerformanceFixtureProperties.class)
public class PerformanceFixtureConfiguration {
}
