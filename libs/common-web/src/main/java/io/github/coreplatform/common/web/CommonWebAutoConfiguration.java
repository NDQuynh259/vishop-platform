package io.github.coreplatform.common.web;

import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import io.github.coreplatform.common.web.exception.ExceptionHandlingAutoConfiguration;
import io.github.coreplatform.common.web.filter.RequestIdFilterAutoConfiguration;
import io.github.coreplatform.common.web.openapi.OpenApiAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Umbrella AutoConfiguration for ViShop Common Web module.
 *
 * <p>Aggregates feature-based auto-configurations:
 *
 * <ul>
 *   <li>{@link ExceptionHandlingAutoConfiguration}
 *   <li>{@link RequestIdFilterAutoConfiguration}
 *   <li>{@link CqrsAutoConfiguration}
 *   <li>{@link OpenApiAutoConfiguration}
 * </ul>
 */
@AutoConfiguration
@Import({
  ExceptionHandlingAutoConfiguration.class,
  RequestIdFilterAutoConfiguration.class,
  CqrsAutoConfiguration.class,
  OpenApiAutoConfiguration.class
})
public class CommonWebAutoConfiguration {}
