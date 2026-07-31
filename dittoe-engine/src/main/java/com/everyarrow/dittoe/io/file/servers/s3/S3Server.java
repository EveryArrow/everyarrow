package com.everyarrow.dittoe.io.file.servers.s3;

import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.ResourcePresentation;
import com.everyarrow.dittoe.io.ResourcePropertyDescriptor;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.s3.reader.S3Reader;
import com.everyarrow.dittoe.io.file.servers.s3.writer.S3Writer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.EndpointSafety;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import com.everyarrow.dittoe.utility.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Concrete S3 server that implements connection creation and all server-related functionalities.
 * <p>
 * All heavy initialization (including creating the S3 connection using credentials)
 * is performed in the {@code init()} method. This server uses an S3Reader when used as a source
 * and an S3Writer when used as a target.
 */
public class S3Server extends FileServer implements OrchestrationMetadataProvider {
    private static final ResourcePresentation RESOURCE_PRESENTATION = new ResourcePresentation(
            "Object",
            List.of(new ResourcePropertyDescriptor("bucket", "Bucket", true))
    );
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Map<String, S3Client> regionClients = new HashMap<>();
    private S3Client s3Client;
    private AwsBasicCredentials awsCredentials;
    private URI endpointOverride;

    /**
     * Minimal constructor. No heavy initialization is done here.
     * The reader and writer objects are created but not fully initialized.
     *
     * @param isSource true if the server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public S3Server(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new S3Reader() : null, isSource ? null : new S3Writer());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    @Override
    public ResourcePresentation getResourcePresentation() {
        return RESOURCE_PRESENTATION;
    }

    /**
     * Initializes the S3 server.
     * <p>
     * This method creates the S3 connection (client) using credentials and region from the resource properties.
     * The following properties must be defined in the resource:
     * <ul>
     *   <li>s3AccessKey</li>
     *   <li>s3SecretKey</li>
     *   <li>s3Region</li>
     * </ul>
     * After establishing the connection, it calls {@code init()} on the parent and on the associated reader and writer.
     *
     * @throws ExecutionException if connection creation or initialization fails.
     */
    @Override
    public void init() throws ExecutionException {

        Map<String, String> props = this.getConnectionDetails();
        String accessKey = props.get("s3AccessKey");
        String secretKey = props.get("s3SecretKey");
        String region = props.get("s3Region");
        if (accessKey == null || secretKey == null || region == null) {
            throw new ExecutionException("Missing required S3 credentials or region in resource properties. " +
                    "Please set s3AccessKey, s3SecretKey, and s3Region.");
        }
        try {
            endpointOverride = validateEndpointOverride(props);
            awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
            this.s3Client = createS3Client(region);
        } catch (Exception e) {
            throw new ExecutionException("Failed to create S3 connection using provided credentials", e);
        }
//
//        // Initialize reader and writer so that their strategies can use the S3 client.
//        if (this.getReader() != null) {
//            this.getReader().init();
//        }
//        if (this.getWriter() != null) {
//            this.getWriter().init();
//        }

        // Perform any parent initialization.
        super.init();
    }

    /**
     * Gets or creates an S3 client for the specified region.
     * Caches clients to avoid creating multiple clients for the same region.
     *
     * @param region the AWS region
     * @return S3Client configured for the specified region
     */
    private S3Client getS3ClientForRegion(String region) {
        return regionClients.computeIfAbsent(region, r -> {
            logger.info("Creating S3 client for region: {}", r);
            return createS3Client(r);
        });
    }

