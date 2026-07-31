package com.everyarrow.dittoe.io.file.servers.s3;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.EndpointSafety;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3ServerEndpointSafetyTest {
    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void validatesConfiguredEndpointThroughSharedPolicy() throws Exception {
        assertEquals(
                "https://8.8.8.8",
                S3Server.validateEndpointOverride(Map.of("s3Endpoint", "https://8.8.8.8")).toString());
        assertThrows(
                ExecutionException.class,
                () -> S3Server.validateEndpointOverride(Map.of("s3Endpoint", "http://127.0.0.1:9000")));
    }

    @Test
    void permitsLocalEmulatorOnlyWithExplicitUnsafeFlag() throws Exception {
        Environment.put(EndpointSafety.ALLOW_UNSAFE_ENDPOINTS_CONFIG, "true");

        assertEquals(
                "http://127.0.0.1:9000",
                S3Server.validateEndpointOverride(Map.of(
                        "s3Endpoint", "http://127.0.0.1:9000",
                        EndpointSafety.ALLOW_UNSAFE_ENDPOINT, "true")).toString());
        assertNull(S3Server.validateEndpointOverride(Map.of()));
    }
}
