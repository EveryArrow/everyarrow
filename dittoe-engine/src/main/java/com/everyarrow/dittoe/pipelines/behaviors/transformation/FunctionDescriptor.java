package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, UI-consumable description of one engine-supported DEAL function. */
public record FunctionDescriptor(String name,
                                 Set<String> aliases,
                                 FunctionCategory category,
                                 String description,
                                 String syntax,
                                 List<FunctionSignature> signatures,
                                 FunctionEvaluationStrategy evaluationStrategy) {
    public FunctionDescriptor {
        Objects.requireNonNull(name, "name");
        aliases = Set.copyOf(aliases);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(syntax, "syntax");
        signatures = List.copyOf(signatures);
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("A DEAL function requires at least one signature");
        }
        Objects.requireNonNull(evaluationStrategy, "evaluationStrategy");
    }

    FunctionSignature requireSignature(int argumentCount) {
        return signatures.stream()
                .filter(signature -> signature.argumentTypes(argumentCount).isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid argument count " + argumentCount + " for DEAL function '" + name
                                + "'; expected " + syntax));
    }
}
