package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.pipelines.behaviors.encryption.Encryption;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionEnvelopeTest {
    @Test
    void envelopeHeaderRecordsExplicitAuthenticatedAlgorithms() {
        String header = Encryption.envelopeHeader("wrapped".getBytes(StandardCharsets.UTF_8), "nonce".getBytes(StandardCharsets.UTF_8));

        assertTrue(header.startsWith("DITTOE-ENCRYPTED-V1\n"));
        assertTrue(header.contains("keyAlgorithm=RSA/ECB/OAEPWithSHA-256AndMGF1Padding\n"));
        assertTrue(header.contains("contentAlgorithm=AES/GCM/NoPadding\n"));
        assertTrue(header.contains("nonce=" + Base64.getEncoder().encodeToString("nonce".getBytes(StandardCharsets.UTF_8))));
        assertTrue(header.endsWith("\n\n"));
    }
}
