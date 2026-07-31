package com.everyarrow.eventengine.core.credits;

import com.everyarrow.utilities.environment.Environment;

import java.util.Locale;

/**
 * Chooses the execution-credit gate for this runtime.
 */
public final class ExecutionCreditGateFactory {
    public static final String MODE_CONFIG = "EA_EXECUTION_CREDITS_MODE";
    public static final String MODE_ALLOWING = "allowing";
    public static final String MODE_LOCAL = "local";

    private ExecutionCreditGateFactory() {
    }

    public static ExecutionCreditGate create() {
        return create(Environment.getInstance());
    }

    static ExecutionCreditGate create(Environment environment) {
        Environment env = environment == null ? Environment.getInstance() : environment;
        String mode = env.get(MODE_CONFIG, MODE_ALLOWING);
        String normalized = mode == null || mode.isBlank()
                ? MODE_ALLOWING
                : mode.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case MODE_ALLOWING -> new AllowingExecutionCreditGate(new DefaultExecutionCreditPolicy());
            case MODE_LOCAL -> new DbExecutionCreditGate(
                    new DefaultExecutionCreditPolicy(),
                    new ExecutionCreditDbGateway());
            default -> throw new IllegalArgumentException(
                    "Unsupported " + MODE_CONFIG + " value: " + mode
                            + ". Expected '" + MODE_ALLOWING + "' or '" + MODE_LOCAL + "'.");
        };
    }
}
