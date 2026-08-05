package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;

class PublicCatalogStaticInfoTest {

    @Test
    void publicContentStaticInfo_내부_정합성_위반은_인수_계약_예외를_던진다() {
        assertThatThrownBy(() -> new PublicContentStaticInfo(
            10L,
            200L,
            3,
            ContentType.EVENT_EXPERIENCE,
            "지역 축제",
            "축제 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            " "
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("cancellationPolicyText must not be blank");
    }

    @Test
    void publicRegionStaticInfo_내부_정합성_위반은_인수_계약_예외를_던진다() {
        assertThatThrownBy(() -> new PublicRegionStaticInfo(10L, "GIMHAE", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not be blank");
    }
}
