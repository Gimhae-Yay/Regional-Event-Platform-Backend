package io.regionevent.regioneventbackend.domain.reservation.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class ReservationIdentifierGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_SUFFIX_LENGTH = 12;
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecureRandom secureRandom;

    public ReservationIdentifierGenerator() {
        this(new SecureRandom());
    }

    ReservationIdentifierGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public ReservationIdentifiers generate(Instant confirmedAt) {
        String reservationNo = "R" + DATE_FORMATTER.format(confirmedAt.atZone(KOREA_ZONE_ID))
            + createRandomSuffix();
        return new ReservationIdentifiers(reservationNo, UUID.randomUUID().toString());
    }

    private String createRandomSuffix() {
        StringBuilder suffix = new StringBuilder(RANDOM_SUFFIX_LENGTH);
        for (int index = 0; index < RANDOM_SUFFIX_LENGTH; index++) {
            suffix.append(CROCKFORD_BASE32[secureRandom.nextInt(CROCKFORD_BASE32.length)]);
        }
        return suffix.toString();
    }

    public record ReservationIdentifiers(String reservationNo, String qrReference) {
    }
}
