package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.List;

/** Minimal cross-runtime fixtures for the public DEAL 1.1 contract. */
public final class DealConformanceFixtures {
    private static final List<DealConformanceCase> CASES = List.of(
            new DealConformanceCase("arithmetic-precedence", "1 + 2 * 3", 7),
            new DealConformanceCase("comparison-and-logic", "2 < 3 && 3 <= 3", true),
            new DealConformanceCase("null-coalescing", "null ?? \"fallback\"", "fallback"),
            new DealConformanceCase("lazy-conditional", "if(true,\"selected\",1 / 0)", "selected"),
            new DealConformanceCase("explicit-integer-conversion", "to_integer(\"42\")", 42),
            new DealConformanceCase("escaped-string", "\"line\\nvalue\"", "line\nvalue"));

    private DealConformanceFixtures() {
    }

    public static List<DealConformanceCase> cases() {
        return CASES;
    }
}
