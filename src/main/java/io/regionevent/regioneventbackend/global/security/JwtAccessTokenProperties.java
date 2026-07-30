package io.regionevent.regioneventbackend.global.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt.access")
public class JwtAccessTokenProperties {

    private String issuer;
    private String audience;
    private String activeKeyId;
    private String activeKey;
    private List<VerificationKey> previousKeys = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

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

    public List<VerificationKey> getPreviousKeys() {
        return previousKeys;
    }

    public void setPreviousKeys(List<VerificationKey> previousKeys) {
        this.previousKeys = new ArrayList<>(previousKeys);
    }

    public static class VerificationKey {

        private String keyId;
        private String key;

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
    }
}
