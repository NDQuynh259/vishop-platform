package com.vishop.subscription.database;

import static org.assertj.core.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

class SubscriptionSchemaTest {
  static EmbeddedPostgres postgres;
  String url;
  Connection db;

  @BeforeAll
  static void start() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
  }

  @AfterAll
  static void stop() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void database() throws Exception {
    String name = "schema_" + UUID.randomUUID().toString().replace("-", "");
    try (var c = postgres.getPostgresDatabase().getConnection();
        var s = c.createStatement()) {
      s.execute("CREATE DATABASE " + name);
    }
    url = postgres.getJdbcUrl("postgres", name);
    db = DriverManager.getConnection(url, "postgres", "postgres");
  }

  @AfterEach
  void close() throws Exception {
    db.close();
  }

  Flyway flyway(String target) {
    var c =
        Flyway.configure()
            .dataSource(url, "postgres", "postgres")
            .locations("classpath:db/migration")
            .cleanDisabled(true);
    if (target != null) c.target(target);
    return c.load();
  }

  void execute(String sql, Object... args) throws SQLException {
    execute(db, sql, args);
  }

  static void execute(Connection c, String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      s.executeUpdate();
    }
  }

  long count(String table) throws SQLException {
    try (var s = db.createStatement();
        var r = s.executeQuery("SELECT count(*) FROM " + table)) {
      r.next();
      return r.getLong(1);
    }
  }

  Object value(String sql) throws SQLException {
    try (var s = db.createStatement();
        var r = s.executeQuery(sql)) {
      r.next();
      return r.getObject(1);
    }
  }

  UUID tenant() throws SQLException {
    UUID t = UUID.randomUUID();
    execute(
        "INSERT INTO subscriptions(tenant_id,plan_code,purchased_cycles,revision) VALUES (?,'FREE',0,0)",
        t);
    return t;
  }

  UUID price() throws SQLException {
    UUID p = UUID.randomUUID();
    execute(
        "INSERT INTO subscription_prices(id,plan_code,name,cycle,amount_vnd,shop_limit,monthly_orders,additional_members,active) VALUES (?,'BASIC','Basic','MONTHLY',100000,3,3000,3,true)",
        p);
    return p;
  }

  UUID order(UUID t, UUID p) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "INSERT INTO subscription_orders(id,tenant_id,actor_id,price_id,plan_code,plan_name,cycle,amount_vnd,shop_limit,monthly_orders,additional_members,payment_code,idempotency_key,expected_revision,received_vnd,order_status,payment_status,fulfillment_status,created_at,expires_at) VALUES (?,?,?,?,'BASIC','Basic','MONTHLY',100000,3,3000,3,?,?,0,0,'OPEN','UNPAID','PENDING','2026-01-31 03:00Z','2026-01-31 04:00Z')",
        id,
        t,
        UUID.randomUUID(),
        p,
        "VS" + id,
        id.toString());
    return id;
  }

  UUID payment() throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "INSERT INTO payment_transactions(id,provider_transaction_id,bank,account_number,amount_vnd,transfer_type,status,occurred_at,created_at) VALUES (?,?,'Vietcombank','123',100000,'in','REVIEW',now(),now())",
        id,
        (long) new Random().nextInt(1, Integer.MAX_VALUE));
    return id;
  }

  void rejects(String state, String sql, Object... args) {
    assertThatThrownBy(() -> execute(sql, args))
        .isInstanceOf(SQLException.class)
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(state));
  }

  @Test
  void upgradePreservesLegacyMoneyTermsAndPendingMessages() throws Exception {
    assertThat(flyway("1").migrate().migrationsExecuted).isEqualTo(1);
    UUID tenant = UUID.randomUUID(), price = UUID.randomUUID(), order = UUID.randomUUID();
    execute(
        "INSERT INTO billing_prices VALUES (?,'BASIC','Basic','MONTHLY',100000,3,3000,3,true)",
        price);
    execute(
        "INSERT INTO billing_accounts VALUES (?,'BASIC','MONTHLY','2026-01-31 03:00Z',1,'2026-02-28 03:00Z',1)",
        tenant);
    execute(
        "INSERT INTO billing_orders(id,tenant_id,actor_id,price_id,plan_code,plan_name,cycle,amount_vnd,shop_limit,monthly_orders,additional_members,payment_code,idempotency_key,expected_revision,received_vnd,state,created_at,expires_at,term_starts_at,term_ends_at,review_reason) VALUES (?,?,?,?,'BASIC','Basic','MONTHLY',100000,3,3000,3,'VSLEGACY','legacy',0,110000,'REQUIRES_REVIEW','2026-01-31 03:00Z','2026-01-31 04:00Z','2026-01-31 03:00Z','2026-02-28 03:00Z','PAYMENT_FOR_CLOSED_ORDER')",
        order,
        tenant,
        UUID.randomUUID(),
        price);
    execute(
        "INSERT INTO subscription_terms VALUES (?,?,'2026-01-31 03:00Z','2026-02-28 03:00Z')",
        order,
        tenant);
    execute(
        "INSERT INTO sepay_inbox(id,raw_payload,payload_hash,status,received_at,processed_at,review_reason) VALUES (100,'{}',repeat('a',64),'DONE',now(),now(),null),(101,'{}',repeat('b',64),'DONE',now(),now(),null),(102,'{}',repeat('c',64),'PENDING',now(),null,null)");
    // Explicit columns keep fixtures independent from physical column order in V1.
    execute(
        "INSERT INTO bank_payments(id,order_id,account_number,bank,reference_code,amount_vnd,transfer_type,status,occurred_at,created_at) VALUES (100,?,'123','Vietcombank','REF100',100000,'in','ALLOCATED',now(),now()),(101,?,'123','Vietcombank','REF101',10000,'in','REVIEW',now(),now())",
        order,
        order);
    execute(
        "INSERT INTO outbox_events(id,event_type,aggregate_type,aggregate_id,payload,occurred_at) VALUES (?,'SubscriptionActivated','Billing',?,'{}',now())",
        UUID.randomUUID(),
        tenant);
    assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(1);
    flyway(null).validate();
    assertThat(count("payment_transactions")).isEqualTo(2);
    assertThat(count("payment_allocations")).isEqualTo(1);
    assertThat(count("subscription_terms")).isEqualTo(1);
    assertThat(count("outbox_events")).isEqualTo(1);
    assertThat(value("SELECT order_status FROM subscription_orders")).isEqualTo("COMPLETED");
    assertThat(value("SELECT fulfillment_status FROM subscription_orders")).isEqualTo("APPLIED");
    assertThat(value("SELECT payment_status FROM subscription_orders")).isEqualTo("OVERPAID");
    assertThat(value("SELECT received_vnd FROM subscription_orders")).isEqualTo(110000L);
    assertThat(
            value(
                "SELECT allocated_vnd FROM payment_transactions WHERE provider_transaction_id=100"))
        .isEqualTo(100000L);
    assertThat(
            value(
                "SELECT allocated_vnd FROM payment_transactions WHERE provider_transaction_id=101"))
        .isEqualTo(0L);
    assertThat(value("SELECT payment_transaction_id FROM sepay_webhook_inbox WHERE id=100"))
        .isEqualTo(value("SELECT id FROM payment_transactions WHERE provider_transaction_id=100"));
    assertThat(value("SELECT status FROM sepay_webhook_inbox WHERE id=102")).isEqualTo("PENDING");
    assertThat(value("SELECT raw_payload FROM sepay_webhook_inbox WHERE id=102")).isEqualTo("{}");
    assertThat(flyway(null).migrate().migrationsExecuted).isZero();
  }

  @Test
  void inconsistentLegacyDataAbortsMigrationWithoutRenamingOrDeletingIt() throws Exception {
    flyway("1").migrate();
    UUID price = UUID.randomUUID();
    execute(
        "INSERT INTO billing_prices VALUES (?,'BASIC','Basic','MONTHLY',100000,3,3000,3,true)",
        price);
    execute(
        "INSERT INTO billing_orders(id,tenant_id,actor_id,price_id,plan_code,plan_name,cycle,amount_vnd,shop_limit,monthly_orders,additional_members,payment_code,idempotency_key,expected_revision,received_vnd,state,created_at,expires_at) VALUES (?,?,?,?,'BASIC','Basic','MONTHLY',100000,3,3000,3,'VSORPHAN','orphan',0,0,'AWAITING_PAYMENT',now(),now()+interval '1 hour')",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        price);
    assertThatThrownBy(() -> flyway(null).migrate()).isInstanceOf(RuntimeException.class);
    assertThat(count("billing_orders")).isEqualTo(1);
    assertThat(value("SELECT state FROM billing_orders")).isEqualTo("AWAITING_PAYMENT");
    assertThat(flyway("1").info().current().getVersion().toString()).isEqualTo("1");
  }

  @Test
  void databaseEnforcesTenantOwnershipDatesAndRealFulfillment() throws Exception {
    flyway(null).migrate();
    UUID t = tenant(), other = tenant(), p = price(), o = order(t, p);
    rejects(
        "23503",
        "INSERT INTO subscription_terms(order_id,tenant_id,starts_at,ends_at) VALUES (?,?,'2026-01-31 03:00Z','2026-02-28 03:00Z')",
        o,
        other);
    rejects(
        "23514",
        "INSERT INTO subscription_terms(order_id,tenant_id,starts_at,ends_at) VALUES (?,?,'2026-01-31 03:00Z','2026-02-28 03:00Z')",
        o,
        t);
    rejects(
        "23514",
        "UPDATE subscription_orders SET order_status='COMPLETED',fulfillment_status='APPLIED',received_vnd=100000,payment_status='PAID',term_starts_at='2026-01-31 03:00Z',term_ends_at='2026-02-28 03:00Z' WHERE id=?",
        o);
    rejects("23514", "UPDATE subscription_orders SET expires_at=created_at WHERE id=?", o);
    rejects("23514", "UPDATE subscriptions SET plan_code='BASIC' WHERE tenant_id=?", t);
    rejects("23514", "UPDATE subscription_orders SET payment_status='PAID' WHERE id=?", o);
    rejects("23514", "UPDATE subscription_orders SET amount_vnd=200000 WHERE id=?", o);
    db.setAutoCommit(false);
    execute(
        "UPDATE subscription_orders SET order_status='COMPLETED',fulfillment_status='APPLIED',received_vnd=100000,payment_status='PAID',term_starts_at='2026-01-31 03:00Z',term_ends_at='2026-02-28 03:00Z' WHERE id=?",
        o);
    execute(
        "INSERT INTO subscription_terms(order_id,tenant_id,starts_at,ends_at) VALUES (?,?,'2026-01-31 03:00Z','2026-02-28 03:00Z')",
        o,
        t);
    db.commit();
    db.setAutoCommit(true);
    assertThat(count("subscription_terms")).isEqualTo(1);
  }

  @Test
  void allocationsCannotExceedBankMoneyOrForgeTheCachedBalance() throws Exception {
    flyway(null).migrate();
    UUID t = tenant(),
        p = price(),
        o = order(t, p),
        payment = payment(),
        allocation = UUID.randomUUID();
    execute(
        "INSERT INTO payment_allocations(id,payment_id,tenant_id,order_id,amount_vnd) VALUES (?,?,?,?,60000)",
        allocation,
        payment,
        t,
        o);
    assertThat(value("SELECT allocated_vnd FROM payment_transactions")).isEqualTo(60000L);
    rejects("23514", "UPDATE payment_allocations SET amount_vnd=110000 WHERE id=?", allocation);
    assertThat(value("SELECT allocated_vnd FROM payment_transactions")).isEqualTo(60000L);
    rejects("23514", "UPDATE payment_transactions SET allocated_vnd=70000 WHERE id=?", payment);
    rejects("23514", "UPDATE payment_transactions SET amount_vnd=120000 WHERE id=?", payment);
    rejects("23514", "UPDATE subscription_prices SET amount_vnd=200000 WHERE id=?", p);
    execute("UPDATE subscription_prices SET active=false WHERE id=?", p);
    execute("DELETE FROM payment_allocations WHERE id=?", allocation);
    assertThat(value("SELECT allocated_vnd FROM payment_transactions")).isEqualTo(0L);
  }

  @Test
  void concurrentAllocationsUseThePaymentRowAsAnAtomicBalanceGuard() throws Exception {
    flyway(null).migrate();
    UUID t1 = tenant(),
        t2 = tenant(),
        p = price(),
        o1 = order(t1, p),
        o2 = order(t2, p),
        payment = payment();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var futures = new ArrayList<Future<String>>();
      for (var target : List.of(new UUID[] {t1, o1}, new UUID[] {t2, o2}))
        futures.add(
            pool.submit(
                () -> {
                  try (var c = DriverManager.getConnection(url, "postgres", "postgres")) {
                    ready.countDown();
                    start.await();
                    try {
                      execute(
                          c,
                          "INSERT INTO payment_allocations(id,payment_id,tenant_id,order_id,amount_vnd) VALUES (?,?,?,?,70000)",
                          UUID.randomUUID(),
                          payment,
                          target[0],
                          target[1]);
                      return "OK";
                    } catch (SQLException rejected) {
                      return rejected.getSQLState();
                    }
                  }
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  futures.get(0).get(10, TimeUnit.SECONDS),
                  futures.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "23514");
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
    assertThat(count("payment_allocations")).isEqualTo(1);
    assertThat(value("SELECT allocated_vnd FROM payment_transactions")).isEqualTo(70000L);
  }
}
