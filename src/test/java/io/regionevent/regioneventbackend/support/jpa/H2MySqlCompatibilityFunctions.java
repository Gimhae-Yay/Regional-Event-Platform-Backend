package io.regionevent.regioneventbackend.support.jpa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class H2MySqlCompatibilityFunctions {

    private H2MySqlCompatibilityFunctions() {
    }

    public static BigDecimal unixTimestamp(OffsetDateTime value) {
        return BigDecimal.valueOf(value.toInstant().toEpochMilli())
            .movePointLeft(3);
    }
}
