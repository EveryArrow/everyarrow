package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.io.file.servers.ftp.FTPServer;
import com.everyarrow.dittoe.io.file.servers.sftp.SFTPServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteTransportDefaultsTest {
    @Test
    void ftpRequiresExplicitPlaintextOptInBeforeNetworkConnection() throws ExecutionException {
        FTPServer server = new FTPServer(true);
        HashMap<String, String> details = new HashMap<>();
        details.put("ftpHost", "127.0.0.1");
        details.put("ftpUser", "user");
        details.put("ftpPassword", "password");
        server.setConnectionDetails(details);

        assertThrows(ExecutionException.class, server::init);
    }

    @Test
    void sftpRequiresHostKeyTrustBeforeNetworkConnection() throws ExecutionException {
        SFTPServer server = new SFTPServer(true);
        HashMap<String, String> details = new HashMap<>();
        details.put("sftpHost", "127.0.0.1");
        details.put("sftpPort", "22");
        details.put("sftpUser", "user");
        details.put("sftpPassword", "password");
        server.setConnectionDetails(details);

        assertThrows(ExecutionException.class, server::init);
    }
}
