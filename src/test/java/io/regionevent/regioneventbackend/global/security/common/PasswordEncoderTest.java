package io.regionevent.regioneventbackend.global.security.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.regionevent.regioneventbackend.global.config.SecurityConfig;

class PasswordEncoderTest {

    @Test
    void passwordEncoder_encodesWithBcryptPrefixAndMatchesRawPassword() {
        PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

        String firstHash = passwordEncoder.encode("LocalStamp!2026");
        String secondHash = passwordEncoder.encode("LocalStamp!2026");

        assertThat(firstHash).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches("LocalStamp!2026", firstHash)).isTrue();
        assertThat(passwordEncoder.matches("Different!2026", firstHash)).isFalse();
        assertThat(secondHash).isNotEqualTo(firstHash);
    }
}
