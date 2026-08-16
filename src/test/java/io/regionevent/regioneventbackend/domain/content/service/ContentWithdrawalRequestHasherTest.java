package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentWithdrawalRequestHasherTest {

    private final ContentWithdrawalRequestHasher hasher = new ContentWithdrawalRequestHasher();

    @Test
    void 멱등_키의_UTF8_바이트를_SHA256_hex로_해시한다() {
        assertThat(hasher.hashIdempotencyKey("김해-idempotency-key"))
            .isEqualTo("f48885bad436718c71e6193155283a6b4b1db4f1cb6db00fb9e3064bde4923c3");
    }
}
