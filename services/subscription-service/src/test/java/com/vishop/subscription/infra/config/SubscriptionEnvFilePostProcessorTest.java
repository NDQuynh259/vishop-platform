package com.vishop.subscription.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

class SubscriptionEnvFilePostProcessorTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"dev", "prod"})
  void loadsDotenvValuesAndRetainsProfileConfigurationAndCommandLinePrecedence(String profile)
      throws Exception {
    Path file = directory.resolve(".env." + profile);
    Files.writeString(
        file,
        """
        SUBSCRIPTION_DATASOURCE_URL=jdbc:postgresql://db.example.test:5432/subscriptions
        SUBSCRIPTION_DATASOURCE_USERNAME=subscription_app
        SUBSCRIPTION_DATASOURCE_PASSWORD='fixture # value $1'
        SEPAY_ACCOUNT_NUMBER=0012345678
        SUBSCRIPTION_PORT=8183
        CUSTOM_SETTING=from-file
        """);
    var app = new SpringApplication(Probe.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    try (var context =
        app.run(
            "--SUBSCRIPTION_ENV_FILE=" + file,
            "--spring.profiles.active=" + profile,
            "--server.port=8283")) {
      var env = context.getEnvironment();
      assertThat(env.getProperty("spring.datasource.url"))
          .isEqualTo("jdbc:postgresql://db.example.test:5432/subscriptions");
      assertThat(env.getProperty("spring.datasource.username")).isEqualTo("subscription_app");
      assertThat(env.getProperty("spring.datasource.password")).isEqualTo("fixture # value $1");
      assertThat(env.getProperty("vishop.subscription.sepay.account-number"))
          .isEqualTo("0012345678");
      assertThat(env.getProperty("custom.setting")).isEqualTo("from-file");
      assertThat(env.getProperty("server.port")).isEqualTo("8283");
      assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
      assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Probe {}
}
