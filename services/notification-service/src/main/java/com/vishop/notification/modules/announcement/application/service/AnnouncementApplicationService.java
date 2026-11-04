package com.vishop.notification.modules.announcement.application.service;

import com.vishop.notification.modules.announcement.application.api.AnnouncementApi;
import com.vishop.notification.modules.announcement.application.port.SystemTypePolicy;
import com.vishop.notification.modules.announcement.domain.entity.Announcement;
import com.vishop.notification.modules.announcement.domain.port.AnnouncementRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class AnnouncementApplicationService implements AnnouncementApi {
  private final AnnouncementRepository repository;
  private final SystemTypePolicy typePolicy;
  private final Clock clock;
  private final Supplier<UUID> ids;

  public AnnouncementApplicationService(
      AnnouncementRepository repository,
      SystemTypePolicy typePolicy,
      Clock clock,
      Supplier<UUID> ids) {
    this.repository = Objects.requireNonNull(repository);
    this.typePolicy = Objects.requireNonNull(typePolicy);
    this.clock = Objects.requireNonNull(clock);
    this.ids = Objects.requireNonNull(ids);
  }

  @Override
  public Snapshot create(Draft draft) {
    typePolicy.requirePublishableSystemType(draft.type());
    var value =
        new Announcement(
            ids.get(),
            draft.type(),
            draft.title(),
            draft.body(),
            audience(draft.audience()),
            draft.channels(),
            0,
            null);
    repository.save(value, -1);
    return snapshot(value);
  }

  @Override
  public Snapshot revise(UUID id, long expectedVersion, Draft draft) {
    var old = current(id, expectedVersion);
    if (!old.type().equals(draft.type())) throw new IllegalArgumentException("Type is immutable");
    typePolicy.requirePublishableSystemType(draft.type());
    var value =
        old.revise(draft.title(), draft.body(), audience(draft.audience()), draft.channels());
    repository.save(value, expectedVersion);
    return snapshot(value);
  }

  @Override
  public Snapshot publish(UUID id, long expectedVersion) {
    var old = current(id, expectedVersion);
    typePolicy.requirePublishableSystemType(old.type());
    var value = old.publish(clock.instant());
    repository.save(value, expectedVersion);
    return snapshot(value);
  }

  private Announcement current(UUID id, long expectedVersion) {
    var value =
        repository
            .find(id)
            .orElseThrow(() -> new IllegalArgumentException("Announcement not found"));
    if (value.version() != expectedVersion)
      throw new IllegalStateException("Stale announcement version");
    return value;
  }

  private static Announcement.Audience audience(Audience audience) {
    return new Announcement.Audience(
        Announcement.AudienceMode.valueOf(audience.mode().name()), audience.tenantIds());
  }

  private static Snapshot snapshot(Announcement value) {
    return new Snapshot(
        value.id(),
        value.type(),
        value.title(),
        value.body(),
        new Audience(
            AudienceMode.valueOf(value.audience().mode().name()), value.audience().tenantIds()),
        value.channels(),
        value.version(),
        value.publishedAt());
  }
}
