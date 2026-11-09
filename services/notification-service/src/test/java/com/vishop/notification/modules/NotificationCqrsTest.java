package com.vishop.notification.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.publication.NotificationSystemTypePolicy;
import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi;
import com.vishop.notification.modules.announcement.application.command.*;
import com.vishop.notification.modules.announcement.application.service.NotificationAnnouncementService;
import com.vishop.notification.modules.announcement.domain.entity.NotificationAnnouncement;
import com.vishop.notification.modules.announcement.domain.port.NotificationAnnouncementRepository;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelUpdate;
import com.vishop.notification.modules.setting.application.command.UpdateChannelCommand;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class NotificationCqrsTest {
  @Test
  void announcementCommandsUseRegisteredHandlersAndEnforceVersion() {
    var repository = mock(NotificationAnnouncementRepository.class);
    var stored = new HashMap<UUID, NotificationAnnouncement>();
    when(repository.find(any()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
    doAnswer(
            invocation -> {
              NotificationAnnouncement value = invocation.getArgument(0);
              stored.put(value.id(), value);
              return null;
            })
        .when(repository)
        .save(any(), anyLong());
    var application =
        new NotificationAnnouncementService(
            repository, new NotificationSystemTypePolicy(), Clock.systemUTC(), UUID::randomUUID);
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(NotificationAnnouncementApi.class, () -> application);
      context.register(
          CqrsAutoConfiguration.class,
          CreateAnnouncementCommandHandler.class,
          ReviseAnnouncementCommandHandler.class,
          PublishAnnouncementCommandHandler.class);
      context.refresh();
      var commands = context.getBean(CommandBus.class);
      var audience =
          new NotificationAnnouncementApi.Audience(
              NotificationAnnouncementApi.AudienceMode.ALL_ACTIVE_TENANTS, Set.of());
      var draft =
          new NotificationAnnouncementApi.Draft(
              "SYSTEM_MAINTENANCE",
              "Maintenance",
              "Scheduled",
              audience,
              Set.of(NotificationChannel.IN_APP));
      var created = commands.dispatch(new CreateAnnouncementCommand(draft));
      assertThat(created.version()).isZero();
      var revised = commands.dispatch(new ReviseAnnouncementCommand(created.id(), 0, draft));
      assertThat(revised.version()).isEqualTo(1);
      assertThatThrownBy(() -> commands.dispatch(new PublishAnnouncementCommand(created.id(), 0)))
          .isInstanceOf(IllegalStateException.class);
      assertThat(stored.get(created.id()).publishedAt()).isNull();
      var published = commands.dispatch(new PublishAnnouncementCommand(created.id(), 1));
      assertThat(published.version()).isEqualTo(2);
      assertThat(published.publishedAt()).isNotNull();
      assertThat(stored.get(created.id()).publishedAt()).isEqualTo(published.publishedAt());
    }
  }

  @Test
  void commandsDoNotExposeConnectionSecrets() {
    var change = new ChannelUpdate(false, Map.of("appToken", "secret-reference"), 0);
    var configure = new UpdateChannelCommand(UUID.randomUUID(), UUID.randomUUID(), change);
    assertThat(configure.toString()).doesNotContain("secret-reference");
  }
}
