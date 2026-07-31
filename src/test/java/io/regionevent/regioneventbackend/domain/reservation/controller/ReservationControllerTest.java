package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.regionevent.regioneventbackend.domain.reservation.dto.ReservationConfirmationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.usecase.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;

class ReservationControllerTest {

    private ReservationConfirmationUseCase reservationConfirmationUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reservationConfirmationUseCase = Mockito.mock(ReservationConfirmationUseCase.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReservationController(reservationConfirmationUseCase))
            .build();
    }

    @Test
    void confirm_성공하면_실제_HTTP_201을_반환한다() throws Exception {
        ReservationConfirmationResponse response = new ReservationConfirmationResponse(
            "101",
            "R20260730A7K3M9Q2W5XZ",
            "10",
            "20",
            ReservationStatus.CONFIRMED,
            Instant.parse("2026-07-30T00:00:00Z")
        );
        given(reservationConfirmationUseCase.confirm(1L, 10L, "confirmation-key", "request-id"))
            .willReturn(response);

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("1", null, "ROLE_VISITOR");

        mockMvc.perform(post("/api/v1/reservation-holds/10/confirm")
                .header("Idempotency-Key", "confirmation-key")
                .principal(authentication)
                .requestAttr(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-id"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.data.reservationId").value("101"));
    }

}
