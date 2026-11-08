# Contributing to Core Platform

## List API contract

All list endpoints use `PaginationRequest` and `ListEnvelope` from
`libs/common-web/src/main/java/io/github/coreplatform/common/web/pagination`.
Bind the request as an unannotated controller argument (optionally `@ParameterObject`),
so the shared resolver handles `page`, `limit`, `cursor`, `pageMode` and validation.
Return `object`, `url`, `data` and `pageInfo`; do not return a bare collection.
Offset pages start at 1, default to 20 items and permit limits from 1 to 100.
All list APIs support both modes. `pageMode=offset` (the default) selects page/limit;
`pageMode=cursor` selects keyset pagination, including the first page without a token.
Use `PaginationRequest.isCursor()` or `toPagination()` to select the mode. A cursor
token alone never switches modes; clients send pageMode=cursor on every cursor request.

Database-backed lists paginate at the read adapter, with matching access filters and
deterministic ordering. Offset mode also counts matching rows; cursor mode fetches
limit+1 rows and returns nextCursor without a count. `JpaListPagination` implements
both modes with scoped sort-key cursors. Only bounded code catalogs may use
`ListEnvelope.from(url, catalog, pagination, stableKey)` to page an existing list.

The CMS user/tenant lists now use `page`/`limit` instead of raw `offset`/`limit`.
Clients of formerly bare-array endpoints must read items from `data` and pagination
metadata from `pageInfo`.

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
- Keep service-specific business rules, architecture and database designs in `services/<service>/docs/` and update them alongside related changes. See the [service documentation convention](docs/services/README.md).
- Follow the [persistence standard](docs/core-architecture/persistence.md): Spring Data JPA/EntityManager for runtime access, `ddl-auto: validate`, and versioned Flyway SQL for schema changes. Do not add direct JDBC or Hibernate schema generation.
