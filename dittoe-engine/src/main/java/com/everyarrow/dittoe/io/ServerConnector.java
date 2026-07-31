package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.retry.Retry;
import org.everyarrow.dittoe.schema.Resource;

import java.util.Map;
import java.util.Optional;

/**
 * Shared operational contract for all server connectors.
 */
public interface ServerConnector extends AutoCloseable {
    Retry getRetry();

    Resource getResource();

    void setResource(Resource resource);

    Map<String, String> getConnectionDetails();

    void init() throws ExecutionException;

    void testConnection();

    default Optional<Reader> getReaderOption() {
        return Optional.empty();
    }

    default Optional<Writer> getWriterOption() {
        return Optional.empty();
    }

    default Reader requireReader() {
        return getReaderOption().orElseThrow(() ->
                new UnsupportedOperationException("Read operations are not supported by this server instance"));
    }

    default Writer requireWriter() {
        return getWriterOption().orElseThrow(() ->
                new UnsupportedOperationException("Write operations are not supported by this server instance"));
    }

    default boolean supportsRead() {
        return getReaderOption().isPresent();
    }

    default boolean supportsWrite() {
        return getWriterOption().isPresent();
    }
}
