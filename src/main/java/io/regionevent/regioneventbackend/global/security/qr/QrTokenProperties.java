package io.regionevent.regioneventbackend.global.security.qr;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.qr")
public class QrTokenProperties {

    private String activeKeyId;
    private String activeKey;
    private Duration tokenTtl;
    private List<VerificationKey> previousKeys = new ArrayList<>();

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public List<VerificationKey> getPreviousKeys() {
        return previousKeys;
    }

    public void setPreviousKeys(List<VerificationKey> previousKeys) {
        this.previousKeys = new ArrayList<>(previousKeys);
    }

    public static class VerificationKey {

        private String keyId;
        private String key;
        private Instant verificationEndsAt;

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Instant getVerificationEndsAt() {
            return verificationEndsAt;
        }

        public void setVerificationEndsAt(Instant verificationEndsAt) {
            this.verificationEndsAt = verificationEndsAt;
        }
    }
}
