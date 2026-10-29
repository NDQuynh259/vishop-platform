package com.vishop.iam.infra.security;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/** Wakes the durable relay after a business commit; scheduled delivery remains the retry path. */
@Configuration(proxyBeanMethods = false)
public class AuthorizationCommitConfiguration {
  @Bean(destroyMethod = "close")
  ExecutorService authorizationCommitExecutor(
      PlatformTransactionManager manager,
      ObjectProvider<AuthorizationProjection> projection,
      @Value("${vishop.security.authorization.eager-publish:true}") boolean enabled) {
    var executor =
        Executors.newSingleThreadExecutor(
            r -> {
              var thread = new Thread(r, "authorization-projection");
              thread.setDaemon(true);
              return thread;
            });
    var running = new AtomicBoolean();
    if (enabled && manager instanceof ConfigurableTransactionManager configurable) {
      configurable.addListener(
          new TransactionExecutionListener() {
            @Override
            public void afterCommit(TransactionExecution tx, Throwable failure) {
              if (failure != null
                  || tx.isReadOnly()
                  || !tx.getTransactionName().contains(".modules.")
                  || !running.compareAndSet(false, true)) return;
              executor.execute(
                  () -> {
                    try {
                      projection.getObject().publish();
                    } catch (Exception unavailable) {
                      /* Pending rows are retained for scheduled retry. */
                    } finally {
                      running.set(false);
                    }
                  });
            }
          });
    }
    return executor;
  }
}
