package io.regionevent.regioneventbackend.domain.region.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RegionRepositoryTest {

    private final RegionRepository regionRepository;

    @Autowired
    RegionRepositoryTest(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

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

    @Test
    void 공개_지역_검증_정보를_이름과_지역_식별자_오름차순으로_조회한다() {
        Region privateRegion = regionRepository.saveAndFlush(new Region("PRIVATE", "Aardvark", false));
        Region beta = regionRepository.saveAndFlush(new Region("BETA", "Beta", true));
        Region firstSameName = regionRepository.saveAndFlush(new Region("SAME-ONE", "Same", true));
        Region secondSameName = regionRepository.saveAndFlush(new Region("SAME-TWO", "Same", true));

        List<PublicRegionVerificationProjection> regions = regionRepository.findPublicRegionVerifications();

        assertThat(regions)
            .extracting(PublicRegionVerificationProjection::regionId)
            .containsExactly(
                beta.getRegionId(),
                firstSameName.getRegionId(),
                secondSameName.getRegionId()
            );
        assertThat(regions)
            .extracting(PublicRegionVerificationProjection::regionId)
            .doesNotContain(privateRegion.getRegionId());
    }

    @Test
    void 공개_지역_정적_표시_정보를_별도로_조회한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));

        PublicRegionStaticProjection staticInfo = regionRepository.findPublicRegionStaticInfo(
            region.getRegionId()
        ).orElseThrow();

        assertThat(staticInfo).isEqualTo(
            new PublicRegionStaticProjection(region.getRegionId(), "GIMHAE", "김해시")
        );
    }
}
