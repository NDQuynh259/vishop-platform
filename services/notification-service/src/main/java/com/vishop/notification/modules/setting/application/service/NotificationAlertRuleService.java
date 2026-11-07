package com.vishop.notification.modules.setting.application.service;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi;
import com.vishop.notification.modules.setting.domain.entity.NotificationAlertRule;
import com.vishop.notification.modules.setting.domain.port.NotificationAlertRuleRepository;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationAlertRuleService implements NotificationAlertRuleApi {
  private final NotificationAlertRuleRepository repository;

  public List<RuleView> rules(UUID tenantId) {
    var result = new LinkedHashMap<String, RuleView>();
    for (var type : NotificationTypeCode.values()) result.put(type.code(), defaults(type));
    for (var rule : repository.findAll(tenantId)) result.put(rule.getType(), view(rule));
    return List.copyOf(result.values());
  }

  public RuleView ruleById(UUID tenantId, UUID ruleId) {
    return view(repository.findById(tenantId, ruleId));
  }

  public RuleView ruleForType(UUID tenantId, NotificationTypeCode type) {
    return repository
        .findByType(tenantId, type.code())
        .map(this::view)
        .orElseGet(() -> defaults(type));
  }

  @Transactional
  public RuleView createRule(
      UUID tenantId,
      NotificationTypeCode type,
      boolean enabled,
      Set<NotificationChannel> channels) {
    if (type == null) throw invalid("Notification type is required");
    return view(
        repository.create(
            tenantId, new NotificationAlertRule(null, type.code(), enabled, channels, 0)));
  }

  @Transactional
  public RuleView updateRule(UUID tenantId, UUID ruleId, RuleChange change) {
    var current = repository.findById(tenantId, ruleId);
    if (change.getVersion() < 0 || change.getVersion() == Long.MAX_VALUE)
      throw invalid("Invalid rule version");
    if (current.getVersion() != change.getVersion())
      throw new ConflictException("Rule version has changed");
    if (NotificationTypeCode.fromCode(current.getType()).isEmpty())
      throw invalid("Notification type is not in the code catalog");
    var updated =
        new NotificationAlertRule(
            ruleId,
            current.getType(),
            change.isEnabled(),
            change.getChannels(),
            change.getVersion());
    return view(repository.update(tenantId, updated, change.getVersion()));
  }

  private RuleView defaults(NotificationTypeCode type) {
    return new RuleView(
        null, type.code(), type.displayName(), type.defaultEnabled(), type.defaultChannels(), 0);
  }

  private RuleView view(NotificationAlertRule rule) {
    String name =
        NotificationTypeCode.fromCode(rule.getType())
            .map(NotificationTypeCode::displayName)
            .orElse(rule.getType());
    return new RuleView(
        rule.getId(),
        rule.getType(),
        name,
        rule.isEnabled(),
        rule.getChannels(),
        rule.getVersion());
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
