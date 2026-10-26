package com.clydehomu.disputes.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidBodyReturnsFieldErrorsSortedByField() throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"name": " ", "quantity": 0}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid."))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[1].field").value("quantity"));
    }

    @Test
    void invalidRequestParameterReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/probe/page").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void constraintViolationReturnsValidationFailedWithBareFieldNames() throws Exception {
        mockMvc.perform(get("/probe/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[1].field").value("quantity"));
    }

    @Test
    void malformedJsonReturnsValidationFailedWithoutParserDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Jackson", "Unexpected character");
    }

    @Test
    void malformedPathVariableNamesTheParameter() throws Exception {
        mockMvc.perform(get("/probe/typed/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    @Test
    void aBodyPropertyOfTheWrongTypeNamesTheProperty() throws Exception {
        MvcResult result = mockMvc.perform(post("/probe/body-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("UUID", "Jackson");
    }

    @Test
    void aRuleCheckedAfterBindingIsReportedLikeBeanValidation() throws Exception {
        mockMvc.perform(get("/probe/validation-failed"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must not be more than the transaction amount (1250.00)"));
    }

    @Test
    void apiExceptionKeepsItsStatusCodeAndDetail() throws Exception {
        mockMvc.perform(get("/probe/api-exception"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.detail").value("The transaction is older than the dispute window."));
    }

    @Test
    void problemIsReturnedEvenWhenClientOnlyAcceptsHtml() throws Exception {
        mockMvc.perform(get("/probe/api-exception").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_ELIGIBLE"));
    }

    @Test
    void notFoundExceptionReturns404() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Dispute not found."));
    }

    @Test
    void unknownPathReturns404Problem() throws Exception {
        mockMvc.perform(get("/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."));
    }

    @Test
    void wrongMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/probe/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void accessDeniedFromMethodSecurityReturns403NotAServerError() throws Exception {
        mockMvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("You are not allowed to perform this action."));
    }

    @Test
    void authenticationFailureReturns401() throws Exception {
        mockMvc.perform(get("/probe/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
    }

    @Test
    void aLostVersionCheckReturns409DisputeModifiedWithoutLeakingTheEntity() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/stale"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"))
                .andExpect(jsonPath("$.detail").value("Someone else changed this dispute. Reload and try again."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Dispute", "com.clydehomu", "version");
    }

    @Test
    void aPlainOptimisticLockingFailureIsHandledTheSameWay() throws Exception {
        mockMvc.perform(get("/probe/stale-plain"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_MODIFIED"));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutLeakingDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("Something went wrong on our side. Please try again."))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("IllegalStateException", "jdbc", "postgresql", "admin", "at com.clydehomu", "trace");
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        private final Validator validator =
                Validation.buildDefaultValidatorFactory().getValidator();

        record Body(@NotBlank String name, @Min(1) int quantity) {}

        record IdBody(UUID id) {}

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {}

        @GetMapping("/page")
        void page(@RequestParam @Max(100) int size) {}

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new Body("", 0)));
        }

        @PostMapping("/body-uuid")
        void bodyUuid(@RequestBody IdBody body) {}

        @GetMapping("/validation-failed")
        void validationFailed() {
            throw new ValidationFailedException("amount", "must not be more than the transaction amount (1250.00)");
        }

        @GetMapping("/typed/{id}")
        void typed(@PathVariable UUID id) {}

        @GetMapping("/api-exception")
        void apiException() {
            throw new ApiException(
                    ErrorCode.TRANSACTION_NOT_ELIGIBLE, "The transaction is older than the dispute window.");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Dispute not found.");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("internal reason");
        }

        @GetMapping("/unauthenticated")
        void unauthenticated() {
            throw new BadCredentialsException("internal reason");
        }

        @GetMapping("/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException(ProbeController.class, UUID.randomUUID());
        }

        @GetMapping("/stale-plain")
        void stalePlain() {
            throw new OptimisticLockingFailureException("update count 0");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("could not connect to jdbc:postgresql://db:5432/disputes as admin");
        }
    }
}
