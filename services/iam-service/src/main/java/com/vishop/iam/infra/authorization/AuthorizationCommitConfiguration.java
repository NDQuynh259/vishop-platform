package com.vishop.iam.infra.authorization;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;

/** Đánh thức relay sau commit; lịch 500ms là đường retry khi Redis hoặc process gặp sự cố. */
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
              var t = new Thread(r, "authorization-projection");
              t.setDaemon(true);
              return t;
            });
    var running = new AtomicBoolean();
    if (enabled && manager instanceof ConfigurableTransactionManager configurable)
      configurable.addListener(
          new TransactionExecutionListener() {
            public void afterCommit(TransactionExecution tx, Throwable failure) {
              String name = tx.getTransactionName();
              if (failure != null
                  || tx.isReadOnly()
                  || name == null
                  || !name.contains(".modules.")
                  || !running.compareAndSet(false, true)) return;
              executor.execute(
                  () -> {
                    try {
                      projection.getObject().publish();
                    } catch (RuntimeException unavailable) {
                      /* DB pending vẫn còn, lịch sẽ retry. */
                    } finally {
                      running.set(false);
                    }
                  });
            }
          });
    return executor;
  }
}
