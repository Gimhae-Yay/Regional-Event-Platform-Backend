package io.regionevent.regioneventbackend.global.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty() || isBlankDefaultValue(allowedOrigins)) {
            this.allowedOrigins = List.of();
            return;
        }

        allowedOrigins.forEach(CorsProperties::validateAllowedOrigin);
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    private static boolean isBlankDefaultValue(List<String> allowedOrigins) {
        return allowedOrigins.size() == 1 && allowedOrigins.getFirst().isBlank();
    }

    private static void validateAllowedOrigin(String allowedOrigin) {
        if (allowedOrigin == null || allowedOrigin.isBlank() || allowedOrigin.contains("*")) {
            throw new IllegalArgumentException("allowed origin must be an HTTPS origin without wildcards");
        }

        URI uri;
        try {
            uri = URI.create(allowedOrigin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("allowed origin must be a valid HTTPS origin", exception);
        }

        if (!"https".equals(uri.getScheme())
            || uri.getHost() == null
            || uri.getRawUserInfo() != null
            || hasText(uri.getRawPath())
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("allowed origin must contain only scheme, host, and port");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
