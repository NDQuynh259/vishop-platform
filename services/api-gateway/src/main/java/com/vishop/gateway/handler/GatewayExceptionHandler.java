package com.vishop.gateway.handler;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Handles exceptions occurring at the API Gateway level (such as downstream service down, route not
 * found), formatting them consistently into RFC 9457 Problem Details JSON.
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

  // #region handle
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse response = exchange.getResponse();

    if (response.isCommitted()) {
      return Mono.error(ex);
    }

    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String title = "Internal Gateway Error";
    String code = "gateway_error";
    String detail = "An unexpected error occurred at the API Gateway.";

    if (ex instanceof NotFoundException) {
      status = HttpStatus.NOT_FOUND;
      title = "Route Not Found";
      code = "route_not_found";
      detail = "The requested path could not be routed to any downstream service.";
    } else if (ex instanceof ResponseStatusException rse) {
      status = HttpStatus.resolve(rse.getStatusCode().value());
      if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
      title = status.getReasonPhrase();
      detail = rse.getReason() != null ? rse.getReason() : detail;
    } else if (ex.getMessage() != null && ex.getMessage().contains("Connection refused")) {
      status = HttpStatus.SERVICE_UNAVAILABLE;
      title = "Service Unavailable";
      code = "service_unavailable";
      detail = "The target microservice is currently unreachable. Please try again shortly.";
    }

    log.error(
        "[Gateway Error] {} {}: {} (Status {})",
        exchange.getRequest().getMethod(),
        exchange.getRequest().getURI().getPath(),
        ex.getMessage(),
        status);

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if (requestId == null) requestId = "none";

    String json =
        String.format(
            """
            {
              "type": "/errors/%s",
              "title": "%s",
              "status": %d,
              "detail": "%s",
              "code": "%s",
              "timestamp": "%s",
              "requestId": "%s"
            }
            """,
            code.replace('_', '-'),
            title,
            status.value(),
            detail,
            code,
            Instant.now().toString(),
            requestId);

    DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
  // #endregion
}
