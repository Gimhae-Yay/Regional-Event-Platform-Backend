package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PublicCatalogStaticInfoTest {

    @Test
    void publicContentStaticInfo_내부_정합성_위반은_공통_서버_오류를_반환한다() {
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
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
        );
    }

    @Test
    void publicRegionStaticInfo_내부_정합성_위반은_공통_서버_오류를_반환한다() {
        assertThatThrownBy(() -> new PublicRegionStaticInfo(10L, "GIMHAE", " "))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }
}
