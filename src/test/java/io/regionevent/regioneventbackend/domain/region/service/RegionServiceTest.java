package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock
    private RegionRepository regionRepository;

    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = new RegionService(regionRepository);
    }

    @Test
    void createPrivateRegion_존재하는정규화코드_중복오류를반환한다() {
        when(regionRepository.existsByRegionCode("JEONJU")).thenReturn(true);

        assertThatThrownBy(() -> regionService.createPrivateRegion("JEONJU", "전주시"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_CODE_ALREADY_EXISTS)
            );

        verify(regionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPrivateRegion_새코드_비공개지역을저장한다() {
        when(regionRepository.existsByRegionCode("JEONJU")).thenReturn(false);
        when(regionRepository.saveAndFlush(any(Region.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Region region = regionService.createPrivateRegion("JEONJU", "전주시");

        ArgumentCaptor<Region> regionCaptor = ArgumentCaptor.forClass(Region.class);
        verify(regionRepository).saveAndFlush(regionCaptor.capture());
        assertThat(region).isSameAs(regionCaptor.getValue());
        assertThat(region.getRegionCode()).isEqualTo("JEONJU");
        assertThat(region.getName()).isEqualTo("전주시");
        assertThat(region.isPublic()).isFalse();
    }

    @Test
    void createPrivateRegion_유일제약충돌_중복오류를반환한다() {
        when(regionRepository.existsByRegionCode("JEONJU")).thenReturn(false);
        when(regionRepository.saveAndFlush(any(Region.class))).thenThrow(regionCodeConstraintViolation());

        assertThatThrownBy(() -> regionService.createPrivateRegion("JEONJU", "전주시"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_CODE_ALREADY_EXISTS)
            );
    }

    private DataIntegrityViolationException regionCodeConstraintViolation() {
        return new DataIntegrityViolationException(
            "duplicate region code",
            new ConstraintViolationException(
                "duplicate region code",
                new SQLException("duplicate region code"),
                "uk_region_region_code"
            )
        );
    }
}
