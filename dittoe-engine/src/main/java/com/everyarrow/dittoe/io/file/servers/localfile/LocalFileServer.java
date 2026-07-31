package com.everyarrow.dittoe.io.file.servers.localfile;

import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.ResourcePresentation;
import com.everyarrow.dittoe.io.Writer;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.localfile.reader.LocalFileReader;
import com.everyarrow.dittoe.io.file.servers.localfile.writer.LocalFileWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalFileServer extends FileServer implements OrchestrationMetadataProvider {
    private static final ResourcePresentation RESOURCE_PRESENTATION = new ResourcePresentation("File", List.of());
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public LocalFileServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new LocalFileReader() : null, isSource ? null : new LocalFileWriter());

        if (isSource)
            this.getReader().setServer(this);
        else
            this.getWriter().setServer(this);
    }

    @Override
    public ResourcePresentation getResourcePresentation() {
        return RESOURCE_PRESENTATION;
    }

    public File downloadFile(String fileName, Path destinationPath) throws ExecutionException {
        try {
            // Retrieve the path where the files are stored from connection details.
            String path = this.getConnectionDetails().get("path");
            if (path == null || path.isBlank()) {
                throw new ExecutionException("Local file download requires a configured base path.");
            }

            // Copy the source file (ZIP) from the local directory to the temporary file.
            Files.copy(SafePathResolver.resolveUnderBase(path, fileName), destinationPath, StandardCopyOption.REPLACE_EXISTING);

            // Return the downloaded file.
            return destinationPath.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from local directory.", e);
        }
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public Writer getWriter() {
        return super.getWriter();
    }

    @Override
    public List<Resource> getResources() {
        try {
            // Get the path from connection details
            String path = this.getConnectionDetails().get("path");
            if (path == null || path.isEmpty()) {
                throw new ExecutionException("No path specified in connection details");
            }
            
            // Create Path object for the directory
            Path directoryPath = SafePathResolver.resolveAllowedPath(path, "local file resource directory");
            if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
                throw new ExecutionException("Specified path does not exist or is not a directory: " + path);
            }
            
            // List all files in the directory (non-recursive)
            List<Resource> resources = new ArrayList<>();
            try (var fileStream = Files.list(directoryPath)) {
                fileStream.filter(Files::isRegularFile).forEach(filePath -> {
                    // Create a new Resource for each file
                    Resource fileResource = new Resource();
                    fileResource.setResourceName(filePath.getFileName().toString());
                    fileResource.setResourceProperties(new HashMap<>());
                    fileResource.getResourceProperties().put("filePath", filePath.toString());
                    fileResource.setServer(this);
                    
                    resources.add(fileResource);
                });
            }
            
            return resources;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void testConnection() {
        // nothing to test
    }

    @Override
    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        Map<String, List<String>> resourceDependencies = new HashMap<>();
        resources.forEach(resource -> resourceDependencies.put(resource.getResourceName(), new ArrayList<>()));
        return resourceDependencies;
    }

    @Override
    public Map<String, List<Field>> getResourcesFields(List<Resource> resources) {
        Map<String, List<Field>> result = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return result;
        }

        try {
            for (Resource resource : resources) {
                String fileName = resource.getResourceName();
                if (fileName == null || fileName.isBlank()) {
                    logger.warn("Resource name (file name) cannot be null or blank");
                    continue;
                }

                // Get path from connection details
                String path = this.getConnectionDetails().get("path");
                // Get filePath from resource properties (if set)
                Map<String, String> props = resource.getResourceProperties();
                String filePath = props != null ? props.get("filePath") : null;

                // Determine the full file path
                String fullPath;
                if (filePath != null && !filePath.isBlank()) {
                    fullPath = SafePathResolver.resolveUnderBase(path, Path.of(filePath).getFileName().toString()).toString();
                } else if (path != null && !path.isBlank()) {
                    fullPath = SafePathResolver.resolveUnderBase(path, fileName).toString();
                } else {
                    logger.warn("No file path or path specified for resource: {}", fileName);
                    continue;
                }

                // Determine file extension
                String fileExtension = getFileExtension(fileName).toLowerCase();

                try {
                    List<Field> fields = getFieldsForResource(fullPath, fileExtension);
                    result.put(fileName, fields);
                } catch (Exception e) {
                    logger.warn("Failed to get fields for resource {}: {}", fileName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred while retrieving fields for resources: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Extracts fields from a resource based on its file type.
     *
     * @param filePath      The full path to the file
     * @param fileExtension The file extension
     * @return List of fields extracted from the resource
     * @throws ExecutionException if field extraction fails
     */
    private List<Field> getFieldsForResource(String filePath, String fileExtension) throws ExecutionException {
        switch (fileExtension.toLowerCase()) {
            case "csv":
                return getFieldsFromCsv(filePath);
            // Add more file types here as they become supported
            // case "json":
            //     return getFieldsFromJson(filePath);
            // case "xml":
            //     return getFieldsFromXml(filePath);
            default:
                return new ArrayList<>(); // Return empty list for unsupported file types
        }
    }

    /**
     * Extracts fields from a CSV file by reading only the first line.
     *
     * @param filePath The full path to the CSV file
     * @return List of fields from the CSV file
     * @throws ExecutionException if field extraction fails
     */
    private List<Field> getFieldsFromCsv(String filePath) throws ExecutionException {
        try {
            Path path = SafePathResolver.resolveAllowedPath(filePath, "local CSV file");
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new ExecutionException("File does not exist or is not a regular file: " + filePath);
            }

            // Read only the first line to get headers
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(path.toFile(), StandardCharsets.UTF_8))) {
                String firstLine = reader.readLine();
                if (firstLine == null || firstLine.trim().isEmpty()) {
                    throw new ExecutionException("CSV file appears to be empty or has no readable content");
                }

                // Use CsvUtils to parse the first line
                String delimiter = ","; // Default delimiter for CSV
                return CsvUtils.getFieldsFromFirstLine(firstLine, delimiter);
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to extract fields from CSV file: " + filePath, e);
        }
    }

    /**
     * Extracts the file extension from a file name.
     *
     * @param fileName The file name
     * @return The file extension (without the dot) or empty string if no extension
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 && lastDotIndex < fileName.length() - 1
                ? fileName.substring(lastDotIndex + 1)
                : "";
    }

    @Override
    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        Map<String, Map<String, String>> result = new HashMap<>();

        try {
            if (resources != null) {
                resources.stream()
                    .map(Resource::getResourceName)
                    .forEach(resourceName -> result.put(resourceName, new HashMap<>()));

                logger.info("Generated merge properties for {} local file resources", result.size());
            }
        } catch (Exception e) {
            logger.error("Error occurred while generating merge properties for local file resources: {}", e.getMessage(), e);
        }

        return result;
    }
}
