package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** Zero-based, end-exclusive character range in a DEAL expression. */
public record DealSourceSpan(int start, int end) {
    private static final DealSourceSpan UNKNOWN = new DealSourceSpan(-1, -1);

    public DealSourceSpan {
        boolean unknown = start == -1 && end == -1;
        if (!unknown && (start < 0 || end < start)) {
            throw new IllegalArgumentException("A DEAL source span requires 0 <= start <= end");
        }
    }

    public static DealSourceSpan unknown() {
        return UNKNOWN;
    }

    public static DealSourceSpan covering(DealSourceSpan first, DealSourceSpan second) {
        if (!first.known()) {
            return second;
        }
        if (!second.known()) {
            return first;
        }
        return new DealSourceSpan(Math.min(first.start, second.start), Math.max(first.end, second.end));
    }

    public boolean known() {
        return start >= 0;
    }
}
