package io.github.coreplatform.common.cache.redis;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.coreplatform.common.cache.CacheSerializationException;
import io.github.coreplatform.common.cache.CacheService;
import io.github.coreplatform.common.cache.ReactiveCacheReader;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

class RedisCacheServiceTest {
  private static RedisServer server;
  private static LettuceConnectionFactory connection;
  private static StringRedisTemplate redis;
  private static ObjectMapper json;
  private static CacheService cache;
  private static ReactiveCacheReader reader;

  record Product(String name, List<String> tags, Instant updatedAt) {}

  @BeforeAll
  static void start() throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    server =
        RedisServer.newRedisServer()
            .port(port)
            .setting("bind 127.0.0.1")
            .setting("maxmemory 64M")
            .build();
    server.start();
    connection = new LettuceConnectionFactory("127.0.0.1", port);
    connection.afterPropertiesSet();
    connection.start();
    redis = new StringRedisTemplate(connection);
    json = new ObjectMapper().registerModule(new JavaTimeModule());
    cache = new RedisCacheService(redis, json, "test:cache:");
    reader =
        new ReactiveRedisCacheReader(
            new ReactiveStringRedisTemplate(connection), json, "test:cache:");
  }

  @AfterAll
  static void stop() throws Exception {
    if (connection != null) connection.destroy();
    if (server != null) server.stop();
  }

  @BeforeEach
  void clear() {
    try (var c = connection.getConnection()) {
      c.serverCommands().flushDb();
    }
  }

  @Test
  void storesAndReadsDtoUsingConfiguredJsonModules() {
    var product =
        new Product("Coffee", List.of("drink", "new"), Instant.parse("2026-10-25T00:00:00Z"));
    cache.put("product:42", product, Duration.ofMinutes(5));
    assertThat(cache.get("product:42", Product.class)).contains(product);
    assertThat(redis.opsForValue().get("test:cache:product:42")).contains("Coffee");
    assertThat(redis.getExpire("test:cache:product:42")).isBetween(290L, 300L);
    cache.put("label", "hello", Duration.ZERO);
    assertThat(cache.get("label", String.class)).contains("hello");
  }

  @Test
  void missingValuesAndEvictionAreExplicit() {
    assertThat(cache.get("absent", Product.class)).isEmpty();
    assertThat(cache.exists("absent")).isFalse();
    assertThat(cache.evict("absent")).isFalse();
    cache.put("product", new Product("Tea", List.of(), Instant.EPOCH), Duration.ZERO);
    assertThat(cache.exists("product")).isTrue();
    assertThat(cache.evict("product")).isTrue();
    assertThat(cache.get("product", Product.class)).isEmpty();
  }

  @Test
  void namespacesIsolateDifferentConsumers() {
    CacheService another = new RedisCacheService(redis, json, "another:cache:");
    cache.put("same-key", "first", Duration.ZERO);
    another.put("same-key", "second", Duration.ZERO);
    assertThat(cache.get("same-key", String.class)).contains("first");
    assertThat(another.get("same-key", String.class)).contains("second");
    cache.evict("same-key");
    assertThat(another.get("same-key", String.class)).contains("second");
  }

  @Test
  void zeroTtlPersistsAndCanRemoveExistingExpiry() {
    cache.put("item", "first", Duration.ofMinutes(1));
    cache.put("item", "second", Duration.ZERO);
    assertThat(redis.getExpire("test:cache:item")).isEqualTo(-1L);
    assertThat(cache.expire("item", Duration.ofMinutes(1))).isTrue();
    assertThat(redis.getExpire("test:cache:item")).isBetween(50L, 60L);
    assertThat(cache.expire("item", Duration.ZERO)).isTrue();
    assertThat(cache.expire("item", Duration.ZERO)).isTrue();
    assertThat(redis.getExpire("test:cache:item")).isEqualTo(-1L);
    assertThat(cache.expire("absent", Duration.ZERO)).isFalse();
    assertThat(cache.expire("absent", Duration.ofMinutes(1))).isFalse();
    assertThat(cache.get("item", String.class)).contains("second");
  }

  @Test
  void expirationAppliesToNewAndExistingEntries() {
    cache.put("new", "value", Duration.ofMillis(200));
    cache.put("existing", "value", Duration.ZERO);
    assertThat(cache.expire("existing", Duration.ofMillis(200))).isTrue();
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              assertThat(cache.get("new", String.class)).isEmpty();
              assertThat(cache.get("existing", String.class)).isEmpty();
            });
  }

  @Test
  void invalidInputsDoNotWriteRedis() {
    assertThatIllegalArgumentException().isThrownBy(() -> cache.put(" ", "x", Duration.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> cache.put("key", "x", Duration.ofSeconds(-1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> cache.put("key", "x", Duration.ofNanos(1)));
    assertThatNullPointerException().isThrownBy(() -> cache.put("key", null, Duration.ZERO));
    assertThatNullPointerException().isThrownBy(() -> cache.put("key", "x", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> cache.expire("key", Duration.ofSeconds(-1)));
    assertThatIllegalArgumentException().isThrownBy(() -> cache.get("", String.class));
    assertThatNullPointerException().isThrownBy(() -> cache.get("key", null));
    assertThat(redis.keys("*")).isEmpty();
  }

  @Test
  void invalidJsonAndRedisFailuresPropagateInsteadOfBecomingMisses() {
    redis.opsForValue().set("test:cache:broken", "invalid-json");
    assertThatThrownBy(() -> cache.get("broken", Product.class))
        .isInstanceOf(CacheSerializationException.class);
    redis.opsForValue().set("test:cache:null", "null");
    assertThatThrownBy(() -> cache.get("null", Product.class))
        .isInstanceOf(CacheSerializationException.class);
    redis.opsForHash().put("test:cache:wrong-type", "field", "value");
    assertThatThrownBy(() -> cache.get("wrong-type", Product.class))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void reactiveBatchReadsPreserveOrderMissingEntriesAndDuplicateKeys() {
    var product = new Product("Coffee", List.of("drink"), Instant.EPOCH);
    cache.put("product", product, Duration.ofMinutes(1));
    StepVerifier.create(reader.getAll(List.of("absent", "product", "product"), Product.class))
        .assertNext(
            values -> {
              assertThat(values).hasSize(3);
              assertThat(values.get(0)).isEmpty();
              assertThat(values.get(1)).contains(product);
              assertThat(values.get(2)).contains(product);
            })
        .verifyComplete();
    StepVerifier.create(reader.get("product", Product.class)).expectNext(product).verifyComplete();
    StepVerifier.create(reader.get("absent", Product.class)).verifyComplete();
    StepVerifier.create(reader.getAll(List.of(), String.class))
        .assertNext(values -> assertThat(values).isEmpty())
        .verifyComplete();
  }

  @Test
  void reactiveHashReadsDecodeValuesAndCompleteEmptyForMissingFields() throws Exception {
    var product = new Product("Tea", List.of(), Instant.EPOCH);
    redis.opsForHash().put("test:cache:hash", "dto", json.writeValueAsString(product));
    redis.opsForHash().put("test:cache:hash", "token", "literal-token");
    StepVerifier.create(reader.getHashValue("hash", "dto", Product.class))
        .expectNext(product)
        .verifyComplete();
    StepVerifier.create(reader.getHashValue("hash", "token", String.class))
        .expectNext("literal-token")
        .verifyComplete();
    StepVerifier.create(reader.getHashValue("hash", "absent", String.class)).verifyComplete();
  }

  @Test
  void callerOwnedScriptsUseTheCacheNamespaceAndPreserveAtomicPreconditions() {
    var adapter = new RedisCacheService(redis, json, "test:cache:");
    var script =
        new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end; redis.call('SET',KEYS[1],ARGV[2]); return 1",
            Long.class);
    cache.put("item", "before", Duration.ZERO);
    assertThat(adapter.execute(script, List.of("item"), "before", "after")).isEqualTo(1L);
    assertThat(adapter.execute(script, List.of("item"), "before", "stale")).isZero();
    assertThat(cache.get("item", String.class)).contains("after");
    assertThat(redis.hasKey("item")).isFalse();
    StepVerifier.create(reader.get("item", String.class)).expectNext("after").verifyComplete();
    var broken = new DefaultRedisScript<>("return redis.call('INVALID_CACHE_COMMAND')", Long.class);
    assertThatThrownBy(() -> adapter.execute(broken, List.of("item")))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void emptyPrefixPreservesExistingFullyQualifiedStringKeys() {
    var adapter = new RedisCacheService(redis, json, "");
    var direct =
        new ReactiveRedisCacheReader(new ReactiveStringRedisTemplate(connection), json, "");
    redis.opsForValue().set("external:state", "ACTIVE");
    assertThat(adapter.get("external:state", String.class)).contains("ACTIVE");
    adapter.put("external:literal", "\"quoted\"", Duration.ZERO);
    assertThat(redis.opsForValue().get("external:literal")).isEqualTo("\"quoted\"");
    StepVerifier.create(direct.get("external:literal", String.class))
        .expectNext("\"quoted\"")
        .verifyComplete();
    StepVerifier.create(direct.get("external:state", String.class))
        .expectNext("ACTIVE")
        .verifyComplete();
  }

  @Test
  void reactiveDecodingErrorsAreNotTreatedAsCacheMisses() {
    redis.opsForValue().set("test:cache:broken", "invalid-json");
    StepVerifier.create(reader.get("broken", Product.class))
        .expectError(CacheSerializationException.class)
        .verify();
    StepVerifier.create(reader.getAll(List.of("absent", "broken"), Product.class))
        .expectError(CacheSerializationException.class)
        .verify();
    redis.opsForHash().put("test:cache:hash", "dto", "null");
    StepVerifier.create(reader.getHashValue("hash", "dto", Product.class))
        .expectError(CacheSerializationException.class)
        .verify();
    StepVerifier.create(reader.get(" ", String.class))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void failedSerializationPreservesPreviousValue() {
    cache.put("item", "before", Duration.ZERO);
    var recursive = new java.util.HashMap<String, Object>();
    recursive.put("self", recursive);
    assertThatThrownBy(() -> cache.put("item", recursive, Duration.ZERO))
        .isInstanceOf(CacheSerializationException.class);
    assertThat(cache.get("item", String.class)).contains("before");
  }
}
