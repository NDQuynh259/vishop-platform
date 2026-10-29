package com.vishop.subscription.infra.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class SubscriptionConfiguration {
  @Bean
  Clock billingClock() {
    return Clock.systemUTC();
  }
}
