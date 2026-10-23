package com.vishop.tenant.infrastructure.config;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration to apply global path prefixes to REST controllers.
 * Automatically prefixes all @RestController endpoints with the configured api-prefix (default '/api/v1'),
 * eliminating the need to repeat '/api/v1' in every controller @RequestMapping.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @NonNull
    private final String apiPrefix;

    public WebMvcConfig(@Value("${vishop.api-prefix:/api/v1}") @NonNull String apiPrefix) {
        this.apiPrefix = Objects.requireNonNull(apiPrefix, "apiPrefix must not be null");
    }

    @Override
    public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
        if (StringUtils.hasText(apiPrefix)) {
            configurer.addPathPrefix(apiPrefix, HandlerTypePredicate.forAnnotation(RestController.class));
        }
    }
}
