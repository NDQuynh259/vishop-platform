package io.github.coreplatform.common.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.saga.persistence.SagaInstanceEntity;
import io.github.coreplatform.common.saga.persistence.SagaInstanceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core engine for executing Saga workflows with automated forward execution,
 * state persistence, and backward compensation on failure.
 */
@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagaRepository;
    private final ObjectMapper objectMapper;

    public SagaOrchestrator(SagaInstanceRepository sagaRepository, ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes an entire saga definition synchronously in sequence.
     * If any step fails, triggers compensating transactions in reverse order.
     *
     * @param sagaDefinition definition containing ordered steps
     * @param context shared data context
     * @param correlationId tracing/business correlation ID
     * @param <T> context type
     * @return completed context
     */
    public <T> T execute(SagaDefinition<T> sagaDefinition, T context, UUID correlationId) {
        UUID sagaId = UuidV7.nextId();
        String sagaType = sagaDefinition.sagaType();
        List<SagaStep<T>> steps = sagaDefinition.steps();

        log.info("Starting Saga [{}] with ID [{}] and correlation [{}]", sagaType, sagaId, correlationId);

        String initialJson = serialize(context);
        SagaInstanceEntity instance = new SagaInstanceEntity(
                sagaId, sagaType, correlationId, SagaStatus.STARTED, 0,
                steps.isEmpty() ? "none" : steps.get(0).name(),
                initialJson, Instant.now()
        );
        saveInstance(instance);

        int executedStepsCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            SagaStep<T> step = steps.get(i);
            String stepName = step.name();

            instance.updateProgress(i, stepName, SagaStatus.IN_PROGRESS, serialize(context));
            saveInstance(instance);

            log.debug("Executing Saga [{}] step [{}/{}: {}]", sagaType, i + 1, steps.size(), stepName);

            try {
                step.execute(context);
                executedStepsCount++;
            } catch (Exception ex) {
                log.error("Saga [{}] failed at step [{}: {}]: {}", sagaType, i, stepName, ex.getMessage(), ex);
                boolean compensationOk = compensate(sagaDefinition, context, instance, executedStepsCount - 1, ex);
                throw new SagaException(sagaType, stepName, ex.getMessage(), ex, compensationOk);
            }
        }

        instance.markCompleted(serialize(context));
        saveInstance(instance);
        log.info("Saga [{}] ID [{}] successfully completed all [{}] steps", sagaType, sagaId, steps.size());

        return context;
    }

    private <T> boolean compensate(SagaDefinition<T> sagaDefinition, T context,
                                   SagaInstanceEntity instance, int fromStepIndex, Exception cause) {
        List<SagaStep<T>> steps = sagaDefinition.steps();
        String sagaType = sagaDefinition.sagaType();

        instance.markCompensating(cause.getMessage());
        saveInstance(instance);

        log.warn("Initiating compensation for Saga [{}] from step index [{}] backward", sagaType, fromStepIndex);

        boolean allCompensationsSucceeded = true;

        for (int i = fromStepIndex; i >= 0; i--) {
            SagaStep<T> step = steps.get(i);
            String stepName = step.name();
            log.info("Compensating Saga [{}] step [{}: {}]", sagaType, i, stepName);

            try {
                step.compensate(context);
            } catch (Exception compEx) {
                log.error("CRITICAL: Failed to compensate Saga [{}] step [{}: {}]: {}",
                        sagaType, i, stepName, compEx.getMessage(), compEx);
                allCompensationsSucceeded = false;
            }
        }

        if (allCompensationsSucceeded) {
            instance.markCompensated();
            saveInstance(instance);
            log.info("Saga [{}] successfully compensated all executed steps", sagaType);
        } else {
            instance.markFailed("One or more compensating actions failed. Manual intervention required.");
            saveInstance(instance);
            log.error("Saga [{}] compensation FAILED. Manual intervention required.", sagaType);
        }

        return allCompensationsSucceeded;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveInstance(SagaInstanceEntity entity) {
        try {
            sagaRepository.saveAndFlush(entity);
        } catch (Exception e) {
            log.error("Failed to persist SagaInstanceEntity state [{}]", entity.getId(), e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
