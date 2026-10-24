package io.github.coreplatform.common.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.coreplatform.common.domain.BusinessRule;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

  // #region testDomainException
  @Test
  void testDomainException() {
    DomainException ex1 = new DomainException("Something failed");
    assertEquals("domain_error", ex1.getCode());
    assertEquals("Something failed", ex1.getMessage());

    DomainException ex2 = new DomainException("custom_code", "Custom message");
    assertEquals("custom_code", ex2.getCode());
    assertEquals("Custom message", ex2.getMessage());
  }

  // #endregion

  // #region testEntityNotFoundException
  @Test
  void testEntityNotFoundException() {
    EntityNotFoundException ex1 = new EntityNotFoundException("Tenant", "123");
    assertEquals("entity_not_found", ex1.getCode());
    assertEquals("Tenant with id '123' was not found", ex1.getMessage());
    assertEquals("Tenant", ex1.getEntityType());
    assertEquals("123", ex1.getEntityId());

    EntityNotFoundException ex2 = new EntityNotFoundException("Custom not found");
    assertEquals("entity_not_found", ex2.getCode());
    assertEquals("Custom not found", ex2.getMessage());
    assertNull(ex2.getEntityType());
    assertNull(ex2.getEntityId());
  }

  // #endregion

  // #region testConflictException
  @Test
  void testConflictException() {
    ConflictException ex1 = new ConflictException("Resource conflict");
    assertEquals("conflict", ex1.getCode());
    assertEquals("Resource conflict", ex1.getMessage());

    ConflictException ex2 =
        new ConflictException("tenant_code_exists", "Tenant code already exists");
    assertEquals("tenant_code_exists", ex2.getCode());
    assertEquals("Tenant code already exists", ex2.getMessage());
  }

  // #endregion

  // #region testBusinessRuleViolationException
  @Test
  void testBusinessRuleViolationException() {
    BusinessRuleViolationException ex1 = new BusinessRuleViolationException("Rule broken");
    assertEquals("business_rule_violation", ex1.getCode());
    assertEquals("Rule broken", ex1.getMessage());

    BusinessRule customRule =
        new BusinessRule() {
          @Override
          public boolean isBroken() {
            return true;
          }

          @Override
          public String getMessage() {
            return "Tenant must be active to create shop";
          }

          @Override
          public String getCode() {
            return "tenant_not_active";
          }
        };

    BusinessRuleViolationException ex2 = new BusinessRuleViolationException(customRule);
    assertEquals("tenant_not_active", ex2.getCode());
    assertEquals("Tenant must be active to create shop", ex2.getMessage());
  }

  // #endregion

  // #region testUnauthorizedException
  @Test
  void testUnauthorizedException() {
    UnauthorizedException ex1 = new UnauthorizedException();
    assertEquals("unauthorized", ex1.getCode());
    assertEquals("Authentication is required to access this resource", ex1.getMessage());

    UnauthorizedException ex2 = new UnauthorizedException("Session has expired");
    assertEquals("unauthorized", ex2.getCode());
    assertEquals("Session has expired", ex2.getMessage());
  }

  // #endregion

  // #region testForbiddenException
  @Test
  void testForbiddenException() {
    ForbiddenException ex1 = new ForbiddenException();
    assertEquals("forbidden", ex1.getCode());
    assertEquals("Access to this resource is denied", ex1.getMessage());

    ForbiddenException ex2 = new ForbiddenException("Cannot access shop in other tenant");
    assertEquals("forbidden", ex2.getCode());
    assertEquals("Cannot access shop in other tenant", ex2.getMessage());
  }

  // #endregion

  // #region testBadRequestException
  @Test
  void testBadRequestException() {
    BadRequestException ex1 = new BadRequestException("Invalid filter range");
    assertEquals("bad_request", ex1.getCode());
    assertEquals("Invalid filter range", ex1.getMessage());

    BadRequestException ex2 =
        new BadRequestException("invalid_pagination", "Page size exceeds limit");
    assertEquals("invalid_pagination", ex2.getCode());
    assertEquals("Page size exceeds limit", ex2.getMessage());
  }
  // #endregion
}