    private S3Client createS3Client(String region) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));

        if (endpointOverride != null) {
            builder.endpointOverride(endpointOverride);
        }

        Map<String, String> props = this.getConnectionDetails();
        if (Boolean.parseBoolean(props.getOrDefault("s3PathStyleAccess", "false"))) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return builder.build();
    }

    static URI validateEndpointOverride(Map<String, String> properties) throws ExecutionException {
        String endpoint = properties.get("s3Endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        return EndpointSafety.requirePublicHttpsEndpoint(endpoint, properties);
    }

    /**
     * Gets the region of a specific S3 bucket.
     *
     * @param bucketName the name of the bucket
     * @return the region where the bucket is located
     */
    private String getBucketRegion(String bucketName) {
        Map<String, String> props = this.getConnectionDetails();
        if (props.containsKey("s3Endpoint")) {
            return props.getOrDefault("s3Region", "us-east-1");
        }

        try {
            S3Client globalClient = getS3ClientForRegion(Region.AWS_GLOBAL.id());
            GetBucketLocationResponse response = globalClient.getBucketLocation(GetBucketLocationRequest.builder()
                    .bucket(bucketName)
                    .build());

            String loc = response.locationConstraintAsString();
            return (loc == null || loc.isBlank()) ? "us-east-1" : loc;
        } catch (S3Exception e) {
            // workaround since S3API likes to fail but for some reason works fine for CLI
            String extractedRegion = extractRegionFromErrorMessage(e.getMessage());
            if (extractedRegion != null) {
                logger.info("Extracted region {} from error message for bucket {}", extractedRegion, bucketName);
                return extractedRegion;
            }

            logger.warn("Could not determine region for bucket {}, defaulting to us-east-1: {}",
                    bucketName, e.getMessage());
            return "us-east-1";
        }
    }

    /**
     * Extracts the expected region from AWS S3 error messages.
     * Looks for patterns like "expecting 'us-east-2'" in the error message.
     *
     * @param errorMessage the error message from S3Exception
     * @return the extracted region or null if not found
     */
    private String extractRegionFromErrorMessage(String errorMessage) {
        if (errorMessage == null) return null;

        // Look for pattern: expecting 'region-name'
        Pattern pattern = java.util.regex.Pattern.compile("expecting\s+'([^']+)'");
        Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private Predicate<String> testForDefault() {
        Set<Pattern> patterns = Set.of(
                // CDK bootstrap (default & qualified)
                Pattern.compile("^cdk-(hnb659fds-assets|toolkit|.*-assets-).*$"),
                Pattern.compile("^cdktoolkit-.*$"),
                // CloudTrail / Config / Logs / EB / Athena
                Pattern.compile("^aws[-.]logs[-.].*$"),
                Pattern.compile("^aws[-.]cloudtrail[-.].*$"),
                Pattern.compile("^aws[-.]config[-.].*$"),
                Pattern.compile("^elasticbeanstalk[-.].*$"),
                Pattern.compile("^athena-query-results[-.].*$")
        );

        return name -> patterns.stream().anyMatch(p -> p.matcher(name).matches());
    }

    @Override
    public List<Resource> getResources() {
        try {
            List<Resource> resources = new ArrayList<>();
            AtomicInteger resourceIdCounter = new AtomicInteger(1);

            String bucketName = this.getConnectionDetails().get("bucket");
            if (bucketName == null) {
                throw new RuntimeException("Bucket must be specified for listing resources");
            }

            logger.info("Processing bucket: {}", bucketName);

            // Get the bucket's region and create/get appropriate S3 client
            String bucketRegion = getBucketRegion(bucketName);

            S3Client regionSpecificClient = getS3ClientForRegion(bucketRegion);
            logger.info("Using S3 client for region: {} for bucket: {}", bucketRegion, bucketName);

            // List all objects in the current S3 bucket using region-specific client
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listResponse;
            do {
                listResponse = regionSpecificClient.listObjectsV2(listRequest);

                // Process S3 objects in parallel for better performance
                List<S3Object> validObjects = listResponse.contents().stream()
                        .filter(s3Object -> !s3Object.key().endsWith("/")) // Skip directories
                        .collect(Collectors.toList());

                if (!validObjects.isEmpty()) {
                    List<Resource> batchResources = processS3ObjectsInParallel(validObjects, bucketName, resourceIdCounter, regionSpecificClient);
                    resources.addAll(batchResources);
                    logger.info("Processed {} resources from bucket: {}", batchResources.size(), bucketName);
                }

                // Handle pagination
                listRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .continuationToken(listResponse.nextContinuationToken())
                        .build();

            } while (listResponse.isTruncated());

            return resources;

        } catch (Exception e) {
            throw new RuntimeException("Error occurred while retrieving S3 resources: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Resource object from an S3Object with appropriate read strategy based on file extension.
     *
     * @param s3Object   The S3Object to create a resource from
     * @param bucketName The S3 bucket name
     * @param resourceId The unique resource ID
     * @param s3Client   The S3 client to use for field extraction
     * @return A Resource object or null if the file type is not supported
     */
    private Resource createResourceFromS3Object(S3Object s3Object, String bucketName, int resourceId, S3Client s3Client) {
        String objectKey = s3Object.key();
        String fileName = getFileNameFromKey(objectKey);
        String fileExtension = getFileExtension(fileName).toLowerCase();

        // Determine if we support this file type
        String strategyClassName = getReadStrategyForFileType(fileExtension);
        if (strategyClassName == null) {
            // Skip unsupported file types
            return null;
        }

        // Create the resource
        Resource resource = new Resource();
        resource.setResourceID(resourceId);
        resource.setResourceName(objectKey.substring(objectKey.lastIndexOf('/') + 1)); // Get file name instead of full path
        resource.setServer(this);
        resource.setStrategy(strategyClassName);

        // Set resource properties
        Map<String, String> resourceProperties = new HashMap<>();
        resourceProperties.put("bucket", bucketName);
        resource.setResourceProperties(resourceProperties);


        return resource;
    }

    /**
     * Processes a list of S3 objects in parallel to create resources with field extraction.
     *
     * @param s3Objects         List of S3 objects to process
     * @param bucketName        The S3 bucket name
     * @param resourceIdCounter Atomic counter for resource IDs
     * @param s3Client          The S3 client to use for field extraction
     * @return List of created resources
     */
    private List<Resource> processS3ObjectsInParallel(List<S3Object> s3Objects, String bucketName,
                                                      AtomicInteger resourceIdCounter, S3Client s3Client) {
        // Create a thread pool with reasonable number of threads (CPU cores * 2)
        int threadPoolSize = Math.min(Runtime.getRuntime().availableProcessors() * 2, s3Objects.size());

        try (ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize)) {
            // Create CompletableFuture for each S3 object processing
            List<CompletableFuture<Resource>> futures = s3Objects.stream()
                    .map(s3Object -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Resource resource = createResourceFromS3Object(s3Object, bucketName,
                                    resourceIdCounter.getAndIncrement(), s3Client);
                            if (resource != null) {
                                logger.debug("Successfully processed resource: {}", s3Object.key());
                            }
                            return resource;
                        } catch (Exception e) {
                            logger.warn("Failed to process S3 object {}: {}", s3Object.key(), e.getMessage());
                            return null;
                        }
                    }, executor))
                    .toList();

            // Wait for all futures to complete and collect results
            List<Resource> resources = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            logger.info("Parallel processing completed: {}/{} resources created successfully",
                    resources.size(), s3Objects.size());

            return resources;
        }
    }

    /**
     * Extracts fields from a resource based on its file type.
     *
     * @param bucketName    The S3 bucket name
     * @param objectKey     The S3 object key
     * @param fileExtension The file extension
     * @param s3Client      The S3 client to use for field extraction
     * @return List of fields extracted from the resource
     * @throws ExecutionException if field extraction fails
     */
    private List<Field> getFieldsForResource(String bucketName, String objectKey, String fileExtension, S3Client s3Client) throws ExecutionException {
        switch (fileExtension.toLowerCase()) {
            case "csv":
                return getFieldsFromCsv(bucketName, objectKey, s3Client);
            // Add more file types here as they become supported
            // case "json":
            //     return getFieldsFromJson(bucketName, objectKey, s3Client);
            // case "xml":
            //     return getFieldsFromXml(bucketName, objectKey, s3Client);
            default:
                return new ArrayList<>(); // Return empty list for unsupported file types
        }
    }

    /**
     * Extracts fields from a CSV file in S3 by reading only the first line (optimized for large files).
     *
     * @param bucketName The S3 bucket name
     * @param objectKey  The S3 object key
     * @param s3Client   The S3 client to use for field extraction
     * @return List of fields from the CSV file
     * @throws ExecutionException if field extraction fails
     */
    private List<Field> getFieldsFromCsv(String bucketName, String objectKey, S3Client s3Client) throws ExecutionException {
        try {
            // Read only the first 100KB to get the header line (much more efficient than downloading entire file)
            String firstLine = readFirstLineFromS3(s3Client, bucketName, objectKey);

            // Extract delimiter from resource properties or use default
            String delimiter = ","; // Default delimiter for CSV

            return CsvUtils.getFieldsFromFirstLine(firstLine, delimiter);
        } catch (Exception e) {
            throw new ExecutionException("Failed to extract fields from CSV file: " + objectKey, e);
        }
    }

    /**
     * Reads only the first line from an S3 object, optimized to minimize data transfer.
     *
     * @param s3Client   The S3 client to use
     * @param bucketName The S3 bucket name
     * @param objectKey  The S3 object key
     * @return The first line of the file
     * @throws ExecutionException if reading fails
     */
    private String readFirstLineFromS3(S3Client s3Client, String bucketName, String objectKey) throws ExecutionException {
        try {
            // Request only the first 100KB of the file (should be enough for most CSV headers)
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .range("bytes=0-10239") // Read first 100KB only
                    .build();

            // Get the partial object content
            ResponseInputStream<GetObjectResponse> s3Object =
                    s3Client.getObject(getObjectRequest, ResponseTransformer.toInputStream());

            // Read the content and extract the first line
            try (InputStreamReader reader = new InputStreamReader(s3Object, StandardCharsets.UTF_8);
                 java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader)) {

                String firstLine = bufferedReader.readLine();
                if (firstLine == null || firstLine.trim().isEmpty()) {
                    throw new ExecutionException("CSV file appears to be empty or has no readable content");
                }

                return firstLine;
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to read first line from S3 object: " + objectKey, e);
        }
    }

    /**
     * Extracts the file name from an S3 object key.
     *
     * @param objectKey The S3 object key
     * @return The file name
     */
    private String getFileNameFromKey(String objectKey) {
        int lastSlashIndex = objectKey.lastIndexOf('/');
        return lastSlashIndex >= 0 ? objectKey.substring(lastSlashIndex + 1) : objectKey;
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

    /**
     * Determines the appropriate read strategy class name based on file extension.
     *
     * @param fileExtension The file extension (lowercase)
     * @return The fully qualified class name of the read strategy, or null if not supported
     */
    private String getReadStrategyForFileType(String fileExtension) {
        return switch (fileExtension) {
            case "csv" -> "com.dittoe.strategy.reader.s3.servers.file.io.dittoe.CSVReadStrategy";
            // Add more file types and their corresponding strategies here as they become available
            // case "json" -> "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.JSONReadStrategy";
            // case "xml" -> "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.XMLReadStrategy";
            // case "txt" -> "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.TextReadStrategy";
            default -> null; // Unsupported file type
        };
    }

    @Override
    public Map<String, List<Field>> getResourcesFields(List<Resource> resources) {
        Map<String, List<Field>> result = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return result;
        }

        try {
            // Group resources by bucket for efficient batch processing
            Map<String, List<Resource>> resourcesByBucket = new HashMap<>();

            for (Resource resource : resources) {
                String fileName = resource.getResourceName();
                if (fileName == null || fileName.isBlank()) {
                    throw new RuntimeException("Resource name (file name) cannot be null or blank");
                }

                Map<String, String> props = resource.getResourceProperties();
                String bucket = props != null ? props.get("bucket") : null;
                if (bucket == null || bucket.isBlank()) {
                    throw new RuntimeException("Bucket property is required for resource '" + fileName + "'");
                }

                resourcesByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(resource);
            }

            // Process resources grouped by bucket
            for (Map.Entry<String, List<Resource>> entry : resourcesByBucket.entrySet()) {
                String bucket = entry.getKey();
                List<Resource> bucketResources = entry.getValue();

                // Get the bucket's region and create/get appropriate S3 client
                String bucketRegion = getBucketRegion(bucket);
                S3Client regionSpecificClient = getS3ClientForRegion(bucketRegion);

                // Build a map of all objects in the bucket for quick lookup
                Map<String, String> fileNameToKey = new HashMap<>();
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .build();

                ListObjectsV2Response listResponse;
                do {
                    listResponse = regionSpecificClient.listObjectsV2(listRequest);
                    for (S3Object obj : listResponse.contents()) {
                        String fileName = getFileNameFromKey(obj.key());
                        fileNameToKey.put(fileName, obj.key());
                    }

                    if (listResponse.isTruncated()) {
                        listRequest = ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .continuationToken(listResponse.nextContinuationToken())
                                .build();
                    }
                } while (listResponse.isTruncated());

                // Now process each resource
                for (Resource resource : bucketResources) {
                    String targetFileName = resource.getResourceName();
                    String key = fileNameToKey.get(targetFileName);

                    if (key == null) {
                        logger.warn("No S3 object found in bucket '{}' with file name '{}'", bucket, targetFileName);
                        continue;
                    }

                    // Determine file extension for strategy/field extraction
                    String fileExtension = getFileExtension(targetFileName).toLowerCase();

                    try {
                        List<Field> fields = getFieldsForResource(bucket, key, fileExtension, regionSpecificClient);
                        result.put(targetFileName, fields);
                    } catch (Exception e) {
                        logger.warn("Failed to get fields for resource {}: {}", targetFileName, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error occurred while retrieving fields for resources: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Finds the first S3 object key whose basename matches the provided fileName.
     *
     * @param client     The S3 client to use
     * @param bucketName The S3 bucket name
     * @param fileName   The file name to match (basename)
     * @return The full S3 object key if found, otherwise null
     */
    private String findObjectKeyByFileName(S3Client client, String bucketName, String fileName) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        do {
            ListObjectsV2Response listResponse = client.listObjectsV2(listRequest);
            for (S3Object obj : listResponse.contents()) {
                if (fileName.equals(getFileNameFromKey(obj.key()))) {
                    return obj.key();
                }
            }

            if (!listResponse.isTruncated()) {
                break;
            }

            listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .continuationToken(listResponse.nextContinuationToken())
                    .build();
        } while (true);

        return null;
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve bucket name from resource properties
            Map<String, String> props = this.getResource().getResourceProperties();
            String bucket = props.get("bucket");
            if (bucket == null || bucket.isEmpty()) {
                throw new ExecutionException("Bucket name (bucket) not provided in resource properties.");
            }

            // Build the S3 GetObjectRequest using the bucket and file name (key).
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();

            // Download the file to the temporary file.
            this.getS3Client().getObject(getRequest, ResponseTransformer.toFile(path));

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from S3", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Provides the S3 client created during initialization.
     *
     * @return the S3Client instance.
     */
    public S3Client getS3Client() {
        return s3Client;
    }

    @Override
    public void testConnection() {
        // Test the connection by invoking an S3 operation (e.g., list buckets).
        try {
            s3Client.listBuckets();
        } catch (Exception e) {
            throw new RuntimeException("S3 connection test failed.", e);
        }
    }

    /**
     * Cleanup method to close all region-specific S3 clients.
     * Should be called when the server is being shut down.
     */
    public void cleanup() {
        regionClients.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing S3 client: {}", e.getMessage());
            }
        });
        regionClients.clear();

        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception e) {
                logger.warn("Error closing main S3 client: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() throws Exception {
        cleanup();
    }

    @Override
    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        return new HashMap<>();
    }

    @Override
    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        Map<String, Map<String, String>> result = new HashMap<>();

        try {
            resources.stream().map(Resource::getResourceName).forEach(resourceName ->
                    result.put(resourceName, new HashMap<>()));

            logger.info("Generated merge properties for {} S3 resources", result.size());
        } catch (Exception e) {
            logger.error("Error occurred while generating merge properties for S3 resources: {}", e.getMessage(), e);
        }

        return result;
    }
}
