package io.github.coreplatform.common.web.cqrs;

import io.github.coreplatform.common.domain.cqrs.Command;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Spring-managed CommandBus implementation that automatically discovers and routes commands to
 * their corresponding {@link CommandHandler}.
 */
@Component
public class SpringCommandBus implements CommandBus {

  private final ApplicationContext applicationContext;
  private final Map<Class<?>, CommandHandler<?, ?>> handlerRegistry = new ConcurrentHashMap<>();

  // #region SpringCommandBus
  public SpringCommandBus(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  // #endregion

  // #region registerHandlers
  @PostConstruct
  public void registerHandlers() {
    Map<String, CommandHandler> beans = applicationContext.getBeansOfType(CommandHandler.class);
    for (CommandHandler<?, ?> handler : beans.values()) {
      Class<?> targetClass = AopUtils.getTargetClass(handler);
      ResolvableType resolvableType = ResolvableType.forClass(targetClass).as(CommandHandler.class);
      Class<?> commandType = resolvableType.getGeneric(0).resolve();
      if (commandType != null) {
        handlerRegistry.put(commandType, handler);
      }
    }
  }

  // #endregion

  // #region dispatch
  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatch(Command<R> command) {
    if (command == null) {
      throw new IllegalArgumentException("Command cannot be null");
    }
    CommandHandler<Command<R>, R> handler =
        (CommandHandler<Command<R>, R>) handlerRegistry.get(command.getClass());
    if (handler == null) {
      // Lazy lookup in case handlers were registered dynamically
      handler = (CommandHandler<Command<R>, R>) findHandler(command.getClass());
    }
    if (handler == null) {
      throw new IllegalStateException(
          "No CommandHandler registered for command: " + command.getClass().getName());
    }
    return handler.handle(command);
  }

  // #endregion

  // #region findHandler
  private CommandHandler<?, ?> findHandler(Class<?> commandClass) {
    Map<String, CommandHandler> beans = applicationContext.getBeansOfType(CommandHandler.class);
    for (CommandHandler<?, ?> handler : beans.values()) {
      Class<?> targetClass = AopUtils.getTargetClass(handler);
      ResolvableType resolvableType = ResolvableType.forClass(targetClass).as(CommandHandler.class);
      Class<?> targetCommand = resolvableType.getGeneric(0).resolve();
      if (commandClass.equals(targetCommand)) {
        handlerRegistry.put(commandClass, handler);
        return handler;
      }
    }
    return null;
  }
  // #endregion
}
