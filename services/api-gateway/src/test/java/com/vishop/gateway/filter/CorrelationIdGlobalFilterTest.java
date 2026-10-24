package com.vishop.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdGlobalFilterTest {

  private CorrelationIdGlobalFilter filter;
  private GatewayFilterChain chain;

  @BeforeEach
  void setUp() {
    filter = new CorrelationIdGlobalFilter();
    chain = mock(GatewayFilterChain.class);
    when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
  }

  @Test
  @DisplayName("Should generate new correlation and request IDs when headers are missing")
  void shouldGenerateIdsWhenMissing() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());

    ServerWebExchange captured = captor.getValue();
    String reqCorrelationId =
        captured
            .getRequest()
            .getHeaders()
            .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
    String reqRequestId =
        captured.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);

    assertThat(reqCorrelationId).isNotNull().isNotBlank();
    assertThat(reqRequestId).isNotNull().isNotBlank();

    // Response headers should also contain the generated IDs
    String resCorrelationId =
        exchange
            .getResponse()
            .getHeaders()
            .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
    String resRequestId =
        exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER);

    assertThat(resCorrelationId).isEqualTo(reqCorrelationId);
    assertThat(resRequestId).isEqualTo(reqRequestId);
  }

  @Test
  @DisplayName("Should preserve existing correlation and request IDs when supplied by client")
  void shouldPreserveExistingIds() {
    String existingCorr = "corr-custom-123";
    String existingReq = "req-custom-456";

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/v1/tenants")
            .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, existingCorr)
            .header(CorrelationIdGlobalFilter.REQUEST_ID_HEADER, existingReq)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());

    ServerWebExchange captured = captor.getValue();
    assertThat(
            captured
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
        .isEqualTo(existingCorr);
    assertThat(
            captured
                .getRequest()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER))
        .isEqualTo(existingReq);

    assertThat(
            exchange
                .getResponse()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
        .isEqualTo(existingCorr);
    assertThat(
            exchange
                .getResponse()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.REQUEST_ID_HEADER))
        .isEqualTo(existingReq);
  }
}
