package com.vishop.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler();
    }

    @Test
    @DisplayName("Should return 404 Problem Details when route is not found")
    void shouldReturn404OnNotFoundException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/unmapped/route")
                .header("X-Request-Id", "req-test-404")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        NotFoundException ex = new NotFoundException("Route not found");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Should return 503 Problem Details when target microservice connection is refused")
    void shouldReturn503OnConnectionRefused() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants")
                .header("X-Request-Id", "req-test-503")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ConnectException ex = new ConnectException("Connection refused: no further information");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Should map ResponseStatusException properly")
    void shouldMapResponseStatusException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/forbidden")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
