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

Add `implementation(project(":libs:common-cache"))` and
`implementation("org.springframework.boot:spring-boot-starter-data-redis")`.
Register a bean in the consuming service's infrastructure configuration:

```java
@Bean
CacheService cacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
  return new RedisCacheService(redis, objectMapper, "catalog:cache:");
}
```

Use the application's configured `ObjectMapper` for JSON, including modules for
date/time types. Read with the same DTO type that was stored. String values use
Redis's native string format; objects use JSON without embedded Java type metadata.
The namespace is prepended to every supplied key. Use a service-specific prefix for
business caches; an empty prefix is supported for existing fully qualified keys.

IAM registers the default `CacheService` in `infra/config/CacheConfiguration`, with
prefix `${vishop.cache.key-prefix:iam:cache:}`. It also registers a qualified
`securityCache` adapter with an empty prefix for shared security keys.
Business modules use cache through an infrastructure adapter implementing a port.

Redis deployment, password, volumes and networking remain in
[`infra/compose`](../../infra/compose). Spring Boot configures connections from each
service's `spring.data.redis` properties. This library does not create connections
or auto-register beans.

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

Cache keys, invalidation and scripts are owned by each caller. No security rules live
in this library; stateless JWT authentication does not use a security projection.

## Example for future callers

```java
cache.put("product:" + product.id(), product, Duration.ofMinutes(5));
Optional<ProductDto> cached = cache.get("product:" + id, ProductDto.class);
cache.evict("product:" + id);
```

Run `./gradlew :libs:common-cache:test` for tests against embedded Redis.
