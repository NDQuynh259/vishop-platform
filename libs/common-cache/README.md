# common-cache

General-purpose Redis cache for services. Business code decides what to cache,
when to read or invalidate it, and how long each entry lives.

## API

`CacheService` exposes:

- `get(key, Dto.class)` returns `Optional<Dto>`; an absent or expired key is empty.
- `put(key, value, ttl)` stores a non-null string directly, or an object as JSON.
- `evict(key)` deletes an entry; returns whether it existed.
- `exists(key)` checks whether an entry exists.
- `expire(key, ttl)` changes expiry; returns false if the entry is absent.

`Duration.ZERO` means persistent. Positive TTLs must be at least one millisecond.
A write replaces the old value and its TTL. Serialization and Redis errors
propagate; callers decide whether to retry, fall back, or fail the request.
Only a cache miss returns `Optional.empty()`.

The API does not automatically query databases, cache method results or invalidate
entries. No business use case is cached just by adding this dependency.

## Wiring

`CacheAutoConfiguration` registers `CacheProperties` and `CacheKeyBuilder`. It does
not create Redis connections or cache adapters. Configure the application/environment
prefix once in the consuming service:

```yaml
vishop:
  cache:
    key-prefix: ${CACHE_KEY_PREFIX:${AUTH_CACHE_PREFIX:vishop:}}
```

`CACHE_KEY_PREFIX` is the shared variable for all namespaces. The IAM/gateway YAML
keeps `AUTH_CACHE_PREFIX` as a fallback for existing deployments. The library alone
defaults to an empty prefix; it does not assume an application or environment name.

`CacheKeyBuilder.build(namespace, parts...)` returns a full Redis key:

```java
keys.build("subscription", "plan", planId);
// vishop:dev:subscription:plan:{planId}, when prefix is vishop:dev:
```

Segments cannot be blank, null or contain `:`. UUIDs and numeric IDs are supported.
The prefix is literal; include its trailing `:` when using colon-separated namespaces.
`withPrefix(logicalKey)` accepts a prebuilt logical key, including its separators.
Each module owns its logical keys, invalidation and TTL; there is no global list of
all business keys. Shared authorization keys live in `AuthorizationCacheKeys` in this module.

Add `implementation(project(":libs:common-cache"))` and
`implementation("org.springframework.boot:spring-boot-starter-data-redis")`.
Register a bean in the consuming service's infrastructure configuration:

```java
@Bean
CacheService cacheService(
    StringRedisTemplate redis, ObjectMapper objectMapper, CacheKeyBuilder keys) {
  return new RedisCacheService(redis, objectMapper, keys);
}
```

Use the application's configured `ObjectMapper` for JSON, including modules for
date/time types. Read with the same DTO type that was stored. String values use
Redis's native string format; objects use JSON without embedded Java type metadata.
Adapters receive logical keys (for example `catalog:product:42`) and add the configured
prefix themselves. Do not pass `keys.build(...)` to an adapter using the same prefix:
that would apply the prefix twice. Full keys from `build(...)` are for direct Redis
operations or adapters explicitly configured with an empty prefix. String-prefix
constructors remain available for existing callers and preserve their literal prefix.
Both blocking and reactive adapters accept the shared `CacheKeyBuilder`.

Business modules use cache through an infrastructure adapter implementing a port.
IAM's authorization projection and gateway's authorization reader inject the same
key builder. They keep their versioned hash/Lua protocol rather than treating
authorization data as an ordinary string cache.

Redis deployment, password, volumes and networking remain in
[`infra/compose`](../../infra/compose). Spring Boot configures connections from each
service's `spring.data.redis` properties. The auto-configuration registers only
properties and the key builder; callers still register the adapters they need.

## Reactive reads and atomic operations

`ReactiveCacheReader`, implemented by `ReactiveRedisCacheReader`, provides
nonblocking `get`, `getAll` and `getHashValue` operations. It uses the same format
and prefix conventions as the blocking adapter. Single misses complete empty;
batch results preserve key order, duplicate keys and missing entries as `Optional`.
Configure it from `ReactiveStringRedisTemplate` and the application's `ObjectMapper`.
JWT authentication does not depend on this cache reader.

`RedisCacheService.execute(script, keys, arguments)` runs caller-owned Lua atomically.
Only keys are prefixed; script arguments and results use Redis's native format.
The script must return a non-null result. On Redis Cluster, all script keys must
share a hash slot. This Redis-specific method belongs to the infrastructure adapter,
while `CacheService` remains independent of Redis APIs.

Cache keys, invalidation and scripts are owned by each caller. Authorization rules
remain outside this library; its versioned Lua protocol is used by IAM/gateway.

For authorization, `AuthorizationCacheKeys` defines separate user, session, tenant,
membership and role keys. Every key is a Hash with `payload`, `version`, and `checkedAt`.
Membership uses `auth:member:{tenantId}:{userId}`, independently of `auth:user:{userId}`.
Session keys retain absolute TTLs; other authorization keys retain revision watermarks.
See [membership cache V13](../../docs/core-architecture/membership-cache.md) for the
physical layout and coordinated rollout requirements.

## Example for future callers

```java
cache.put("catalog:product:" + product.id(), product, Duration.ofMinutes(5));
Optional<ProductDto> cached = cache.get("catalog:product:" + id, ProductDto.class);
cache.evict("catalog:product:" + id);
```

Run `./gradlew :libs:common-cache:test` for tests against embedded Redis.
