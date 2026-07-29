package io.regionevent.regioneventbackend.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ExceptionTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void handleBusinessException_returnsDefinedErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void handleMethodArgumentNotValidException_returnsInvalidInputResponse() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handleHttpMessageNotReadableException_returnsInvalidJsonResponse() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_JSON"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handleMethodArgumentTypeMismatchException_returnsInvalidTypeResponse() throws Exception {
        mockMvc.perform(get("/test/type/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handleUnexpectedException_hidesInternalExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.statusCode").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("internal failure")));
    }

    @RestController
    @RequestMapping("/test")
    private static class ExceptionTestController {

        @GetMapping("/business")
        String business() {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        @PostMapping("/validation")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return request.title();
        }

        @GetMapping("/type/{contentId}")
        String findById(@PathVariable Long contentId) {
            return contentId.toString();
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("internal failure");
        }
    }

    private record ValidationRequest(@NotBlank String title) {
    }
}
