package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.api.IdentityApi.AuthorizationDeliveryPort;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Không biến lỗi hạ tầng thành 401: token hợp lệ có thể được thử lại khi DB/Redis phục hồi. */
@RestControllerAdvice(basePackages = "com.vishop.iam.modules.identity")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {
  // Conflict/constraint/concurrency dùng DatabaseExceptionHandler chung trong common-web.
  // Chỉ ánh xạ lỗi xác nhận trạng thái auth tại đây để client biết có thể retry.
  @ExceptionHandler({
    AuthorizationDeliveryPort.Unavailable.class,
    DataAccessResourceFailureException.class
  })
  public ResponseEntity<ProblemDetail> unavailable(RuntimeException failure) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Authentication state is temporarily unavailable"));
  }
}
