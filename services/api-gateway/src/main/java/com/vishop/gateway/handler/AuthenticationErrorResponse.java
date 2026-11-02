package com.vishop.gateway.handler;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ghi lỗi từ chối xác thực; lỗi routing/service phía sau vẫn do
 * GatewayExceptionHandler xử lý.
 */
@Component
public class AuthenticationErrorResponse {
  public Mono<Void> write(ServerWebExchange exchange, int status) {
    String code = switch (status) {
      case 401 -> "unauthorized";
      case 403 -> "forbidden";
      default -> "authentication-unavailable";
    };
    return write(exchange, status, code);
  }

  public Mono<Void> write(ServerWebExchange exchange, int status, String code) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String title = switch (status) {
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      default -> "Authentication unavailable";
    };
    byte[] json = ("{\"type\":\"/errors/"
        + code
        + "\",\"title\":\""
        + title
        + "\",\"status\":"
        + status
        + "}")
        .getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }
}
