package io.regionevent.regioneventbackend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    @Test
    void doFilter_assignsRequestIdOnlyDuringRequest() throws Exception {
        RequestIdFilter requestIdFilter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isInstanceOf(String.class);
            assertThat(RequestIdFilter.currentRequestId()).isEqualTo(
                servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)
            );
        });

        assertThat(RequestIdFilter.currentRequestId()).isNull();
    }
}
