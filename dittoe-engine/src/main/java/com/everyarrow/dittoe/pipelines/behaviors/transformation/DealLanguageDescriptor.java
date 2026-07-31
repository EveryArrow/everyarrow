package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.List;

/** Engine-owned descriptor for editor and conformance consumers. */
public record DealLanguageDescriptor(String version,
                                     List<String> literals,
                                     List<String> operators,
                                     List<FunctionDescriptor> functions,
                                     List<DealDiagnosticDescriptor> diagnostics) {
    public DealLanguageDescriptor {
        literals = List.copyOf(literals);
        operators = List.copyOf(operators);
        functions = List.copyOf(functions);
        diagnostics = List.copyOf(diagnostics);
    }
}
