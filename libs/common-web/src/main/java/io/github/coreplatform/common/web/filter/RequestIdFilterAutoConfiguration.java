package io.github.coreplatform.common.web.filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Auto-configuration for request ID tracking filter. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestIdFilterAutoConfiguration {

  // #region requestIdFilter
  @Bean
  @ConditionalOnMissingBean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }

  // #endregion

  // #region requestIdFilterRegistration
  @Bean
  @ConditionalOnMissingBean(name = "requestIdFilterRegistration")
  public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
      RequestIdFilter filter) {
    FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
  // #endregion
}
