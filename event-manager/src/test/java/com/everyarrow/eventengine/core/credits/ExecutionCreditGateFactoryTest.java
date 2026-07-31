package com.everyarrow.eventengine.core.credits;

import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionCreditGateFactoryTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void defaultsToAllowingGate() {
        ExecutionCreditGate gate = ExecutionCreditGateFactory.create(Environment.getInstance());

        assertInstanceOf(AllowingExecutionCreditGate.class, gate);
    }

    @Test
    void localModeCreatesDbBackedGate() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);

        ExecutionCreditGate gate = ExecutionCreditGateFactory.create(Environment.getInstance());

        assertInstanceOf(DbExecutionCreditGate.class, gate);
    }

    @Test
    void invalidModeFailsClearly() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, "surprise");

        assertThrows(IllegalArgumentException.class,
                () -> ExecutionCreditGateFactory.create(Environment.getInstance()));
    }
}
