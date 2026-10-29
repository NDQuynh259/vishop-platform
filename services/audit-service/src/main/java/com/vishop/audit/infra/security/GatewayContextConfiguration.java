package com.vishop.audit.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GatewayContextConfiguration {
  @Bean
  GatewayContextCodec gatewayContextCodec(
      ObjectMapper json, @Value("${vishop.security.context.secret}") String secret) {
    return new GatewayContextCodec(json, secret, "audit-service");
  }
}
