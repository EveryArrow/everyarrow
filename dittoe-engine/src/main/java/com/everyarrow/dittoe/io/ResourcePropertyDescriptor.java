package com.everyarrow.dittoe.io;

/**
 * Describes a resource property that can be surfaced in discovery UIs.
 *
 * @param key             serialized key inside {@code Resource.resourceProperties}
 * @param displayName     human-readable label for UI presentation
 * @param usedForMatching whether this property should participate in default auto-matching logic
 */
public record ResourcePropertyDescriptor(String key, String displayName, boolean usedForMatching) {
}
