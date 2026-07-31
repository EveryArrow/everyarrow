package com.everyarrow.dittoe.io.file.servers.azure.reader.strategy;

import com.azure.storage.blob.BlobClient;
import com.everyarrow.dittoe.io.file.servers.azure.AzureServer;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.azure.reader.AzureReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Concrete CSV read strategy for Azure Blob Storage.
 * <p>
 * Reads a CSV file from an Azure blob and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String BLOB_PROPERTY = "azureBlob";

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void read() throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    public void read(int limit) throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties(), limit);
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        return CsvUtils.getRange(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public long getDataCount() throws ExecutionException {
        return CsvUtils.getDataCount(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    private Reader getFileReader() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String blobName = props.get(BLOB_PROPERTY);
            if (blobName == null) {
                throw new ExecutionException("Azure blob name not provided in resource properties.");
            }
            BlobClient blobClient = ((AzureServer) this.getReader().getServer())
                    .getContainerClient().getBlobClient(blobName);
            if (!blobClient.exists()) {
                throw new ExecutionException("Blob " + blobName + " does not exist.");
            }
            return new BufferedReader(new InputStreamReader(blobClient.openInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ExecutionException("Failed to get file reader from Azure Blob Storage", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String blobName = props.get(BLOB_PROPERTY);
            if (blobName == null) {
                throw new ExecutionException("Azure blob name not provided in resource properties.");
            }
            BlobClient blobClient = ((AzureServer) this.getReader().getServer())
                    .getContainerClient().getBlobClient(blobName);
            if (!blobClient.exists()) {
                throw new ExecutionException("Blob " + blobName + " does not exist.");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(blobClient.openInputStream(), StandardCharsets.UTF_8));
            boolean headerSkipped = false;
            int averageSize = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    averageSize = line.getBytes(StandardCharsets.UTF_8).length;
                    break;
                }
            }
            reader.close();
            return averageSize;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get average row size from Azure Blob", e);
        }
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        return CsvUtils.getFields(getFileReader());
    }

    @Override
    public AzureReader getReader() {
        return (AzureReader) super.getReader();
    }
}
