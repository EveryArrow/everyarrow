package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.io.api.APIServerConnector;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServer;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServerConnector;
import com.everyarrow.dittoe.io.api.servers.salesforce.SalesforceServer;
import com.everyarrow.dittoe.io.database.DatabaseServerConnector;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServerConnector;
import com.everyarrow.dittoe.io.database.servers.mysql.MySQLServer;
import com.everyarrow.dittoe.io.database.servers.postgresql.PostgreSQLServer;
import com.everyarrow.dittoe.io.file.FileServerConnector;
import com.everyarrow.dittoe.io.file.servers.localfile.LocalFileServer;
import com.everyarrow.dittoe.io.file.servers.s3.S3Server;
import com.everyarrow.dittoe.io.stream.StreamServer;
import com.everyarrow.dittoe.io.stream.StreamServerConnector;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IOServerCapabilityTest {

    @Test
    void bareIoServerDoesNotAdvertiseOptionalMetadataCapabilities() throws ExecutionException {
        BareIOServer server = new BareIOServer();

        assertFalse(server.supportsRead());
        assertFalse(server.supportsWrite());
        assertTrue(server.getReaderOption().isEmpty());
        assertTrue(server.getWriterOption().isEmpty());
        assertFalse(server.supportsResourceCatalog());
        assertFalse(server.supportsResourceDependencies());
        assertFalse(server.supportsResourceFields());
        assertFalse(server.supportsMergeProperties());
        assertFalse(server.supportsResourcePresentation());
        assertFalse(server.supportsWizardDiscovery());
        assertFalse(server.supportsOrchestrationMetadata());

        assertThrows(UnsupportedOperationException.class, server::requireReader);
        assertThrows(UnsupportedOperationException.class, server::requireWriter);
        assertThrows(UnsupportedOperationException.class, server::getResources);
        assertThrows(UnsupportedOperationException.class, () -> server.getResourcesDependencies(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> server.getResourcesFields(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> server.getPropertiesForMerge(List.of()));
        assertThrows(UnsupportedOperationException.class, server::getResourcePresentation);
    }

    @Test
    void postgresAdvertisesDatabaseCapabilities() throws ExecutionException {
        PostgreSQLServer sourceServer = new PostgreSQLServer(true);
        PostgreSQLServer targetServer = new PostgreSQLServer(false);

        assertInstanceOf(DatabaseServerConnector.class, sourceServer);
        assertInstanceOf(JDBCServerConnector.class, sourceServer);
        assertTrue(sourceServer.supportsRead());
        assertFalse(sourceServer.supportsWrite());
        assertTrue(sourceServer.getDatabaseReaderOption().isPresent());
        assertTrue(sourceServer.getDatabaseWriterOption().isEmpty());
        assertTrue(sourceServer.getReaderOption().isPresent());
        assertTrue(sourceServer.getWriterOption().isEmpty());
        assertTrue(sourceServer.requireDatabaseReader() instanceof Reader);
        assertTrue(sourceServer.supportsResourceCatalog());
        assertTrue(sourceServer.supportsResourceDependencies());
        assertTrue(sourceServer.supportsResourceFields());
        assertTrue(sourceServer.supportsMergeProperties());
        assertTrue(sourceServer.supportsResourcePresentation());
        assertTrue(sourceServer.supportsWizardDiscovery());
        assertTrue(sourceServer.supportsOrchestrationMetadata());
        assertEquals("Table", sourceServer.getResourcePresentation().resourceNameLabel());
        assertEquals("schema", sourceServer.getResourcePresentation().resourceProperties().getFirst().key());

        assertFalse(targetServer.supportsRead());
        assertTrue(targetServer.supportsWrite());
        assertTrue(targetServer.getDatabaseReaderOption().isEmpty());
        assertTrue(targetServer.getDatabaseWriterOption().isPresent());
        assertTrue(targetServer.getReaderOption().isEmpty());
        assertTrue(targetServer.getWriterOption().isPresent());
        assertTrue(targetServer.requireDatabaseWriter() instanceof Writer);
        assertTrue(targetServer.supportsResourceCatalog());
        assertTrue(targetServer.supportsResourceDependencies());
        assertTrue(targetServer.supportsResourceFields());
        assertTrue(targetServer.supportsMergeProperties());
        assertTrue(targetServer.supportsResourcePresentation());
        assertTrue(targetServer.supportsWizardDiscovery());
        assertTrue(targetServer.supportsOrchestrationMetadata());
    }

    @Test
    void mysqlAdvertisesDatabaseAndWizardCapabilities() throws ExecutionException {
        MySQLServer sourceServer = new MySQLServer(true);
        MySQLServer targetServer = new MySQLServer(false);

        assertInstanceOf(DatabaseServerConnector.class, sourceServer);
        assertInstanceOf(JDBCServerConnector.class, sourceServer);
        assertTrue(sourceServer.supportsRead());
        assertFalse(sourceServer.supportsWrite());
        assertTrue(sourceServer.getDatabaseReaderOption().isPresent());
        assertTrue(sourceServer.getDatabaseWriterOption().isEmpty());
        assertTrue(sourceServer.supportsResourceCatalog());
        assertTrue(sourceServer.supportsResourceDependencies());
        assertTrue(sourceServer.supportsResourceFields());
        assertTrue(sourceServer.supportsMergeProperties());
        assertTrue(sourceServer.supportsResourcePresentation());
        assertTrue(sourceServer.supportsWizardDiscovery());
        assertTrue(sourceServer.supportsOrchestrationMetadata());
        assertEquals("Table", sourceServer.getResourcePresentation().resourceNameLabel());
        assertEquals("schema", sourceServer.getResourcePresentation().resourceProperties().getFirst().key());

        assertFalse(targetServer.supportsRead());
        assertTrue(targetServer.supportsWrite());
        assertTrue(targetServer.getDatabaseReaderOption().isEmpty());
        assertTrue(targetServer.getDatabaseWriterOption().isPresent());
        assertTrue(targetServer.supportsResourceCatalog());
        assertTrue(targetServer.supportsResourceDependencies());
        assertTrue(targetServer.supportsResourceFields());
        assertTrue(targetServer.supportsMergeProperties());
        assertTrue(targetServer.supportsResourcePresentation());
        assertTrue(targetServer.supportsWizardDiscovery());
        assertTrue(targetServer.supportsOrchestrationMetadata());
    }

    @Test
    void fileServersAdvertiseReadWriteAndMetadataCapabilities() throws ExecutionException {
        LocalFileServer localSource = new LocalFileServer(true);
        LocalFileServer localTarget = new LocalFileServer(false);
        S3Server s3Source = new S3Server(true);
        S3Server s3Target = new S3Server(false);

        assertInstanceOf(FileServerConnector.class, localSource);
        assertTrue(localSource.supportsRead());
        assertFalse(localSource.supportsWrite());
        assertTrue(localSource.getFileReaderOption().isPresent());
        assertTrue(localSource.getFileWriterOption().isEmpty());
        assertTrue(localSource.getReaderOption().isPresent());
        assertTrue(localSource.getWriterOption().isEmpty());
        assertTrue(localSource.requireFileReader() instanceof Reader);
        assertTrue(localSource.supportsResourceCatalog());
        assertTrue(localSource.supportsResourceDependencies());
        assertTrue(localSource.supportsResourceFields());
        assertTrue(localSource.supportsMergeProperties());
        assertTrue(localSource.supportsResourcePresentation());
        assertTrue(localSource.supportsWizardDiscovery());
        assertTrue(localSource.supportsOrchestrationMetadata());
        assertEquals("File", localSource.getResourcePresentation().resourceNameLabel());
        assertTrue(localSource.getResourcePresentation().resourceProperties().isEmpty());

        assertTrue(localTarget.supportsWrite());
        assertFalse(localTarget.supportsRead());
        assertTrue(localTarget.getFileReaderOption().isEmpty());
        assertTrue(localTarget.getFileWriterOption().isPresent());
        assertTrue(localTarget.getReaderOption().isEmpty());
        assertTrue(localTarget.getWriterOption().isPresent());
        assertTrue(s3Source.supportsRead());
        assertFalse(s3Source.supportsWrite());
        assertTrue(s3Source.getFileReaderOption().isPresent());
        assertTrue(s3Source.getFileWriterOption().isEmpty());
        assertTrue(s3Source.getReaderOption().isPresent());
        assertTrue(s3Source.getWriterOption().isEmpty());
        assertTrue(s3Source.supportsResourceCatalog());
        assertTrue(s3Source.supportsResourceDependencies());
        assertTrue(s3Source.supportsResourceFields());
        assertTrue(s3Source.supportsMergeProperties());
        assertTrue(s3Source.supportsResourcePresentation());
        assertTrue(s3Source.supportsWizardDiscovery());
        assertTrue(s3Source.supportsOrchestrationMetadata());
        assertEquals("Object", s3Source.getResourcePresentation().resourceNameLabel());
        assertEquals("bucket", s3Source.getResourcePresentation().resourceProperties().getFirst().key());
        assertTrue(s3Target.supportsWrite());
        assertFalse(s3Target.supportsRead());
        assertTrue(s3Target.getFileReaderOption().isEmpty());
        assertTrue(s3Target.getFileWriterOption().isPresent());
        assertTrue(s3Target.getReaderOption().isEmpty());
        assertTrue(s3Target.getWriterOption().isPresent());
    }

    @Test
    void apiServersAdvertiseFamilyAndBackendCapabilities() throws ExecutionException {
        BigQueryServer bigQuerySource = new BigQueryServer(true);
        BigQueryServer bigQueryTarget = new BigQueryServer(false);
        SalesforceServer salesforceSource = new SalesforceServer(true);
        SalesforceServer salesforceTarget = new SalesforceServer(false);

        assertInstanceOf(APIServerConnector.class, bigQuerySource);
        assertInstanceOf(BigQueryServerConnector.class, bigQuerySource);
        assertTrue(bigQuerySource.supportsRead());
        assertFalse(bigQuerySource.supportsWrite());
        assertTrue(bigQuerySource.getAPIReaderOption().isPresent());
        assertTrue(bigQuerySource.getAPIWriterOption().isEmpty());
        assertTrue(bigQuerySource.requireAPIReader() instanceof Reader);
        assertTrue(bigQuerySource.supportsResourceCatalog());
        assertTrue(bigQuerySource.supportsResourceDependencies());
        assertTrue(bigQuerySource.supportsResourceFields());
        assertTrue(bigQuerySource.supportsMergeProperties());
        assertTrue(bigQuerySource.supportsResourcePresentation());
        assertTrue(bigQuerySource.supportsWizardDiscovery());
        assertTrue(bigQuerySource.supportsOrchestrationMetadata());
        assertEquals("Table", bigQuerySource.getResourcePresentation().resourceNameLabel());
        assertEquals("dataset", bigQuerySource.getResourcePresentation().resourceProperties().getFirst().key());

        assertFalse(bigQueryTarget.supportsRead());
        assertTrue(bigQueryTarget.supportsWrite());
        assertTrue(bigQueryTarget.getAPIReaderOption().isEmpty());
        assertTrue(bigQueryTarget.getAPIWriterOption().isPresent());
        assertTrue(bigQueryTarget.requireAPIWriter() instanceof Writer);

        assertInstanceOf(APIServerConnector.class, salesforceSource);
        assertTrue(salesforceSource.supportsRead());
        assertFalse(salesforceSource.supportsWrite());
        assertTrue(salesforceSource.getAPIReaderOption().isPresent());
        assertTrue(salesforceSource.getAPIWriterOption().isEmpty());
        assertFalse(salesforceSource.supportsResourceCatalog());
        assertFalse(salesforceSource.supportsResourceDependencies());
        assertFalse(salesforceSource.supportsResourceFields());
        assertFalse(salesforceSource.supportsMergeProperties());
        assertFalse(salesforceSource.supportsResourcePresentation());
        assertFalse(salesforceSource.supportsWizardDiscovery());
        assertFalse(salesforceSource.supportsOrchestrationMetadata());
        assertThrows(UnsupportedOperationException.class, salesforceSource::getResources);
        assertThrows(UnsupportedOperationException.class, () -> salesforceSource.getResourcesDependencies(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> salesforceSource.getResourcesFields(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> salesforceSource.getPropertiesForMerge(List.of()));
        assertThrows(UnsupportedOperationException.class, salesforceSource::getResourcePresentation);

        assertFalse(salesforceTarget.supportsRead());
        assertTrue(salesforceTarget.supportsWrite());
        assertTrue(salesforceTarget.getAPIReaderOption().isEmpty());
        assertTrue(salesforceTarget.getAPIWriterOption().isPresent());
    }

    @Test
    void streamServersAdvertiseFamilyCapabilitiesWithoutExtraMetadata() throws ExecutionException {
        BareStreamServer server = new BareStreamServer();

        assertInstanceOf(StreamServerConnector.class, server);
        assertFalse(server.supportsRead());
        assertFalse(server.supportsWrite());
        assertTrue(server.getStreamReaderOption().isEmpty());
        assertTrue(server.getStreamWriterOption().isEmpty());
        assertThrows(UnsupportedOperationException.class, server::requireStreamReader);
        assertThrows(UnsupportedOperationException.class, server::requireStreamWriter);
        assertFalse(server.supportsResourceCatalog());
        assertFalse(server.supportsResourceDependencies());
        assertFalse(server.supportsResourceFields());
        assertFalse(server.supportsMergeProperties());
        assertFalse(server.supportsResourcePresentation());
        assertFalse(server.supportsWizardDiscovery());
        assertFalse(server.supportsOrchestrationMetadata());
    }

    private static class BareIOServer extends IOServer {
        private BareIOServer() throws ExecutionException {
            super(null, null);
        }

        @Override
        public void testConnection() {
            // no-op
        }
    }

    private static class BareStreamServer extends StreamServer {
        private BareStreamServer() throws ExecutionException {
            super(null, null);
        }

        @Override
        public void testConnection() {
            // no-op
        }
    }
}
