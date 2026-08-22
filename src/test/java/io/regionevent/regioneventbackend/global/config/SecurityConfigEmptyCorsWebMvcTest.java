package io.regionevent.regioneventbackend.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;

@WebMvcTest(
    value = SecurityConfigEmptyCorsWebMvcTest.EmptyCorsTestController.class,
    properties = "security.cors.allowed-origins="
)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class,
    SecurityConfigEmptyCorsWebMvcTest.EmptyCorsTestController.class
})
class SecurityConfigEmptyCorsWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_빈Allowlist는교차출처를허용하지않는다() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "https://local-stamp.org")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @RestController
    static class EmptyCorsTestController {

        @PostMapping("/api/v1/auth/refresh")
        ResponseEntity<Void> refresh() {
            return ResponseEntity.noContent().build();
        }
    }
}
