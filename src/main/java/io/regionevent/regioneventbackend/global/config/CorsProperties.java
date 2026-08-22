package io.regionevent.regioneventbackend.global.config;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cors")
public class CorsProperties implements InitializingBean {

    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.stream().allMatch(String::isBlank)) {
            this.allowedOrigins = List.of();
            return;
        }
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    public void afterPropertiesSet() {
        allowedOrigins.forEach(this::validateAllowedOrigin);
    }

    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    private void validateAllowedOrigin(String allowedOrigin) {
        try {
            URI uri = URI.create(allowedOrigin);
            if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || hasPath(uri)
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || allowedOrigin.contains("*")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "security.cors.allowed-origins에는 경로·쿼리·fragment·와일드카드가 없는 HTTPS Origin만 설정할 수 있습니다.",
                exception
            );
        }
    }

    private boolean hasPath(URI uri) {
        return uri.getRawPath() != null && !uri.getRawPath().isEmpty();
    }
}
