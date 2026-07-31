package com.everyarrow.dittoe.io.file.servers.gcs;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.EndpointSafety;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCSServerEndpointSafetyTest {
    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void validatesConfiguredEndpointThroughSharedPolicy() throws Exception {
        assertEquals(
                "https://8.8.8.8",
                GCSServer.validateEndpointOverride(Map.of("gcsEndpoint", "https://8.8.8.8")));
        assertThrows(
                ExecutionException.class,
                () -> GCSServer.validateEndpointOverride(Map.of("gcsEndpoint", "http://169.254.169.254")));
    }

    @Test
    void permitsLocalEmulatorOnlyWithExplicitUnsafeFlag() throws Exception {
        Environment.put(EndpointSafety.ALLOW_UNSAFE_ENDPOINTS_CONFIG, "true");

        assertEquals(
                "http://127.0.0.1:4443",
                GCSServer.validateEndpointOverride(Map.of(
                        "gcsEndpoint", "http://127.0.0.1:4443",
                        EndpointSafety.ALLOW_UNSAFE_ENDPOINT, "true")));
        assertNull(GCSServer.validateEndpointOverride(Map.of()));
    }
}
