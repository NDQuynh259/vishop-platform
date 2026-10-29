package com.vishop.subscription;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.vishop.subscription.database.JpaDatabaseFixture;
import com.vishop.subscription.infra.messaging.SubscriptionOutbox;
import com.vishop.subscription.modules.payment.application.command.ProcessBankTransferCommand;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(
    properties = {
      "vishop.outbox.enabled=false",
      "vishop.subscription.worker.enabled=false",
      "management.tracing.enabled=false",
      "vishop.subscription.sepay.enabled=true",
      "vishop.subscription.sepay.webhook-secret=test-sepay-signing-secret-at-least-32-bytes",
      "vishop.subscription.sepay.bank=Vietcombank",
      "vishop.subscription.sepay.account-number=0012345678",
      "vishop.subscription.sepay.account-holder=VISHOP",
      "vishop.security.jwt.secret=test-billing-jwt-secret-at-least-32-bytes"
    })
@AutoConfigureMockMvc
@Import(SubscriptionApplicationTest.TimeConfiguration.class)
class SubscriptionApplicationTest {
  @Autowired org.springframework.context.ApplicationContext context;
  static final Instant NOW = Instant.parse("2026-01-31T03:00:00Z");
  static final String SECRET = "test-sepay-signing-secret-at-least-32-bytes";
  static final EmbeddedPostgres PG = start();

  static EmbeddedPostgres start() {
    try {
      var pg = EmbeddedPostgres.builder().setPort(0).start();
      org.flywaydb.core.Flyway.configure()
          .dataSource(pg.getPostgresDatabase())
          .locations("classpath:db/migration")
          .load()
          .migrate();
      return pg;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "postgres");
  }

  @AfterAll
  static void stop() throws Exception {
    PG.close();
  }

  @TestConfiguration
  static class TimeConfiguration {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc http;
  @Autowired ObjectMapper json;
  @Autowired JwtTokenProvider tokens;
  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CommandBus commands;
  @SpyBean SubscriptionOutbox outbox;
  JpaDatabaseFixture db;

  @BeforeEach
  void reset() {
    org.mockito.Mockito.reset(
        org.springframework.test.util.AopTestUtils.<SubscriptionOutbox>getUltimateTargetObject(
            outbox));
    db = new JpaDatabaseFixture(em, transactions);
    db.execute(
        "TRUNCATE billing_prices,billing_accounts,billing_orders,subscription_terms,sepay_inbox,bank_payments,outbox_events CASCADE");
  }

  String token(UUID tenant, Set<String> permissions) {
    return tokens.generateIamAccessToken(
        UUID.randomUUID(), tenant, "USER", tenant == null ? null : "OWNER", permissions);
  }

  String owner(UUID tenant) {
    return token(tenant, Set.of("billing.view", "billing.manage"));
  }

  String admin() {
    return tokens.generateIamAccessToken(UUID.randomUUID(), null, "PLATFORM_ADMIN", null, Set.of());
  }

  MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
  }

