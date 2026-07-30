package io.regionevent.regioneventbackend.domain.reservation.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class ReservationNumberGenerator {

    private static final char[] CROCKFORD_BASE32_CHARACTERS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_SUFFIX_LENGTH = 12;
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    private final SecureRandom secureRandom;

    public ReservationNumberGenerator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    ReservationNumberGenerator(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public String generate() {
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE_ID));
        StringBuilder reservationNumber = new StringBuilder("R").append(today.toString().replace("-", ""));

        for (int index = 0; index < RANDOM_SUFFIX_LENGTH; index++) {
            reservationNumber.append(CROCKFORD_BASE32_CHARACTERS[secureRandom.nextInt(CROCKFORD_BASE32_CHARACTERS.length)]);
        }
        return reservationNumber.toString();
    }
}
