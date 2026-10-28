package io.github.coreplatform.common.web.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.DomainException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

  // #region setUp
  @BeforeEach
  void setUp() {
    TestController controller = new TestController();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new SecurityExceptionHandler(),
                new DatabaseExceptionHandler(),
                new GlobalExceptionHandler())
            .build();
  }

  // #endregion

  // #region shouldHandleValidationErrorsWith422AndErrorList
  @Test
  void shouldHandleValidationErrorsWith422AndErrorList() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .header("X-Request-Id", "req-123"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string("Content-Type", containsString("application/problem+json")))
        .andExpect(jsonPath("$.type").value("/errors/validation-failed"))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.requestId").value("req-123"))
        .andExpect(jsonPath("$.instance").value("/test/validation"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
  }

  // #endregion

  // #region shouldHandleEntityNotFoundExceptionWith404
  @Test
  void shouldHandleEntityNotFoundExceptionWith404() throws Exception {
    mockMvc
        .perform(get("/test/not-found").header("X-Request-Id", "req-404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errors/entity-not-found"))
        .andExpect(jsonPath("$.title").value("Entity not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("entity_not_found"))
        .andExpect(jsonPath("$.detail").value("Tenant with id 'ten-999' was not found"))
        .andExpect(jsonPath("$.entityType").value("Tenant"))
        .andExpect(jsonPath("$.entityId").value("ten-999"))
        .andExpect(jsonPath("$.requestId").value("req-404"));
  }

  // #endregion

  // #region shouldHandleConflictExceptionWith409
  @Test
  void shouldHandleConflictExceptionWith409() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errors/conflict"))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("tenant_code_already_exists"))
        .andExpect(jsonPath("$.detail").value("Tenant code already exists"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  // #endregion

  // #region shouldHandleBusinessRuleViolationWith422
  @Test
  void shouldHandleBusinessRuleViolationWith422() throws Exception {
    mockMvc
        .perform(get("/test/business-rule"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("/errors/business-rule-violation"))
        .andExpect(jsonPath("$.title").value("Business rule violation"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("tenant_archived"))
        .andExpect(jsonPath("$.detail").value("Archived tenant cannot be modified"));
  }

  // #endregion

  // #region shouldHandleUnauthorizedExceptionWith401
  @Test
  void shouldHandleUnauthorizedExceptionWith401() throws Exception {
    mockMvc
        .perform(get("/test/unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errors/unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }

  // #endregion

  // #region shouldHandleForbiddenExceptionWith403
  @Test
  void shouldHandleForbiddenExceptionWith403() throws Exception {
    mockMvc
        .perform(get("/test/forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errors/forbidden"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("forbidden"));
  }

  // #endregion

  // #region shouldHandleBadRequestExceptionWith400
  @Test
  void shouldHandleBadRequestExceptionWith400() throws Exception {
    mockMvc
        .perform(get("/test/bad-request"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errors/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("bad_request"));
  }

  // #endregion

  // #region shouldHandleDomainExceptionWith422
  @Test
  void shouldHandleDomainExceptionWith422() throws Exception {
    mockMvc
        .perform(get("/test/domain"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("/errors/domain-violation"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("custom_domain_error"));
  }

  // #endregion

  // #region shouldHandleIllegalArgumentExceptionWith400
  @Test
  void shouldHandleIllegalArgumentExceptionWith400() throws Exception {
    mockMvc
        .perform(get("/test/illegal-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errors/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("bad_request"))
        .andExpect(jsonPath("$.detail").value("Illegal argument supplied"));
  }

  // #endregion

  // #region shouldHandleMalformedJsonWith400
  @Test
  void shouldHandleMalformedJsonWith400() throws Exception {
    mockMvc
        .perform(
            post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errors/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("malformed_request"));
  }

  // #endregion

  // #region shouldHandleTypeMismatchWith400
  @Test
  void shouldHandleTypeMismatchWith400() throws Exception {
    mockMvc
        .perform(get("/test/uuid/invalid-uuid-format"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errors/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("type_mismatch"));
  }

  // #endregion

  // #region shouldHandleMissingParamWith400
  @Test
  void shouldHandleMissingParamWith400() throws Exception {
    mockMvc
        .perform(get("/test/required-param"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errors/bad-request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("missing_parameter"));
  }

  // #endregion

  // #region shouldHandleAccessDeniedWith403
  @Test
  void shouldHandleAccessDeniedWith403() throws Exception {
    mockMvc
        .perform(get("/test/access-denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errors/forbidden"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("access_denied"));
  }

  // #endregion

  // #region shouldHandleDataIntegrityViolationWith409
  @Test
  void shouldHandleDataIntegrityViolationWith409() throws Exception {
    mockMvc
        .perform(get("/test/data-integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errors/conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("database_conflict"));
  }

  // #endregion

  // #region shouldHandleOptimisticLockCollisionWith409
  @Test
  void shouldHandleOptimisticLockCollisionWith409() throws Exception {
    mockMvc
        .perform(get("/test/optimistic-lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errors/conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("optimistic_lock_conflict"));
  }

  // #endregion

  @ParameterizedTest
  @ValueSource(strings = {"general", "lock"})
  void shouldHandleConcurrencyFailureWith409WithoutLeakingDetails(String kind) throws Exception {
    mockMvc
        .perform(get("/test/concurrency/" + kind).header("X-Request-Id", "req-concurrency"))
        .andExpect(status().isConflict())
        .andExpect(header().string("Content-Type", containsString("application/problem+json")))
        .andExpect(jsonPath("$.type").value("/errors/conflict"))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("concurrency_conflict"))
        .andExpect(
            jsonPath("$.detail")
                .value("The operation conflicted with another transaction. Please retry."))
        .andExpect(jsonPath("$.instance").value("/test/concurrency/" + kind))
        .andExpect(jsonPath("$.requestId").value("req-concurrency"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  // #region shouldHandleGeneralExceptionWith500WithoutLeakingDetails
  @Test
  void shouldHandleGeneralExceptionWith500WithoutLeakingDetails() throws Exception {
    mockMvc
        .perform(get("/test/unhandled"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("/errors/internal-server-error"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("internal_server_error"))
        .andExpect(
            jsonPath("$.detail")
                .value("An unexpected error occurred. Please contact support with the request ID."))
        .andExpect(jsonPath("$.requestId", notNullValue()));
  }

  // #endregion

  // ==========================================
  // Dummy Controller for Testing
  // ==========================================

  @RestController
  @RequestMapping("/test")
  static class TestController {

    // #region validationEndpoint
    @PostMapping("/validation")
    public String validationEndpoint(@Valid @RequestBody TestRequest request) {
      return "ok";
    }

    // #endregion

    // #region notFoundEndpoint
    @GetMapping("/not-found")
    public String notFoundEndpoint() {
      throw new EntityNotFoundException("Tenant", "ten-999");
    }

    // #endregion

    // #region conflictEndpoint
    @GetMapping("/conflict")
    public String conflictEndpoint() {
      throw new ConflictException("tenant_code_already_exists", "Tenant code already exists");
    }

    // #endregion

    // #region businessRuleEndpoint
    @GetMapping("/business-rule")
    public String businessRuleEndpoint() {
      throw new BusinessRuleViolationException(
          "tenant_archived", "Archived tenant cannot be modified");
    }

    // #endregion

    // #region unauthorizedEndpoint
    @GetMapping("/unauthorized")
    public String unauthorizedEndpoint() {
      throw new UnauthorizedException();
    }

    // #endregion

    // #region forbiddenEndpoint
    @GetMapping("/forbidden")
    public String forbiddenEndpoint() {
      throw new ForbiddenException();
    }

    // #endregion

    // #region badRequestEndpoint
    @GetMapping("/bad-request")
    public String badRequestEndpoint() {
      throw new BadRequestException("Invalid filter parameter");
    }

    // #endregion

    // #region domainEndpoint
    @GetMapping("/domain")
    public String domainEndpoint() {
      throw new DomainException("custom_domain_error", "Domain constraint failed");
    }

    // #endregion

    // #region illegalArgumentEndpoint
    @GetMapping("/illegal-argument")
    public String illegalArgumentEndpoint() {
      throw new IllegalArgumentException("Illegal argument supplied");
    }

    // #endregion

    // #region uuidEndpoint
    @GetMapping("/uuid/{id}")
    public String uuidEndpoint(@PathVariable("id") UUID id) {
      return id.toString();
    }

    // #endregion

    // #region requiredParamEndpoint
    @GetMapping("/required-param")
    public String requiredParamEndpoint(@RequestParam("filter") String filter) {
      return filter;
    }

    // #endregion

    // #region accessDeniedEndpoint
    @GetMapping("/access-denied")
    public String accessDeniedEndpoint() {
      throw new AccessDeniedException("Access is denied to shop");
    }

    // #endregion

    // #region dataIntegrityEndpoint
    @GetMapping("/data-integrity")
    public String dataIntegrityEndpoint() {
      throw new DataIntegrityViolationException(
          "duplicate key value violates unique constraint 'tenants_code_key'");
    }

    // #endregion

    // #region optimisticLockEndpoint
    @GetMapping("/optimistic-lock")
    public String optimisticLockEndpoint() {
      throw new OptimisticLockingFailureException(
          "Row was updated or deleted by another transaction");
    }

    // #endregion

    @GetMapping("/concurrency/{kind}")
    public String concurrencyEndpoint(@PathVariable("kind") String kind) {
      if (kind.equals("lock")) {
        throw new CannotAcquireLockException("Sensitive SQL lock details");
      }
      throw new ConcurrencyFailureException("Sensitive SQL transaction details");
    }

    // #region unhandledEndpoint
    @GetMapping("/unhandled")
    public String unhandledEndpoint() {
      throw new RuntimeException("Sensitive database connection failure at 192.168.1.10:5432");
    }
    // #endregion
  }

  record TestRequest(@NotBlank String name) {}
}
