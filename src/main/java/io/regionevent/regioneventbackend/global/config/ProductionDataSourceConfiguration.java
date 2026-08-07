package io.regionevent.regioneventbackend.global.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
@EnableConfigurationProperties(ProductionDataSourceConfiguration.Properties.class)
public class ProductionDataSourceConfiguration {

    @Validated
    @ConfigurationProperties(prefix = "spring.datasource")
    public record Properties(
        @NotBlank String url,
        @NotBlank String username,
        @NotBlank String password
    ) {

        @AssertTrue(message = "spring.datasource.url must use the MySQL JDBC scheme in the prod profile")
        public boolean isMysqlJdbcUrl() {
            return url != null && url.startsWith("jdbc:mysql:");
        }
    }
}
