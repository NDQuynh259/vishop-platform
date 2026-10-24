# Contributing to Core Platform

## Branch naming

Use `<type>/<ticket-optional>-<short-description>` with lowercase kebab-case descriptions.

Allowed branch types:

- `feature/` for new capabilities
- `bugfix/` for behavior corrections
- `hotfix/` for urgent production fixes
- `docs/` for documentation-only changes
- `refactor/` for structural changes without behavior changes
- `test/` for test-only changes
- `build/` for build and dependency configuration
- `ci/` for continuous integration changes
- `chore/` for maintenance

Examples: `feature/CORE-42-add-tracing`, `bugfix/fix-span-propagation`, `docs/update-module-guide`.

## Commit messages

Follow Conventional Commits:

```text
<type>(optional-scope): <imperative summary>
```

Use one of `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, or `chore`. Keep the subject concise, lowercase after the colon, and omit the trailing period. Add `!` after the type/scope for a breaking change and describe it in the commit body.

Examples: `feat(observability): propagate trace context through kafka`, `docs: define core module boundaries`, `refactor(domain): use neutral package namespace`.

## Pull request expectations

- Keep changes focused and explain user-visible or API compatibility impacts.
- Add or update tests for behavior changes.
- Run `./gradlew clean test` before requesting review.
- Do not include generated build outputs or secrets.
- Follow the [persistence standard](docs/core-architecture/persistence.md): Spring Data JPA/EntityManager for runtime access, `ddl-auto: validate`, and versioned Flyway SQL for schema changes. Do not add direct JDBC or Hibernate schema generation.
