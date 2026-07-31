package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.io.api.APIServerConnector;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServer;
import com.everyarrow.dittoe.io.api.servers.salesforce.SalesforceServer;
import com.everyarrow.dittoe.io.database.DatabaseServerConnector;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServerConnector;
import com.everyarrow.dittoe.io.database.servers.mssql.MSSQLServer;
import com.everyarrow.dittoe.io.database.servers.mysql.MySQLServer;
import com.everyarrow.dittoe.io.database.servers.postgresql.PostgreSQLServer;
import com.everyarrow.dittoe.io.database.servers.snowflake.SnowflakeServer;
import com.everyarrow.dittoe.io.database.servers.sqlite.SQLiteServer;
import com.everyarrow.dittoe.io.file.FileServerConnector;
import com.everyarrow.dittoe.io.file.servers.azure.AzureServer;
import com.everyarrow.dittoe.io.file.servers.digitalocean.DigitalOceanServer;
import com.everyarrow.dittoe.io.file.servers.dropbox.DropboxServer;
import com.everyarrow.dittoe.io.file.servers.ftp.FTPServer;
import com.everyarrow.dittoe.io.file.servers.gcs.GCSServer;
import com.everyarrow.dittoe.io.file.servers.googledrive.GoogleDriveServer;
import com.everyarrow.dittoe.io.file.servers.ibm.IbmServer;
import com.everyarrow.dittoe.io.file.servers.localfile.LocalFileServer;
import com.everyarrow.dittoe.io.file.servers.oracle.OracleServer;
import com.everyarrow.dittoe.io.file.servers.s3.S3Server;
import com.everyarrow.dittoe.io.file.servers.sftp.SFTPServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorImplementationChecklistAuditTest {

    private static final Path MAIN_JAVA_ROOT = Path.of("src", "main", "java");
    private static final Map<Class<? extends IOServer>, ConnectorChecklistStatus> CONNECTOR_STATUS = createConnectorStatus();

    @Test
    void allConcreteEngineServersAreExplicitlyClassified() throws Exception {
        Set<String> discovered = toClassNameSet(discoverConcreteServerClasses());
        Set<String> classified = toClassNameSet(CONNECTOR_STATUS.keySet());

        Set<String> missing = new TreeSet<>(discovered);
        missing.removeAll(classified);

        Set<String> extra = new TreeSet<>(classified);
        extra.removeAll(discovered);

        assertEquals(discovered, classified,
                "Every concrete engine server must be explicitly classified.\nMissing: " + missing + "\nExtra: " + extra);
    }

    @Test
    void everyConcreteEngineServerHonorsDirectionalReadWriteContract() throws Exception {
        for (Class<? extends IOServer> connectorClass : sortedConnectorClasses(CONNECTOR_STATUS.keySet())) {
            IOServer sourceServer = instantiate(connectorClass, true);
            IOServer targetServer = instantiate(connectorClass, false);

            assertTrue(sourceServer.supportsRead(), connectorClass.getSimpleName() + " source instance must expose a reader");
            assertFalse(sourceServer.supportsWrite(), connectorClass.getSimpleName() + " source instance must not expose a writer");
            assertTrue(sourceServer.getReaderOption().isPresent(), connectorClass.getSimpleName() + " source instance must have reader metadata");
            assertTrue(sourceServer.getWriterOption().isEmpty(), connectorClass.getSimpleName() + " source instance must not have writer metadata");

            assertFalse(targetServer.supportsRead(), connectorClass.getSimpleName() + " target instance must not expose a reader");
            assertTrue(targetServer.supportsWrite(), connectorClass.getSimpleName() + " target instance must expose a writer");
            assertTrue(targetServer.getReaderOption().isEmpty(), connectorClass.getSimpleName() + " target instance must not have reader metadata");
            assertTrue(targetServer.getWriterOption().isPresent(), connectorClass.getSimpleName() + " target instance must have writer metadata");

            assertFamilyConnectorContract(sourceServer, targetServer);
        }
    }

    @Test
    void checklistCompleteServersSatisfyWizardAndOrchestrationContract() throws Exception {
        for (Class<? extends IOServer> connectorClass : connectorsWithStatus(ConnectorChecklistStatus.CHECKLIST_COMPLETE)) {
            IOServer sourceServer = instantiate(connectorClass, true);
            IOServer targetServer = instantiate(connectorClass, false);

            assertTrue(sourceServer.supportsResourcePresentation(),
                    connectorClass.getSimpleName() + " source instance must expose resource presentation metadata");
            assertTrue(targetServer.supportsResourcePresentation(),
                    connectorClass.getSimpleName() + " target instance must expose resource presentation metadata");
            assertTrue(sourceServer.supportsWizardDiscovery(),
                    connectorClass.getSimpleName() + " source instance must satisfy wizard discovery");
            assertTrue(targetServer.supportsWizardDiscovery(),
                    connectorClass.getSimpleName() + " target instance must satisfy wizard discovery");
            assertTrue(sourceServer.supportsOrchestrationMetadata(),
                    connectorClass.getSimpleName() + " source instance must satisfy orchestration metadata");
            assertTrue(targetServer.supportsOrchestrationMetadata(),
                    connectorClass.getSimpleName() + " target instance must satisfy orchestration metadata");

            ResourcePresentation sourcePresentation = sourceServer.getResourcePresentation();
            ResourcePresentation targetPresentation = targetServer.getResourcePresentation();

            assertNotNull(sourcePresentation, connectorClass.getSimpleName() + " source instance must return resource presentation");
            assertNotNull(targetPresentation, connectorClass.getSimpleName() + " target instance must return resource presentation");
            assertFalse(sourcePresentation.resourceNameLabel().isBlank(),
                    connectorClass.getSimpleName() + " source resource label must not be blank");
            assertFalse(targetPresentation.resourceNameLabel().isBlank(),
                    connectorClass.getSimpleName() + " target resource label must not be blank");
            assertFalse(hasChecklistStubMarkers(connectorClass),
                    connectorClass.getSimpleName() + " is marked checklist-complete but still contains obvious stub markers");
        }
    }

    @Test
    void legacyGapServersRemainExplicitlyOutsideChecklistCompleteBucket() throws Exception {
        for (Class<? extends IOServer> connectorClass : connectorsWithStatus(ConnectorChecklistStatus.LEGACY_GAP)) {
            assertFalse(meetsChecklistContract(connectorClass),
                    connectorClass.getSimpleName() + " now appears checklist-complete. Promote it in the audit map once reviewed.");
        }
    }

    @Test
    void concreteEngineServerSourcesDoNotContainGenericStubMarkers() throws Exception {
        for (Class<? extends IOServer> connectorClass : sortedConnectorClasses(CONNECTOR_STATUS.keySet())) {
            assertFalse(hasChecklistStubMarkers(connectorClass),
                    connectorClass.getSimpleName() + " must not contain generic NotImplemented placeholder markers.");
        }
    }

    private static Map<Class<? extends IOServer>, ConnectorChecklistStatus> createConnectorStatus() {
        Map<Class<? extends IOServer>, ConnectorChecklistStatus> connectors = new LinkedHashMap<>();

        connectors.put(BigQueryServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(SalesforceServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(MSSQLServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(MySQLServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(PostgreSQLServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(SnowflakeServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(SQLiteServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(AzureServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(DigitalOceanServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(DropboxServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(FTPServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(GCSServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(GoogleDriveServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(IbmServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(LocalFileServer.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(OracleServer.class, ConnectorChecklistStatus.LEGACY_GAP);
        connectors.put(S3Server.class, ConnectorChecklistStatus.CHECKLIST_COMPLETE);
        connectors.put(SFTPServer.class, ConnectorChecklistStatus.LEGACY_GAP);

        return Map.copyOf(connectors);
    }

    private static List<Class<? extends IOServer>> connectorsWithStatus(ConnectorChecklistStatus status) {
        return CONNECTOR_STATUS.entrySet().stream()
                .filter(entry -> entry.getValue() == status)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends IOServer>> discoverConcreteServerClasses() throws Exception {
        Path ioRoot = MAIN_JAVA_ROOT.resolve(Path.of("com", "everyarrow", "dittoe", "io"));

        try (Stream<Path> paths = Files.walk(ioRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Server.java"))
                    .filter(path -> path.toString().contains(Path.of("servers").toString()))
                    .map(ConnectorImplementationChecklistAuditTest::loadClass)
                    .filter(IOServer.class::isAssignableFrom)
                    .map(clazz -> (Class<? extends IOServer>) clazz)
                    .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                    .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::getName))));
        }
    }

    private static Class<?> loadClass(Path sourceFile) {
        Path relativeSourceFile = MAIN_JAVA_ROOT.relativize(sourceFile);
        String className = relativeSourceFile.toString()
                .replace('\\', '.')
                .replace('/', '.')
                .replace(".java", "");

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load connector class " + className, e);
        }
    }

    private static IOServer instantiate(Class<? extends IOServer> connectorClass, boolean isSource) throws Exception {
        Constructor<? extends IOServer> constructor = connectorClass.getDeclaredConstructor(Boolean.class);
        return constructor.newInstance(isSource);
    }

    private static void assertFamilyConnectorContract(IOServer sourceServer, IOServer targetServer) {
        assertInstanceOf(ServerConnector.class, sourceServer);
        assertInstanceOf(ServerConnector.class, targetServer);

        if (sourceServer instanceof com.everyarrow.dittoe.io.file.FileServer) {
            assertInstanceOf(FileServerConnector.class, sourceServer);
            assertInstanceOf(FileServerConnector.class, targetServer);
        }

        if (sourceServer instanceof com.everyarrow.dittoe.io.database.DatabaseServer) {
            assertInstanceOf(DatabaseServerConnector.class, sourceServer);
            assertInstanceOf(DatabaseServerConnector.class, targetServer);
        }

        if (sourceServer instanceof com.everyarrow.dittoe.io.api.APIServer) {
            assertInstanceOf(APIServerConnector.class, sourceServer);
            assertInstanceOf(APIServerConnector.class, targetServer);
        }

        if (sourceServer instanceof JDBCServer) {
            assertInstanceOf(JDBCServerConnector.class, sourceServer);
            assertInstanceOf(JDBCServerConnector.class, targetServer);
        }
    }

    private static boolean meetsChecklistContract(Class<? extends IOServer> connectorClass) throws Exception {
        IOServer sourceServer = instantiate(connectorClass, true);
        IOServer targetServer = instantiate(connectorClass, false);

        return sourceServer.supportsRead()
                && !sourceServer.supportsWrite()
                && sourceServer.getReaderOption().isPresent()
                && sourceServer.getWriterOption().isEmpty()
                && !targetServer.supportsRead()
                && targetServer.supportsWrite()
                && targetServer.getReaderOption().isEmpty()
                && targetServer.getWriterOption().isPresent()
                && sourceServer.supportsResourcePresentation()
                && targetServer.supportsResourcePresentation()
                && sourceServer.supportsWizardDiscovery()
                && targetServer.supportsWizardDiscovery()
                && sourceServer.supportsOrchestrationMetadata()
                && targetServer.supportsOrchestrationMetadata()
                && !sourceServer.getResourcePresentation().resourceNameLabel().isBlank()
                && !targetServer.getResourcePresentation().resourceNameLabel().isBlank()
                && !hasChecklistStubMarkers(connectorClass);
    }

    private static boolean hasChecklistStubMarkers(Class<? extends IOServer> connectorClass) throws IOException {
        String source = Files.readString(sourceFileFor(connectorClass));

        return source.contains("NotImplementedException")
                || source.contains("This method hasn't been implemented.")
                || source.contains("is not implemented for")
                || source.contains("not implemented for");
    }

    private static Path sourceFileFor(Class<? extends IOServer> connectorClass) {
        return MAIN_JAVA_ROOT.resolve(connectorClass.getName().replace('.', '/') + ".java");
    }

    private static List<Class<? extends IOServer>> sortedConnectorClasses(Set<Class<? extends IOServer>> connectorClasses) {
        return connectorClasses.stream()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    private static Set<String> toClassNameSet(Set<Class<? extends IOServer>> connectorClasses) {
        return connectorClasses.stream()
                .map(Class::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private enum ConnectorChecklistStatus {
        CHECKLIST_COMPLETE,
        LEGACY_GAP
    }
}
