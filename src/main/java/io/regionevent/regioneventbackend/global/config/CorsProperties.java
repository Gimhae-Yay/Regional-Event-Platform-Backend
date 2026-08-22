package io.regionevent.regioneventbackend.global.config;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cors")
public class CorsProperties implements InitializingBean {

    private static final String HTTPS_SCHEME = "https";
    private static final int HTTPS_DEFAULT_PORT = 443;

    private List<String> allowedOrigins = List.of();
    private String apiPublicOrigin;
    private String siteRegistrableDomain;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.stream().allMatch(this::isBlank)) {
            this.allowedOrigins = List.of();
            return;
        }
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    public void setApiPublicOrigin(String apiPublicOrigin) {
        this.apiPublicOrigin = apiPublicOrigin;
    }

    public void setSiteRegistrableDomain(String siteRegistrableDomain) {
        this.siteRegistrableDomain = siteRegistrableDomain;
    }

    @Override
    public void afterPropertiesSet() {
        if (allowedOrigins.isEmpty()) {
            return;
        }

        NormalizedOrigin normalizedApiPublicOrigin = normalizeOrigin(
            apiPublicOrigin,
            "security.cors.api-public-origin"
        );
        String normalizedSiteRegistrableDomain = normalizeSiteRegistrableDomain();
        validateOriginInSite(
            normalizedApiPublicOrigin,
            normalizedSiteRegistrableDomain,
            "security.cors.api-public-origin"
        );

        this.allowedOrigins = allowedOrigins.stream()
            .map(origin -> normalizeOrigin(origin, "security.cors.allowed-origins"))
            .map(origin -> validateOriginInSite(
                origin,
                normalizedSiteRegistrableDomain,
                "security.cors.allowed-origins"
            ))
            .map(NormalizedOrigin::value)
            .distinct()
            .toList();
    }

    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    private NormalizedOrigin normalizeOrigin(String origin, String propertyName) {
        if (isBlank(origin)) {
            throw invalidConfiguration(propertyName);
        }

        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            int port = uri.getPort();
            if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())
                || host == null
                || isIpAddress(host)
                || uri.getRawUserInfo() != null
                || hasPath(uri)
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || origin.contains("*")
                || port > 65535) {
                throw new IllegalArgumentException();
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String normalizedPort = port == -1 || port == HTTPS_DEFAULT_PORT ? "" : ":" + port;
            return new NormalizedOrigin(HTTPS_SCHEME + "://" + normalizedHost + normalizedPort, normalizedHost);
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration(propertyName, exception);
        }
    }

    private String normalizeSiteRegistrableDomain() {
        if (isBlank(siteRegistrableDomain)) {
            throw invalidConfiguration("security.cors.site-registrable-domain");
        }

        String normalizedSiteRegistrableDomain = siteRegistrableDomain.toLowerCase(Locale.ROOT);
        if (isIpAddress(normalizedSiteRegistrableDomain)
            || !normalizedSiteRegistrableDomain.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")) {
            throw new IllegalStateException(
                "security.cors.site-registrable-domain에는 IP·와일드카드·경로가 없는 도메인을 설정해야 합니다."
            );
        }
        return normalizedSiteRegistrableDomain;
    }

    private NormalizedOrigin validateOriginInSite(
        NormalizedOrigin origin,
        String siteRegistrableDomain,
        String propertyName
    ) {
        String host = origin.host();
        if (!host.equals(siteRegistrableDomain) && !host.endsWith("." + siteRegistrableDomain)) {
            throw invalidConfiguration(propertyName);
        }
        return origin;
    }

    private boolean hasPath(URI uri) {
        return uri.getRawPath() != null && !uri.getRawPath().isEmpty();
    }

    private boolean isIpAddress(String host) {
        return host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private IllegalStateException invalidConfiguration(String propertyName) {
        return invalidConfiguration(propertyName, null);
    }

    private IllegalStateException invalidConfiguration(String propertyName, IllegalArgumentException cause) {
        return new IllegalStateException(
            propertyName + "에는 IP·경로·쿼리·fragment·사용자 정보·와일드카드가 없는 HTTPS Origin을 설정해야 합니다.",
            cause
        );
    }

    private record NormalizedOrigin(String value, String host) {
    }
}
