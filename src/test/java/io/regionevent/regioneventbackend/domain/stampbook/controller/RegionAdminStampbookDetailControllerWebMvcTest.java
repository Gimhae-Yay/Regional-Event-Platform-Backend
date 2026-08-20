package io.regionevent.regioneventbackend.domain.stampbook.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.stampbook.service.GetRegionAdminStampbookDetailUseCase;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RegionAdminStampbookDetailController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RegionAdminStampbookDetailControllerWebMvcTest {

    private static final long REGION_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRegionAdminStampbookDetailUseCase getRegionAdminStampbookDetailUseCase;

    @Test
    void detail_범위를_벗어난식별자면입력오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/stampbooks/9223372036854775808"
            )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getRegionAdminStampbookDetailUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, REGION_ADMIN_USER_ID)
        );
    }
}
