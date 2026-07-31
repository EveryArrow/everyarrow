package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.List;
import java.util.Arrays;

/** Versioned public contract for the embedded DEAL implementation. */
public final class DealLanguage {
    public static final String CURRENT_VERSION = "1.1";

    private static final DealLanguageDescriptor DESCRIPTOR = new DealLanguageDescriptor(
            CURRENT_VERSION,
            List.of("number", "string", "boolean", "null", "array"),
            List.of(
                    "=", "??", "||", "&&", "==", "!=", "<", "<=", ">", ">=",
                    "+", "-", "*", "/", "%", "!", "[]"),
            FunctionRegistry.descriptors(),
            Arrays.stream(DealDiagnosticCode.values())
                    .map(code -> new DealDiagnosticDescriptor(
                            code, DealDiagnosticSeverity.ERROR, code.description()))
                    .toList());

    private DealLanguage() {
    }

    public static DealLanguageDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
