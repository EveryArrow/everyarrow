package com.everyarrow.dittoe.utility.security;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointSafetyTest {
    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void allowsPublicHttpsEndpoint() throws ExecutionException {
        assertEquals("https://s3.example.com",
                checked("https://s3.example.com", "93.184.216.34").toString());
    }

    @Test
    void rejectsPlainHttpAndLocalhostByDefault() {
        assertThrows(ExecutionException.class,
                () -> checked("http://s3.example.com", "93.184.216.34"));
        assertThrows(ExecutionException.class,
                () -> checked("https://127.0.0.1:9000", "127.0.0.1"));
    }

    @Test
    void callerFlagAloneCannotEnableUnsafeEndpoint() {
        assertThrows(ExecutionException.class,
                () -> EndpointSafety.requirePublicHttpsEndpoint("http://127.0.0.1:9000",
                        Map.of(EndpointSafety.ALLOW_UNSAFE_ENDPOINT, "true")));
    }

    @Test
    void allowsUnsafeEndpointOnlyWithCallerAndOperatorOptIn() throws ExecutionException {
        Environment.put(EndpointSafety.ALLOW_UNSAFE_ENDPOINTS_CONFIG, "true");

        assertEquals("http://127.0.0.1:9000",
                EndpointSafety.requirePublicHttpsEndpoint("http://127.0.0.1:9000",
                        Map.of(EndpointSafety.ALLOW_UNSAFE_ENDPOINT, "true")).toString());
    }

    @Test
    void rejectsPrivateDnsAnswersAndMetadataHosts() {
        assertThrows(ExecutionException.class,
                () -> checked("https://storage.example.com", "10.0.0.8"));
        assertThrows(ExecutionException.class,
                () -> checked("https://storage.example.com", "169.254.169.254"));
        assertThrows(ExecutionException.class,
                () -> checked("https://metadata.google.internal", "93.184.216.34"));
    }

    @Test
    void rejectsIpv6LocalAndDocumentationAddresses() {
        assertThrows(ExecutionException.class,
                () -> checked("https://[fc00::1]", "fc00::1"));
        assertThrows(ExecutionException.class,
                () -> checked("https://[fe80::1]", "fe80::1"));
        assertThrows(ExecutionException.class,
                () -> checked("https://[2001:db8::1]", "2001:db8::1"));
    }

    @Test
    void rejectsAnyUnsafeDnsAnswer() {
        assertThrows(ExecutionException.class,
                () -> EndpointSafety.requirePublicHttpsEndpoint(
                        "https://storage.example.com",
                        Map.of(),
                        host -> new InetAddress[]{
                                InetAddress.getByName("93.184.216.34"),
                                InetAddress.getByName("127.0.0.1")
                        }));
    }

    @Test
    void rejectsUnsupportedSchemesAndEmbeddedCredentialsEvenWithUnsafeOptIn() {
        Environment.put(EndpointSafety.ALLOW_UNSAFE_ENDPOINTS_CONFIG, "true");
        Map<String, String> properties = Map.of(EndpointSafety.ALLOW_UNSAFE_ENDPOINT, "true");

        assertThrows(ExecutionException.class,
                () -> EndpointSafety.requirePublicHttpsEndpoint("file:///etc/passwd", properties));
        assertThrows(ExecutionException.class,
                () -> EndpointSafety.requirePublicHttpsEndpoint("http://user:secret@127.0.0.1:9000", properties));
    }

    private static java.net.URI checked(String endpoint, String... addresses) throws ExecutionException {
        return EndpointSafety.requirePublicHttpsEndpoint(
                endpoint,
                Map.of(),
                host -> {
                    InetAddress[] resolved = new InetAddress[addresses.length];
                    for (int index = 0; index < addresses.length; index++) {
                        resolved[index] = InetAddress.getByName(addresses[index]);
                    }
                    return resolved;
                });
    }
}
