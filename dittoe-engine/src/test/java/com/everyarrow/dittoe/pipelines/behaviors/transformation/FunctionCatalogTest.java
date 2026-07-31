package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionCatalogTest {
    @Test
    void publishesUniqueCanonicalFunctionsWithTypedSignatures() {
        List<FunctionDescriptor> descriptors = FunctionRegistry.descriptors();

        assertFalse(descriptors.isEmpty());
        assertEquals(descriptors.size(),
                new HashSet<>(descriptors.stream().map(FunctionDescriptor::name).toList()).size());

        FunctionDescriptor conditional = descriptors.stream()
                .filter(descriptor -> descriptor.name().equals("if"))
                .findFirst()
                .orElseThrow();
        assertEquals(FunctionCategory.CONDITIONAL, conditional.category());
        assertEquals(FunctionEvaluationStrategy.LAZY, conditional.evaluationStrategy());
        assertEquals(FunctionValueType.ANY, conditional.signatures().getFirst().resultType());
    }

    @Test
    void rejectsWrongArityAndTypesAtTheRegistryBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> FunctionRegistry.invoke("current_date", new Object[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionRegistry.invoke("date_add", new Object[]{"2026-07-19", 1}));
    }
}
