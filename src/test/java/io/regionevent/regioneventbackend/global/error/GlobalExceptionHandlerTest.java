package io.regionevent.regioneventbackend.global.error;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

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
import org.springframework.web.bind.annotation.RequestParam;
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
    void handleBusinessException_contentStateConflict_serializesDefinedErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business/content-state-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value(409))
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"))
            .andExpect(jsonPath("$.message").value("콘텐츠 상태가 요청을 처리할 수 없습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.*").value(hasSize(4)));
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
    void handleHttpMessageNotReadableException_whenBodyIsMissing_returnsInvalidJsonResponse() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_JSON"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handleHttpMessageNotReadableException_whenJsonFieldTypeMismatched_returnsInvalidTypeResponse() throws Exception {
        mockMvc.perform(post("/test/typed-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "count": "not-a-number"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"))
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
    void handleHandlerMethodValidationException_returnsInvalidInputResponse() throws Exception {
        mockMvc.perform(get("/test/parameter?contentId=0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handleNoHandlerFoundException_returnsNotFoundResponse() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
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

        @GetMapping("/business/content-state-conflict")
        String contentStateConflict() {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        @PostMapping("/validation")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return request.title();
        }

        @GetMapping("/type/{contentId}")
        String findById(@PathVariable Long contentId) {
            return contentId.toString();
        }

        @GetMapping("/parameter")
        String findByRequestParam(@RequestParam @Positive Long contentId) {
            return contentId.toString();
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("internal failure");
        }

        @PostMapping("/typed-validation")
        Long validateTyped(@Valid @RequestBody TypedValidationRequest request) {
            return request.count();
        }
    }

    private record ValidationRequest(@NotBlank String title) {
    }

    private record TypedValidationRequest(Long count) {
    }
}
