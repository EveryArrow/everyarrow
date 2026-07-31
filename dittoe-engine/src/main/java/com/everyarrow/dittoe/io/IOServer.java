package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.Server;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An abstract base class representing an I/O server that supports both reading and writing operations,
 * integrated with a retry mechanism for handling transient failures.
 * <p>
 * This class extends {@link Server} and implements {@link AutoCloseable}, ensuring that any resources
 * held by an instance can be properly released. The I/O server is constructed with specific implementations
 * for reading (via a {@link Reader}) and writing (via a {@link Writer}). It also initializes a default
 * {@link Retry} mechanism, which can be used to automatically re-attempt failed operations.
 * <p>
 * Concrete subclasses are responsible for lifecycle and connectivity. Resource catalog and metadata
 * operations are exposed as optional capabilities so partially implemented connectors do not have to
 * advertise unsupported behavior.
 */
public abstract class IOServer extends Server implements AutoCloseable, ServerConnector {
    private final Reader reader;
    private final Writer writer;
    private final Retry retry;
    private Resource resource;

    public IOServer(Reader reader, Writer writer) throws ExecutionException {
        this.reader = reader;
        this.writer = writer;

        try {
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExecutionException("Failed to initialize retry mechanism", e);
        }
    }

    public void init() throws ExecutionException {
        // Default implementation is empty.
    }

    public Retry getRetry() {
        return retry;
    }

    public Reader getReader() {
        return reader;
    }

    public Writer getWriter() {
        return writer;
    }

    @Override
    public Optional<Reader> getReaderOption() {
        return Optional.ofNullable(reader);
    }

    @Override
    public Optional<Writer> getWriterOption() {
        return Optional.ofNullable(writer);
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public List<Resource> getResources() {
        if (this instanceof ResourceCatalog resourceCatalog) {
            return resourceCatalog.getResources();
        }

        throw unsupportedCapability("resource catalog");
    }

    public abstract void testConnection();

    @Override
    public void close() throws Exception {
        // Default implementation can be overridden by subclasses to perform cleanup.
    }

    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        if (this instanceof ResourceDependencyProvider resourceDependencyProvider) {
            return resourceDependencyProvider.getResourcesDependencies(resources);
        }

        throw unsupportedCapability("resource dependency metadata");
    }

    public Map<String, List<Field>> getResourcesFields(List<Resource> resources) {
        if (this instanceof ResourceFieldProvider resourceFieldProvider) {
            return resourceFieldProvider.getResourcesFields(resources);
        }

        throw unsupportedCapability("resource field metadata");
    }

    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        if (this instanceof MergePropertyProvider mergePropertyProvider) {
            return mergePropertyProvider.getPropertiesForMerge(resources);
        }

        throw unsupportedCapability("merge properties");
    }

    public ResourcePresentation getResourcePresentation() {
        if (this instanceof ResourcePresentationProvider resourcePresentationProvider) {
            return resourcePresentationProvider.getResourcePresentation();
        }

        throw unsupportedCapability("resource presentation metadata");
    }

    public boolean supportsResourceCatalog() {
        return this instanceof ResourceCatalog;
    }

    public boolean supportsResourceDependencies() {
        return this instanceof ResourceDependencyProvider;
    }

    public boolean supportsResourceFields() {
        return this instanceof ResourceFieldProvider;
    }

    public boolean supportsMergeProperties() {
        return this instanceof MergePropertyProvider;
    }

    public boolean supportsResourcePresentation() {
        return this instanceof ResourcePresentationProvider;
    }

    public boolean supportsWizardDiscovery() {
        return this instanceof WizardDiscoveryProvider;
    }

    public boolean supportsOrchestrationMetadata() {
        return this instanceof OrchestrationMetadataProvider;
    }

    private UnsupportedOperationException unsupportedCapability(String capabilityName) {
        return new UnsupportedOperationException(
                "Server " + this.getClass().getSimpleName() + " does not support " + capabilityName);
    }
}
