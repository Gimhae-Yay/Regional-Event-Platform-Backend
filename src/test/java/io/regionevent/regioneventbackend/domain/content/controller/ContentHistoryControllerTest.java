package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import io.regionevent.regioneventbackend.domain.content.dto.ContentHistoryResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentHistoryResult;
import io.regionevent.regioneventbackend.domain.content.service.GetContentHistoryUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

class ContentHistoryControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long CONTENT_ID = 10L;

    private final GetContentHistoryUseCase getContentHistoryUseCase = mock(GetContentHistoryUseCase.class);
    private final ContentHistoryController contentHistoryController =
        new ContentHistoryController(getContentHistoryUseCase);

    @Test
    void getContentHistory_returnsApiContractResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);
        when(getContentHistoryUseCase.get(USER_ID, CONTENT_ID)).thenReturn(new ContentHistoryResult(
            CONTENT_ID,
            List.of(new ContentHistoryResult.History(
                ContentLogStatus.REJECTED,
                "필수 정보가 누락되었습니다.",
                Instant.parse("2026-08-01T00:00:00Z"),
                new ContentHistoryResult.Actor(20L, "김해 지역 관리자")
            ))
        ));

        ResponseEntity<ApiResponse<ContentHistoryResponse>> response =
            contentHistoryController.getContentHistory(authentication, CONTENT_ID.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SUCCESS");
        assertThat(response.getBody().message()).isEqualTo("콘텐츠 이력 조회에 성공했습니다.");
        assertThat(response.getBody().data().contentId()).isEqualTo(CONTENT_ID);
        assertThat(response.getBody().data().histories()).singleElement().satisfies(history -> {
            assertThat(history.status()).isEqualTo("REJECTED");
            assertThat(history.reason()).isEqualTo("필수 정보가 누락되었습니다.");
            assertThat(history.processedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
            assertThat(history.actor()).isEqualTo(new ContentHistoryResponse.Actor(20L, "김해 지역 관리자"));
        });
    }
}