  JsonNode result(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsByteArray());
  }

  String price() throws Exception {
    return price("BASIC", 100000);
  }

  String price(String plan, long amount) throws Exception {
    return result(
            http.perform(
                    body(
                        auth(post("/api/v1/cms/billing/prices"), admin()),
                        Map.of(
                            "planCode",
                            plan,
                            "name",
                            plan,
                            "cycle",
                            "MONTHLY",
                            "amountVnd",
                            amount,
                            "shopLimit",
                            3,
                            "monthlyOrders",
                            3000,
                            "additionalMembers",
                            3)))
                .andExpect(status().isOk())
                .andReturn())
        .path("id")
        .asText();
  }

  JsonNode checkout(String bearer, String price, String key) throws Exception {
    return result(
        http.perform(
                body(
                    auth(post("/api/v1/billing/orders"), bearer).header("Idempotency-Key", key),
                    Map.of("priceId", price)))
            .andExpect(status().isOk())
            .andReturn());
  }

  String raw(long id, String code, long amount, String account, String reference) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "id",
            id,
            "gateway",
            "Vietcombank",
            "transactionDate",
            "2026-01-31 10:00:00",
            "accountNumber",
            account,
            "subAccount",
            "",
            "code",
            code,
            "transferType",
            "in",
            "transferAmount",
            amount,
            "referenceCode",
            reference));
  }

  String signature(String timestamp, String payload) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256="
        + HexFormat.of()
            .formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
  }

  void webhook(String payload) throws Exception {
    String ts = Long.toString(NOW.getEpochSecond());
    http.perform(
            post("/api/v1/billing/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", ts)
                .header("X-SePay-Signature", signature(ts, payload))
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("success").value(true));
  }

  void pay(long id, JsonNode order, long amount) throws Exception {
    webhook(
        raw(
            id,
            order.path("order").path("paymentCode").asText(),
            amount,
            "0012345678",
            "REF" + id));
    commands.dispatch(new ProcessBankTransferCommand(id));
  }

  long count(String table) {
    return db.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  @Test
  void subscriptionMutationBoundaryRequiresPaymentTransaction() {
    var api =
        context.getBean(
            com.vishop.subscription.modules.subscription.application.api.SubscriptionApi.class);
    assertThatThrownBy(() -> api.applyPayment(UUID.randomUUID(), UUID.randomUUID(), 100000, NOW))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(() -> api.findOrder("VSTEST"))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void explicitMigrationsAndStatelessTenantPermissions() throws Exception {
    assertThat(count("billing_prices")).isZero();
    http.perform(get("/api/v1/billing/subscription")).andExpect(status().isUnauthorized());
    UUID tenant = UUID.randomUUID();
    String allowed = owner(tenant);
    http.perform(auth(get("/api/v1/billing/subscription"), allowed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("FREE"));
    http.perform(auth(get("/api/v1/billing/plans"), token(tenant, Set.of())))
        .andExpect(status().isForbidden());
    http.perform(
            body(
                auth(post("/api/v1/cms/billing/prices"), allowed),
                Map.of(
                    "planCode",
                    "BASIC",
                    "name",
                    "Basic",
                    "cycle",
                    "MONTHLY",
                    "amountVnd",
                    1,
                    "shopLimit",
                    1,
                    "monthlyOrders",
                    1,
                    "additionalMembers",
                    0)))
        .andExpect(status().isForbidden());
    var order = checkout(allowed, price(), "scope");
    String id = order.path("order").path("id").asText();
    assertThat(order.path("qrUrl").asText())
        .startsWith("https://vietqr.app/img?")
        .contains("amount=100000");
    http.perform(
            auth(get("/api/v1/billing/orders/" + id), owner(UUID.randomUUID()))
                .header("X-Tenant-Id", tenant))
        .andExpect(status().isNotFound());
    http.perform(auth(get("/api/v1/billing/subscription"), admin()))
        .andExpect(status().isForbidden());
  }

  @Test
  void durableWebhookDeduplicatesAndRenewsWithoutCalendarAnchorDrift() throws Exception {
    UUID tenant = UUID.randomUUID();
    String actor = owner(tenant);
    String price = price();
    var first = checkout(actor, price, "first");
    String payload =
        raw(1, first.path("order").path("paymentCode").asText(), 100000, "0012345678", "REF1");
    webhook(payload);
    assertThat(count("sepay_inbox")).isEqualTo(1);
    assertThat(count("subscription_terms")).isZero();
    commands.dispatch(new ProcessBankTransferCommand(1));
    webhook(payload);
    commands.dispatch(new ProcessBankTransferCommand(1));
    webhook(json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(payload)));
    http.perform(auth(get("/api/v1/billing/subscription"), actor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("ACTIVE"))
        .andExpect(jsonPath("endsAt").value("2026-02-28T03:00:00Z"));
    assertThat(count("subscription_terms")).isEqualTo(1);
    webhook(raw(2, first.path("order").path("paymentCode").asText(), 100000, "0012345678", "REF1"));
    commands.dispatch(new ProcessBankTransferCommand(2));
    assertThat(count("bank_payments")).isEqualTo(1);
    webhook(raw(4, first.path("order").path("paymentCode").asText(), 200000, "0012345678", "REF1"));
    commands.dispatch(new ProcessBankTransferCommand(4));
    http.perform(auth(get("/api/v1/cms/billing/webhooks/review"), admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("data[0].reason").value("CONFLICTING_BANK_REFERENCE"));
    var second = checkout(actor, price, "renew");
    pay(3, second, 100000);
    http.perform(auth(get("/api/v1/billing/subscription"), actor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("endsAt").value("2026-03-31T03:00:00Z"));
    assertThat(count("subscription_terms")).isEqualTo(2);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='SubscriptionActivated'",
                Long.class))
        .isEqualTo(2);
  }

  @Test
  void partialOverpaidAndLateTransfersRemainTraceable() throws Exception {
    UUID tenant = UUID.randomUUID();
    String actor = owner(tenant);
    String price = price();
    var order = checkout(actor, price, "partial");
    pay(10, order, 40000);
    assertThat(count("subscription_terms")).isZero();
    http.perform(
            auth(get("/api/v1/billing/orders/" + order.path("order").path("id").asText()), actor))
        .andExpect(jsonPath("order.status").value("PARTIALLY_PAID"));
    pay(11, order, 70000);
    assertThat(count("subscription_terms")).isZero();
    assertThat(
            db.queryForObject("SELECT review_reason FROM bank_payments WHERE id=11", String.class))
        .isEqualTo("OVERPAYMENT");
    var late = checkout(actor, price, "late");
    db.update(
        "UPDATE billing_orders SET expires_at='2026-01-31 02:59:00Z' WHERE id=CAST(? AS uuid)",
        late.path("order").path("id").asText());
    pay(12, late, 100000);
    assertThat(
            db.queryForObject("SELECT review_reason FROM bank_payments WHERE id=12", String.class))
        .isEqualTo("LATE_PAYMENT");
    webhook(raw(13, "VSUNKNOWN", 10000, "0012345678", "REF13"));
    commands.dispatch(new ProcessBankTransferCommand(13));
    webhook(raw(14, "VSUNKNOWN", 10000, "WRONG", "REF14"));
    commands.dispatch(new ProcessBankTransferCommand(14));
    http.perform(auth(get("/api/v1/cms/billing/payments/review"), admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("data.length()").value(4));
  }

  @Test
  void invalidSignaturesReplayAndPayloadConflictsDoNotApplyPayments() throws Exception {
    String payload = raw(20, "VSTEST", 10000, "0012345678", "REF20");
    String ts = Long.toString(NOW.getEpochSecond());
    http.perform(
            post("/api/v1/billing/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", ts)
                .header("X-SePay-Signature", "sha256=" + "0".repeat(64))
                .content(payload))
        .andExpect(status().isUnauthorized());
    String old = Long.toString(NOW.minusSeconds(301).getEpochSecond());
    http.perform(
            post("/api/v1/billing/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", old)
                .header("X-SePay-Signature", signature(old, payload))
                .content(payload))
        .andExpect(status().isUnauthorized());
    assertThat(count("sepay_inbox")).isZero();
    webhook(payload);
    String changed = raw(20, "VSTEST", 20000, "0012345678", "REF20");
    http.perform(
            post("/api/v1/billing/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", ts)
                .header("X-SePay-Signature", signature(ts, changed))
                .content(changed))
        .andExpect(status().isConflict());
    assertThat(count("sepay_inbox")).isEqualTo(1);
  }

  @Test
  void idempotentCheckoutAndConcurrentPartialTransfersGrantOnlyOneTerm() throws Exception {
    UUID tenant = UUID.randomUUID();
    String actor = owner(tenant);
    String price = price();
    var first = checkout(actor, price, "same");
    var again = checkout(actor, price, "same");
    assertThat(again.path("order").path("id")).isEqualTo(first.path("order").path("id"));
    String otherPrice = price("PRO", 200000);
    http.perform(
            body(
                auth(post("/api/v1/billing/orders"), actor).header("Idempotency-Key", "same"),
                Map.of("priceId", otherPrice)))
        .andExpect(status().isConflict());
    webhook(
        raw(30, first.path("order").path("paymentCode").asText(), 40000, "0012345678", "REF30"));
    webhook(
        raw(31, first.path("order").path("paymentCode").asText(), 60000, "0012345678", "REF31"));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a = pool.submit(() -> commands.dispatch(new ProcessBankTransferCommand(30)));
      var b = pool.submit(() -> commands.dispatch(new ProcessBankTransferCommand(31)));
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(count("subscription_terms")).isEqualTo(1);
    assertThat(count("bank_payments")).isEqualTo(2);
  }

  @Test
  void outboxFailureRollsBackMoneyAndFulfillmentAndPendingInboxCanRetry() throws Exception {
    var order = checkout(owner(UUID.randomUUID()), price(), "rollback");
    webhook(
        raw(40, order.path("order").path("paymentCode").asText(), 100000, "0012345678", "REF40"));
    doThrow(new IllegalStateException("Outbox unavailable"))
        .when(
            org.springframework.test.util.AopTestUtils.<SubscriptionOutbox>getUltimateTargetObject(
                outbox))
        .append(eq("SubscriptionActivated"), any(), any(), anyMap());
    assertThatThrownBy(() -> commands.dispatch(new ProcessBankTransferCommand(40)))
        .isInstanceOf(RuntimeException.class);
    assertThat(count("subscription_terms")).isZero();
    assertThat(count("bank_payments")).isZero();
    assertThat(db.queryForObject("SELECT status FROM sepay_inbox WHERE id=40", String.class))
        .isEqualTo("PENDING");
    org.mockito.Mockito.reset(
        org.springframework.test.util.AopTestUtils.<SubscriptionOutbox>getUltimateTargetObject(
            outbox));
    commands.dispatch(new ProcessBankTransferCommand(40));
    assertThat(count("subscription_terms")).isEqualTo(1);
  }

  @Test
  void priceRetirementPreservesExistingOrderSnapshotAndCancellationDoesNotHideMoney()
      throws Exception {
    UUID tenant = UUID.randomUUID();
    String actor = owner(tenant);
    String price = price();
    var order = checkout(actor, price, "snapshot");
    String id = order.path("order").path("id").asText();
    http.perform(
            body(
                auth(put("/api/v1/cms/billing/prices/" + price + "/availability"), admin()),
                Map.of("active", false)))
        .andExpect(status().isOk());
    pay(50, order, 100000);
    assertThat(count("subscription_terms")).isEqualTo(1);
    http.perform(
            body(
                auth(post("/api/v1/billing/orders"), actor).header("Idempotency-Key", "retired"),
                Map.of("priceId", price)))
        .andExpect(status().isBadRequest());
    var cancelled = checkout(actor, price("BASIC", 120000), "cancelled");
    String cancelledId = cancelled.path("order").path("id").asText();
    http.perform(auth(post("/api/v1/billing/orders/" + cancelledId + "/cancel"), actor))
        .andExpect(status().isOk());
    pay(51, cancelled, 120000);
    assertThat(count("subscription_terms")).isEqualTo(1);
    assertThat(
            db.queryForObject("SELECT review_reason FROM bank_payments WHERE id=51", String.class))
        .isEqualTo("PAYMENT_FOR_CLOSED_ORDER");
  }
}
