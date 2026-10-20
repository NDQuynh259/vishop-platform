# Reusable Core Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current branch into a reusable Java 21/Spring Boot core while preserving all existing docs, and establish contribution and branch naming rules.

**Architecture:** Keep the Gradle multi-module `platform-common` libraries, exclude ViShop-specific runnable services and infrastructure from the core build while preserving the existing service/infrastructure files pending a separate cleanup, and replace project/package identity with `core-platform` / `io.github.coreplatform`. Put reusable architecture and contribution rules under `docs/core-architecture/`; do not modify or delete existing documents under `docs/`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Gradle Kotlin DSL, JUnit 5.

---

### Task 1: Make root Gradle identity neutral

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] Set root project name to `core-platform` and group to `io.github.coreplatform`.
- [ ] Keep Java toolchain, Spring dependency management, and common module includes.
- [ ] Keep only `platform-common` modules included in the core build; service directories remain in place but are excluded.

### Task 2: Add package migration regression check

**Files:**
- Create: `scripts/verify-core-package.ps1`

- [ ] Add a PowerShell check that scans tracked Java source under `platform-common` and exits non-zero if `com.vishop` remains; run it before migrating packages and confirm expected failure.

### Task 3: Exclude ViShop-specific apps and infrastructure from the core

**Files:**
- Keep unchanged: `services/`
- Keep unchanged: `infra/`
- Modify: `settings.gradle.kts`
- Modify: `.gitignore`

- [ ] Preserve service source/configuration/build output and infrastructure files because they may include local uncommitted work.
- [ ] Remove service entries from Gradle settings so service code is not part of the core build.
- [ ] Ignore Gradle/build outputs so generated artifacts are not newly committed.
- [ ] Preserve every pre-existing file under `docs/`; do not move or rewrite existing docs.

### Task 4: Migrate shared Java packages

**Files:**
- Modify: Java files under `platform-common/**/src/{main,test}/java/com/vishop/**`
- Modify: module Gradle configuration using `com.vishop`

- [ ] Move all common module Java package paths and declarations from `com.vishop.common` to `io.github.coreplatform.common`.
- [ ] Update imports and any test references.
- [ ] Run the package regression check and confirm no legacy package remains in shared source.

### Task 5: Document core architecture and contribution rules

**Files:**
- Create: `docs/core-architecture/README.md`
- Create: `docs/core-architecture/module-boundaries.md`
- Create: `CONTRIBUTING.md`

- [ ] Document reusable modules, dependencies, and the distinction between framework core and application-specific services.
- [ ] Define Conventional Commit types (`feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`) and subject format.
- [ ] Define branch pattern `<type>/<ticket-optional>-<slug>` with allowed types and examples.
- [ ] State that existing ViShop documentation is retained as historical/source-project documentation and core architecture lives under `docs/core-architecture/`.

### Task 6: Update root README for the core

**Files:**
- Modify: `README.md`

- [ ] Replace ViShop-specific overview and runnable service instructions with core purpose, module layout, build/test commands, and links to core docs/contribution rules.
- [ ] Preserve the full `docs/` tree and existing document contents.

### Task 7: Verify core build and repository scope

**Files:**
- Verify: Gradle project and source tree

- [ ] Run `./gradlew clean test` (Windows: `.\gradlew.bat clean test`).
- [ ] Run `scripts/verify-core-package.ps1`.
- [ ] Inspect `git status --short` and confirm no existing docs were deleted and no generated build artifacts are staged.
