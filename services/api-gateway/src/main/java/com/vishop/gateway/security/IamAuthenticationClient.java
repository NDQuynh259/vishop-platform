package com.vishop.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class IamAuthenticationClient {
  public record Identity(
      UUID userId,
      UUID tenantId,
      String systemRole,
      List<String> roles,
      Set<String> permissions,
      String accessToken) {}

  private final WebClient client;

  public IamAuthenticationClient(
      WebClient.Builder builder,
      @Value("${vishop.iam.url:http://localhost:8080}") String url,
      @Value("${vishop.iam.internal-secret:${IAM_INTERNAL_SECRET:}}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("IAM_INTERNAL_SECRET needs at least 32 bytes");
    client = builder.baseUrl(url).defaultHeader("X-Iam-Internal-Key", secret).build();
  }

  public Mono<Identity> authenticate(String credential, boolean bearer) {
    return client
        .post()
        .uri("/internal/auth/introspect")
        .bodyValue(Map.of("credential", credential, "type", bearer ? "BEARER" : "SESSION"))
        .retrieve()
        .bodyToMono(Identity.class)
        .timeout(Duration.ofSeconds(3))
        .switchIfEmpty(Mono.error(new IllegalStateException("Empty IAM response")))
        .map(
            identity -> {
              if (identity.userId() == null
                  || identity.accessToken() == null
                  || identity.accessToken().isBlank()
                  || identity.permissions() == null
                  || identity.roles() == null)
                throw new IllegalStateException("Incomplete IAM response");
              return identity;
            });
  }
}
