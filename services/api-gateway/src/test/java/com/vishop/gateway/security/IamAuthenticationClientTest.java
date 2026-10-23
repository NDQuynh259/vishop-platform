package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IamAuthenticationClientTest {
  @Test
  void usesPrivateIamEndpointAndDeserializesGlobalIdentity() {
    var request = new AtomicReference<ClientRequest>();
    String user = UUID.randomUUID().toString();
    var builder =
        WebClient.builder()
            .exchangeFunction(
                r -> {
                  request.set(r);
                  return Mono.just(
                      ClientResponse.create(HttpStatus.OK)
                          .header("Content-Type", "application/json")
                          .body(
                              "{\"userId\":\""
                                  + user
                                  + "\",\"tenantId\":null,\"systemRole\":\"USER\","
                                  + "\"roles\":[],\"permissions\":[],\"accessToken\":\"fresh-jwt\"}")
                          .build());
                });
    String secret = "test-internal-secret-with-at-least-thirty-two-bytes";
    var client = new IamAuthenticationClient(builder, "http://iam-service:8080", secret);
    StepVerifier.create(client.authenticate("opaque-cookie", false))
        .assertNext(
            identity -> {
              assertThat(identity.userId().toString()).isEqualTo(user);
              assertThat(identity.tenantId()).isNull();
              assertThat(identity.accessToken()).isEqualTo("fresh-jwt");
            })
        .verifyComplete();
    assertThat(request.get().url().toString())
        .isEqualTo("http://iam-service:8080/internal/auth/introspect");
    assertThat(request.get().headers().getFirst("X-Iam-Internal-Key")).isEqualTo(secret);
  }

  @Test
  void emptyOrRejectedResponsesAreNotAuthenticated() {
    var builder =
        WebClient.builder()
            .exchangeFunction(
                r -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build()));
    var client =
        new IamAuthenticationClient(
            builder,
            "http://iam-service:8080",
            "test-internal-secret-with-at-least-thirty-two-bytes");
    StepVerifier.create(client.authenticate("revoked", true))
        .expectError(WebClientResponseException.Unauthorized.class)
        .verify();
  }
}
