package io.github.coreplatform.common.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaConsumerConfigTest {
  @Test
  void registersErrorHandlerWhenBootCreatesKafkaTemplateAfterUserConfiguration() {
    new ApplicationContextRunner()
        .withUserConfiguration(KafkaConsumerConfig.class)
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(KafkaTemplate.class);
              assertThat(context).hasSingleBean(CommonErrorHandler.class);
              assertThat(context.getBean(CommonErrorHandler.class))
                  .isInstanceOf(DefaultErrorHandler.class);
              assertThat(context).hasBean("kafkaListenerContainerFactory");
            });
  }
}
