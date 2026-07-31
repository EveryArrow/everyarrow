package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Set;

public interface Assignable {
    /**
     * Assigns a value to this target.
     *
     * @param value The value to assign.
     */
    void assign(ExpressionContext context, Object value);

    default Set<String> assignmentReferences() {
        return Set.of();
    }
}
