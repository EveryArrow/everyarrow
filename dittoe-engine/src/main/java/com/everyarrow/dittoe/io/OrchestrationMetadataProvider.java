package com.everyarrow.dittoe.io;

/**
 * Extended wizard contract required for orchestration generation.
 */
public interface OrchestrationMetadataProvider extends WizardDiscoveryProvider,
        ResourceDependencyProvider, MergePropertyProvider {
}
