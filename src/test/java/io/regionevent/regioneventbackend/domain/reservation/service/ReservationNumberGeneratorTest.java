package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ReservationNumberGeneratorTest {

    @Test
    void generate_서울_기준_날짜와_12자리_Crockford_Base32_접미사를_생성한다() {
        ReservationNumberGenerator generator = new ReservationNumberGenerator(
            Clock.fixed(Instant.parse("2026-07-30T15:00:00Z"), ZoneOffset.UTC),
            new SecureRandom()
        );

        String reservationNumber = generator.generate();

        assertThat(reservationNumber).matches("R20260731[0-9A-HJKMNPQRSTVWXYZ]{12}");
    }
}
