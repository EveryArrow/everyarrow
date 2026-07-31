/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import com.everyarrow.utilities.environment.Environment;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LambdasManager {

    private final LambdaClient lambdaClient;

    public LambdasManager(Region region) {
        this.lambdaClient = LambdaClient.builder()
                .region(region)
                .build();
    }

    /**
     * Creates a generic Node.js Lambda function from a resource file.
     * If the function already exists, its ARN is returned without modifying it.
     *
     * @param functionName  Name of the Lambda function
     * @param resourceName  Name of the JS resource file in classpath
     * @param handler       Handler function (e.g., "index.handler")
     * @param roleArn       IAM role ARN for Lambda execution
     * @param timeoutSeconds Function timeout in seconds
     * @param memoryMb      Function memory in MB
     * @param envVars       Environment variables to set on the function
     * @return The function ARN
     */
    public String createNodejsLambdaFromResource(String functionName,
                                                  String resourceName,
                                                  String handler,
                                                  String roleArn,
                                                  int timeoutSeconds,
                                                  int memoryMb,
                                                  Map<String, String> envVars) {
        try {
            GetFunctionConfigurationRequest getRequest = GetFunctionConfigurationRequest.builder()
                    .functionName(functionName)
                    .build();
            GetFunctionConfigurationResponse existing = lambdaClient.getFunctionConfiguration(getRequest);
            System.out.println("[Lambda] Function already exists, reusing: " + existing.functionArn());
            return existing.functionArn();
        } catch (ResourceNotFoundException e) {
            System.out.println("[Lambda] Function does not exist, creating: " + functionName);
        }

        byte[] zipBytes;
        try {
            zipBytes = loadResourceAsZip(resourceName, "index.js");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lambda source from resources: " + resourceName, e);
        }

        FunctionCode code = FunctionCode.builder()
                .zipFile(SdkBytes.fromByteArray(zipBytes))
                .build();

        CreateFunctionRequest.Builder requestBuilder = CreateFunctionRequest.builder()
                .functionName(functionName)
                .runtime(Runtime.NODEJS18_X)
                .role(roleArn)
                .handler(handler)
                .code(code)
                .timeout(timeoutSeconds)
                .memorySize(memoryMb);

        if (envVars != null && !envVars.isEmpty()) {
            requestBuilder.environment(software.amazon.awssdk.services.lambda.model.Environment.builder()
                    .variables(envVars)
                    .build());
        }

        try {
            CreateFunctionResponse response = lambdaClient.createFunction(requestBuilder.build());
            String arn = response.functionArn();
            System.out.println("[Lambda] Function created: " + arn);
            return arn;
        } catch (LambdaException e) {
            System.err.println("[Lambda] Error creating function: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Creates a Lambda function for CloudFront authentication using JS code from resources.
     * If the function already exists, its ARN is returned without modifying it.
     */
    public String createCloudFrontAuthenticationLambda(String functionName,
                                                       String roleArn,
                                                       int timeoutSeconds,
                                                       int memoryMb) {
        try {
            GetFunctionConfigurationRequest getRequest = GetFunctionConfigurationRequest.builder()
                    .functionName(functionName)
                    .build();
            GetFunctionConfigurationResponse existing = lambdaClient.getFunctionConfiguration(getRequest);
            System.out.println("[Lambda] Function already exists, reusing: " + existing.functionArn());
            return existing.functionArn();
        } catch (ResourceNotFoundException e) {
            System.out.println("[Lambda] Function does not exist, creating: " + functionName);
        }

        byte[] zipBytes;
        try {
            zipBytes = loadLambdaZipFromResource("cloud-front-authentication.js", "index.js");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lambda source from resources", e);
        }

        FunctionCode code = FunctionCode.builder()
                .zipFile(SdkBytes.fromByteArray(zipBytes))
                .build();

        CreateFunctionRequest request = CreateFunctionRequest.builder()
                .functionName(functionName)
                .runtime(Runtime.fromValue("nodejs16.x"))
                .role(roleArn)
                .handler("index.handler")
                .code(code)
                .timeout(timeoutSeconds)
                .memorySize(memoryMb)
                .build();

        try {
            CreateFunctionResponse response = lambdaClient.createFunction(request);
            String arn = response.functionArn();
            System.out.println("[Lambda] Function created: " + arn);
            return arn;
        } catch (LambdaException e) {
            System.err.println("[Lambda] Error creating function: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Loads a resource file and packages it as a ZIP for Lambda deployment.
     * Does not perform any placeholder replacement.
     */
    private byte[] loadResourceAsZip(String resourceName, String entryName) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = LambdasManager.class.getClassLoader();
        }

        try (InputStream in = cl.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            byte[] jsBytes = in.readAllBytes();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(jsBytes);
                zos.closeEntry();
            }
            return baos.toByteArray();
        }
    }

    /**
     * Loads a resource file, replaces auth placeholders, and packages as ZIP.
     * Used specifically for CloudFront authentication Lambda.
     */
    private byte[] loadLambdaZipFromResource(String resourceName, String entryName) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = LambdasManager.class.getClassLoader();
        }

        try (InputStream in = cl.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            byte[] jsBytes = in.readAllBytes();

            // Replace auth placeholders from environment
            String jsSource = new String(jsBytes, StandardCharsets.UTF_8);
            String authUser = Environment.getInstance().get("lambda.authUser");
            String authPassword = Environment.getInstance().get("lambda.authPassword");
            jsSource = jsSource
                    .replace("__AUTH_USER__", authUser)
                    .replace("__AUTH_PASS__", authPassword);

            byte[] finalJsBytes = jsSource.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(finalJsBytes);
                zos.closeEntry();
            }
            return baos.toByteArray();
        }
    }

    public void close() {
        lambdaClient.close();
    }
}
