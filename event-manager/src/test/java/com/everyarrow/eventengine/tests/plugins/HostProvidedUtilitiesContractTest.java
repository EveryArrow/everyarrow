package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.utilities.aws.KmsCryptoHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostProvidedUtilitiesContractTest {

    @Test
    void exposesKmsCryptoHelperRequiredByExternalPlugins() {
        assertTrue(KmsCryptoHelper.isEncrypted("ENC[ciphertext]"));
        assertFalse(KmsCryptoHelper.isEncrypted("plaintext"));
    }
}
