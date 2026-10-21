package io.github.coreplatform.common.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.saga.persistence.SagaInstanceEntity;
import io.github.coreplatform.common.saga.persistence.SagaInstanceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SagaOrchestratorTest {

    private SagaInstanceRepository repository;
    private SagaOrchestrator orchestrator;

    private static class OrderContext {
        public boolean inventoryReserved = false;
        public boolean paymentProcessed = false;
        public boolean orderConfirmed = false;
    }

    // #region setUp
    @BeforeEach
    void setUp() {
        repository = mock(SagaInstanceRepository.class);
        when(repository.saveAndFlush(any(SagaInstanceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        orchestrator = new SagaOrchestrator(repository, new ObjectMapper());
    }
    // #endregion

    // #region shouldExecuteAllStepsSuccessfully
    @Test
    void shouldExecuteAllStepsSuccessfully() {
        OrderContext context = new OrderContext();

        SagaStep<OrderContext> step1 = new SagaStep<>() {
            @Override public String name() { return "ReserveInventory"; }
            @Override public void execute(OrderContext ctx) { ctx.inventoryReserved = true; }
            @Override public void compensate(OrderContext ctx) { ctx.inventoryReserved = false; }
        };

        SagaStep<OrderContext> step2 = new SagaStep<>() {
            @Override public String name() { return "ProcessPayment"; }
            @Override public void execute(OrderContext ctx) { ctx.paymentProcessed = true; }
            @Override public void compensate(OrderContext ctx) { ctx.paymentProcessed = false; }
        };

        SagaStep<OrderContext> step3 = new SagaStep<>() {
            @Override public String name() { return "ConfirmOrder"; }
            @Override public void execute(OrderContext ctx) { ctx.orderConfirmed = true; }
            @Override public void compensate(OrderContext ctx) { ctx.orderConfirmed = false; }
        };

        SagaDefinition<OrderContext> sagaDef = new SagaDefinition<>() {
            @Override public String sagaType() { return "CreateOrderSaga"; }
            @Override public List<SagaStep<OrderContext>> steps() { return List.of(step1, step2, step3); }
        };

        OrderContext result = orchestrator.execute(sagaDef, context, UUID.randomUUID());

        assertTrue(result.inventoryReserved);
        assertTrue(result.paymentProcessed);
        assertTrue(result.orderConfirmed);
    }
    // #endregion

    // #region shouldCompensateInReverseOrderWhenStepFails
    @Test
    void shouldCompensateInReverseOrderWhenStepFails() {
        OrderContext context = new OrderContext();
        List<String> executionLog = new ArrayList<>();

        SagaStep<OrderContext> step1 = new SagaStep<>() {
            @Override public String name() { return "ReserveInventory"; }
            @Override public void execute(OrderContext ctx) {
                executionLog.add("EXECUTE_STEP1");
                ctx.inventoryReserved = true;
            }
            @Override public void compensate(OrderContext ctx) {
                executionLog.add("COMPENSATE_STEP1");
                ctx.inventoryReserved = false;
            }
        };

        SagaStep<OrderContext> step2 = new SagaStep<>() {
            @Override public String name() { return "ProcessPayment"; }
            @Override public void execute(OrderContext ctx) throws Exception {
                executionLog.add("EXECUTE_STEP2");
                throw new RuntimeException("Payment gateway declined transaction");
            }
            @Override public void compensate(OrderContext ctx) {
                executionLog.add("COMPENSATE_STEP2");
            }
        };

        SagaDefinition<OrderContext> sagaDef = new SagaDefinition<>() {
            @Override public String sagaType() { return "CreateOrderSaga"; }
            @Override public List<SagaStep<OrderContext>> steps() { return List.of(step1, step2); }
        };

        SagaException exception = assertThrows(SagaException.class, () ->
                orchestrator.execute(sagaDef, context, UUID.randomUUID())
        );

        assertTrue(exception.isCompensationSuccessful());
        assertEquals("ProcessPayment", exception.getStepName());

        // Verify inventory was rolled back
        assertEquals(false, context.inventoryReserved);

        // Verify sequence: Step1 executed, Step2 failed, Step1 compensated
        assertEquals(List.of("EXECUTE_STEP1", "EXECUTE_STEP2", "COMPENSATE_STEP1"), executionLog);
    }
    // #endregion
}
