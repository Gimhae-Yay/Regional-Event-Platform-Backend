package io.regionevent.regioneventbackend.domain.region.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RegionRepositoryTest {

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 지역을_저장하고_식별자로_조회한다() {
        Region region = regionRepository.saveAndFlush(
            new Region("GIMHAE", "김해시", true)
        );

        Region foundRegion = regionRepository.findById(region.getRegionId()).orElseThrow();

        assertThat(foundRegion.getRegionCode()).isEqualTo("GIMHAE");
        assertThat(foundRegion.getName()).isEqualTo("김해시");
        assertThat(foundRegion.isPublic()).isTrue();
        assertThat(foundRegion.getCreatedAt()).isNotNull();
        assertThat(foundRegion.getUpdatedAt()).isNotNull();
    }

    @Test
    void 지역_코드는_중복될_수_없다() {
        regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));

        assertThatThrownBy(
            () -> regionRepository.saveAndFlush(new Region("GIMHAE", "다른 지역", false))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
