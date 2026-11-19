package io.github.coreplatform.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

public final class ServiceEnvFilePostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    if (!environment.getProperty("vishop.env-file.enabled", Boolean.class, true)) {
      return;
    }
    String service = environment.getProperty("vishop.env-file.service");
    if (service == null) {
      return;
    }
    if (!service.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
      throw new IllegalStateException("vishop.env-file.service must be a service directory name");
    }
    String prefix = service.replaceFirst("-service$", "").replace('-', '_');
    String explicitFile =
        environment.getProperty(prefix.toUpperCase(java.util.Locale.ROOT) + "_ENV_FILE");
    Path file = explicitFile == null ? profileFile(environment, service) : Path.of(explicitFile);
    if (file == null || (explicitFile == null && !Files.exists(file))) {
      return;
    }

    file = file.toAbsolutePath().normalize();
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException("Service env file is not a regular file: " + file);
    }
    Map<String, Object> entries = new LinkedHashMap<>();
    try {
      Dotenv.configure()
          .directory(file.getParent().toString())
          .filename(file.getFileName().toString())
          .load()
          .entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)
          .forEach(entry -> entries.put(entry.getKey(), unwrapSingleQuotes(entry.getValue())));
    } catch (DotenvException ignored) {
      throw new IllegalStateException("Unable to read service env file: " + file);
    }
    environment
        .getPropertySources()
        .addAfter(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(service + "-file-systemEnvironment", entries));
  }

  private String unwrapSingleQuotes(String value) {
    // dotenv-java strips double quotes and preserves single quotes used by Compose.
    if (value.length() >= 2
        && value.charAt(0) == '\''
        && value.charAt(value.length() - 1) == '\'') {
      return value.substring(1, value.length() - 1).replace("\\'", "'");
    }
    return value;
  }

  private Path profileFile(ConfigurableEnvironment environment, String serviceName) {
    String profile;
    if (environment.matchesProfiles("prod")) {
      profile = "prod";
    } else if (environment.matchesProfiles("dev")) {
      profile = "dev";
    } else {
      return null;
    }
    Path service = Path.of("services", serviceName);
    return (Files.isDirectory(service) ? service : Path.of("")).resolve(".env." + profile);
  }
}
