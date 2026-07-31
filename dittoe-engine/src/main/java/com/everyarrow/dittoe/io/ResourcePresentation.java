package com.everyarrow.dittoe.io;

import java.util.List;

/**
 * Presentation metadata for browsing a connector's resources in setup wizards.
 *
 * @param resourceNameLabel label used for the primary resource name column
 * @param resourceProperties additional resource properties that should be surfaced in discovery UIs
 */
public record ResourcePresentation(String resourceNameLabel, List<ResourcePropertyDescriptor> resourceProperties) {
    public ResourcePresentation {
        resourceNameLabel = resourceNameLabel == null || resourceNameLabel.isBlank() ? "Resource" : resourceNameLabel;
        resourceProperties = resourceProperties == null ? List.of() : List.copyOf(resourceProperties);
    }
}
