# Observability Module Organization Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clarify the internal structure of `common-observability` without changing runtime behavior or public package names.

**Architecture:** Keep one Gradle module and preserve `CorrelationMdcFilter` and `TracingConstants` under `io.github.coreplatform.common.observability`. Move auto-configuration implementation into an internal configuration package, separate metrics configuration, retain Logback XML as module resources, and update Spring Boot auto-configuration registration to the new configuration class.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Micrometer, JUnit 5, Gradle Kotlin DSL.

---

### Task 1: Add auto-configuration registration test

**Files:**
- Create: `platform-common/common-observability/src/test/java/io/github/coreplatform/common/observability/ObservabilityAutoConfigurationRegistrationTest.java`
- Existing registration: `platform-common/common-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] Test that the auto-configuration imports file names an existing `@AutoConfiguration` class.
- [ ] Run `./gradlew :platform-common:common-observability:test --tests '*ObservabilityAutoConfigurationRegistrationTest'` and confirm it fails before changes.

### Task 2: Separate configuration classes

**Files:**
- Move: `ObservabilityAutoConfiguration.java` to `.../observability/autoconfigure/ObservabilityAutoConfiguration.java`
- Create: `.../observability/autoconfigure/MetricsAutoConfiguration.java`
- Modify: `platform-common/common-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Keep unchanged: `CorrelationMdcFilter.java`, `TracingConstants.java`, `logback-spring.xml`

- [ ] Keep the filter bean registration conditional on servlet applications and servlet classes.
- [ ] Move common metric tags into `MetricsAutoConfiguration` and retain the existing application/env tag behavior.
- [ ] Register both auto-configuration classes in `AutoConfiguration.imports`.
- [ ] Run the registration test and the existing `CorrelationMdcFilterTest`.

### Task 3: Verify module behavior and structure

**Files:**
- Verify: `platform-common/common-observability/**`

- [ ] Run `./gradlew :platform-common:common-observability:cleanTest :platform-common:common-observability:test`.
- [ ] Run `./gradlew test`.
- [ ] Confirm public class package names remain `io.github.coreplatform.common.observability` and no changes occurred outside this module and this plan.
