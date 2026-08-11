package io.regionevent.regioneventbackend.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PortOnePaymentAdapterTest {

    @Test
    void hash_성공과_명시적_실패_응답_원문이_다르면_서로_다른_SHA_256을_반환한다() {
        byte[] succeededResponse = "{\"cancellation\":{\"id\":\"cancel-1\",\"status\":\"SUCCEEDED\"}}"
            .getBytes(StandardCharsets.UTF_8);
        byte[] failedResponse = "{\"cancellation\":{\"id\":\"cancel-2\",\"status\":\"FAILED\"}}"
            .getBytes(StandardCharsets.UTF_8);

        String succeededHash = PortOnePaymentAdapter.hash(succeededResponse);
        String failedHash = PortOnePaymentAdapter.hash(failedResponse);

        assertThat(succeededHash)
            .isEqualTo("348114deb878d7f0476517062eddb997092aaf354b6beea252a16ab33381a4a1");
        assertThat(failedHash).isNotEqualTo(succeededHash);
        assertThat(succeededHash).doesNotContain("cancel-1", "SUCCEEDED");
        assertThat(failedHash).doesNotContain("cancel-2", "FAILED");
    }
}
