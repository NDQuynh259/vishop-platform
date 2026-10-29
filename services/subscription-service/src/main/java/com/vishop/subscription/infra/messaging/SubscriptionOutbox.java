package com.vishop.subscription.infra.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.events.EventEnvelope;
import io.github.coreplatform.common.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class SubscriptionOutbox implements BusinessEventPort {
  private final OutboxEventRepository outbox;
  private final ObjectMapper json;

  public SubscriptionOutbox(OutboxEventRepository outbox, ObjectMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(String type, UUID id, UUID tenant, Map<String, ?> payload) {
    var event =
        EventEnvelope.create(
            type,
            "subscription-service",
            tenant,
            "Billing",
            id,
            null,
            null,
            json.valueToTree(payload));
    try {
      outbox.save(OutboxEventEntity.fromEnvelope(event, json.writeValueAsString(event)));
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot serialize billing event", failure);
    }
  }
}
