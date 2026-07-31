package com.everyarrow.dittoe.utility;

/**
 * Represents a chunk of indices.
 */
public record Chunk(int startIndex, int endIndex) {

    @Override
    public String toString() {
        return "Chunk{" +
                "startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }
}
