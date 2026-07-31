package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A typed DEAL function signature with an optional repeating argument group.
 * This models fixed, overloaded, variadic, and condition/value-pair functions.
 */
public record FunctionSignature(List<FunctionValueType> prefix,
                                List<FunctionValueType> repeating,
                                int minimumRepetitions,
                                int maximumRepetitions,
                                List<FunctionValueType> suffix,
                                FunctionValueType resultType) {
    public static final int UNBOUNDED = -1;

    public FunctionSignature {
        prefix = List.copyOf(prefix);
        repeating = List.copyOf(repeating);
        suffix = List.copyOf(suffix);
        if (minimumRepetitions < 0) {
            throw new IllegalArgumentException("minimumRepetitions must not be negative");
        }
        if (maximumRepetitions != UNBOUNDED && maximumRepetitions < minimumRepetitions) {
            throw new IllegalArgumentException("maximumRepetitions must be unbounded or at least the minimum");
        }
        if (repeating.isEmpty() && (minimumRepetitions != 0 || maximumRepetitions != 0)) {
            throw new IllegalArgumentException("A signature without a repeating group cannot repeat");
        }
    }

    public static FunctionSignature fixed(FunctionValueType resultType, FunctionValueType... arguments) {
        return new FunctionSignature(List.of(arguments), List.of(), 0, 0, List.of(), resultType);
    }

    public static FunctionSignature variadic(FunctionValueType resultType,
                                             FunctionValueType argumentType,
                                             int minimumArguments) {
        return new FunctionSignature(
                List.of(), List.of(argumentType), minimumArguments, UNBOUNDED, List.of(), resultType);
    }

    public static FunctionSignature repeating(FunctionValueType resultType,
                                              List<FunctionValueType> repeating,
                                              int minimumRepetitions,
                                              List<FunctionValueType> suffix) {
        return new FunctionSignature(
                List.of(), repeating, minimumRepetitions, UNBOUNDED, suffix, resultType);
    }

    Optional<List<FunctionValueType>> argumentTypes(int argumentCount) {
        int repeatingArgumentCount = argumentCount - prefix.size() - suffix.size();
        if (repeatingArgumentCount < 0) {
            return Optional.empty();
        }
        if (repeating.isEmpty()) {
            if (repeatingArgumentCount != 0) {
                return Optional.empty();
            }
            List<FunctionValueType> result = new ArrayList<>(argumentCount);
            result.addAll(prefix);
            result.addAll(suffix);
            return Optional.of(List.copyOf(result));
        }
        if (repeatingArgumentCount % repeating.size() != 0) {
            return Optional.empty();
        }

        int repetitions = repeatingArgumentCount / repeating.size();
        if (repetitions < minimumRepetitions
                || (maximumRepetitions != UNBOUNDED && repetitions > maximumRepetitions)) {
            return Optional.empty();
        }

        List<FunctionValueType> result = new ArrayList<>(argumentCount);
        result.addAll(prefix);
        for (int i = 0; i < repetitions; i++) {
            result.addAll(repeating);
        }
        result.addAll(suffix);
        return Optional.of(List.copyOf(result));
    }
}
