package io.regionevent.regioneventbackend.global.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

class BearerAccessTokenAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_whenTokenIsValid_setsLongPrincipalAndGrantedAuthorities() throws Exception {
        JwtAccessTokenService jwtAccessTokenService = mock(JwtAccessTokenService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        BearerAccessTokenAuthenticationFilter filter = new BearerAccessTokenAuthenticationFilter(
            jwtAccessTokenService,
            authenticationEntryPoint
        );
        when(jwtAccessTokenService.authenticate("access-token")).thenReturn(
            new JwtAccessTokenService.AuthenticatedUser(
                1L,
                List.of(AccessTokenAuthority.VISITOR, AccessTokenAuthority.OPERATOR)
            )
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer access-token");
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
            authentication.set(SecurityContextHolder.getContext().getAuthentication())
        );

        assertThat(authentication.get().getPrincipal()).isEqualTo(1L);
        assertThat(authentication.get().getAuthorities())
            .extracting(grantedAuthority -> grantedAuthority.getAuthority())
            .containsExactly("ROLE_VISITOR", "ROLE_OPERATOR");
    }
}
