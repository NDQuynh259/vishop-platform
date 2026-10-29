package com.vishop.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayContextSigners {
  private final Map<String, GatewayContextCodec> codecs;

  public GatewayContextSigners(
      ObjectMapper json,
      @Value("${vishop.security.context.iam-secret}") String iam,
      @Value("${vishop.security.context.audit-secret}") String audit,
      @Value("${vishop.security.context.subscription-secret}") String subscription) {
    codecs =
        Map.of(
            "iam-service",
            new GatewayContextCodec(json, iam, "iam-service"),
            "audit-service",
            new GatewayContextCodec(json, audit, "audit-service"),
            "subscription-service",
            new GatewayContextCodec(json, subscription, "subscription-service"));
  }

  public GatewayContextCodec forService(String service) {
    var codec = codecs.get(service);
    if (codec == null)
      throw new IllegalArgumentException("No trusted context audience for route " + service);
    return codec;
  }
}
