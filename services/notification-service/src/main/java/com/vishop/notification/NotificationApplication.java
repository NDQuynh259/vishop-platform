package com.vishop.notification;

import com.vishop.notification.infra.security.NotificationSecurityConfiguration;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationModuleConfiguration;
import com.vishop.notification.modules.notification.presentation.rest.controller.NotificationInboxController;
import com.vishop.notification.modules.setting.infrastructure.config.NotificationSettingModuleConfiguration;
import com.vishop.notification.modules.setting.presentation.rest.controller.NotificationRuleController;
import com.vishop.notification.modules.setting.presentation.rest.controller.NotificationSettingController;
import io.github.coreplatform.common.security.config.CommonSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
    scanBasePackageClasses = NotificationSecurityConfiguration.class,
    exclude = CommonSecurityAutoConfiguration.class)
@Import({
  NotificationModuleConfiguration.class,
  NotificationSettingModuleConfiguration.class,
  NotificationInboxController.class,
  NotificationSettingController.class,
  NotificationRuleController.class
})
public class NotificationApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationApplication.class, args);
  }
}
