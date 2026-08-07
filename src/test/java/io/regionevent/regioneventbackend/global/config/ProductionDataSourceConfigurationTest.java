package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionDataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues("spring.profiles.active=prod")
        .withUserConfiguration(ProductionDataSourceConfiguration.class);

    @Test
    void prod_프로필에서_MySQL_데이터소스_구성이면_시작한다() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:mysql://rds.internal:3306/regional_event",
                "spring.datasource.username=regional_event",
                "spring.datasource.password=secret"
            )
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prod_프로필에서_H2_URL이면_시작하지_않는다() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:regional-event",
                "spring.datasource.username=sa",
                "spring.datasource.password=password"
            )
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prod_프로필에서_데이터소스_비밀번호가_없으면_시작하지_않는다() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:mysql://rds.internal:3306/regional_event",
                "spring.datasource.username=regional_event"
            )
            .run(context -> assertThat(context).hasFailed());
    }
}
