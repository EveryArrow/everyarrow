/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;

import java.util.ArrayList;
import java.util.List;

public class CloudFrontOriginManager implements AutoCloseable {

    private final CloudFrontClient cloudFrontClient;

    public CloudFrontOriginManager() {
        this.cloudFrontClient = CloudFrontClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .build();
    }

    public void addOrUpdateAlbOrigin(
            String distributionId,
            String originId,
            String albDnsName,
            boolean useHttps,
            int port) {

        System.out.println("Adding/Updating ALB origin in CloudFront distribution: " + distributionId);
        System.out.println("Origin ID: " + originId);
        System.out.println("ALB DNS: " + albDnsName);
        System.out.println("Protocol: " + (useHttps ? "HTTPS" : "HTTP"));
        System.out.println("Port: " + port);

        try {
            GetDistributionConfigRequest getRequest = GetDistributionConfigRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionConfigResponse getResponse = cloudFrontClient.getDistributionConfig(getRequest);
            DistributionConfig config = getResponse.distributionConfig();
            String etag = getResponse.eTag();

            Origins currentOrigins = config.origins();
            List<Origin> originList = new ArrayList<>(currentOrigins.items());

            boolean originExists = false;
            for (int i = 0; i < originList.size(); i++) {
                if (originList.get(i).id().equals(originId)) {
                    System.out.println("Origin already exists, updating...");
                    originList.set(i, buildAlbOrigin(originId, albDnsName, useHttps, port));
                    originExists = true;
                    break;
                }
            }

            if (!originExists) {
                System.out.println("Origin does not exist, adding new origin...");
                originList.add(buildAlbOrigin(originId, albDnsName, useHttps, port));
            }

            Origins updatedOrigins = Origins.builder()
                    .quantity(originList.size())
                    .items(originList)
                    .build();

            DistributionConfig updatedConfig = config.toBuilder()
                    .origins(updatedOrigins)
                    .build();

            UpdateDistributionRequest updateRequest = UpdateDistributionRequest.builder()
                    .id(distributionId)
                    .distributionConfig(updatedConfig)
                    .ifMatch(etag)
                    .build();

            UpdateDistributionResponse updateResponse = cloudFrontClient.updateDistribution(updateRequest);

            System.out.println("CloudFront distribution updated successfully");
            System.out.println("Distribution Status: " + updateResponse.distribution().status());

        } catch (NoSuchDistributionException e) {
            throw new RuntimeException("CloudFront distribution not found: " + distributionId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update CloudFront distribution: " + e.getMessage(), e);
        }
    }

    public void addOrUpdateAlbOriginMatchViewer(
            String distributionId,
            String originId,
            String albDnsName) {

        System.out.println("Adding/Updating ALB origin in CloudFront distribution: " + distributionId);
        System.out.println("Origin ID: " + originId);
        System.out.println("ALB DNS: " + albDnsName);
        System.out.println("Protocol: Match Viewer");

        try {
            GetDistributionConfigRequest getRequest = GetDistributionConfigRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionConfigResponse getResponse = cloudFrontClient.getDistributionConfig(getRequest);
            DistributionConfig config = getResponse.distributionConfig();
            String etag = getResponse.eTag();

            Origins currentOrigins = config.origins();
            List<Origin> originList = new ArrayList<>(currentOrigins.items());

            boolean originExists = false;
            for (int i = 0; i < originList.size(); i++) {
                if (originList.get(i).id().equals(originId)) {
                    System.out.println("Origin already exists, updating...");
                    originList.set(i, buildAlbOriginMatchViewer(originId, albDnsName));
                    originExists = true;
                    break;
                }
            }

            if (!originExists) {
                System.out.println("Origin does not exist, adding new origin...");
                originList.add(buildAlbOriginMatchViewer(originId, albDnsName));
            }

            Origins updatedOrigins = Origins.builder()
                    .quantity(originList.size())
                    .items(originList)
                    .build();

            DistributionConfig updatedConfig = config.toBuilder()
                    .origins(updatedOrigins)
                    .build();

            UpdateDistributionRequest updateRequest = UpdateDistributionRequest.builder()
                    .id(distributionId)
                    .distributionConfig(updatedConfig)
                    .ifMatch(etag)
                    .build();

            UpdateDistributionResponse updateResponse = cloudFrontClient.updateDistribution(updateRequest);

            System.out.println("CloudFront distribution updated successfully");
            System.out.println("Distribution Status: " + updateResponse.distribution().status());

        } catch (NoSuchDistributionException e) {
            throw new RuntimeException("CloudFront distribution not found: " + distributionId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update CloudFront distribution: " + e.getMessage(), e);
        }
    }

    private Origin buildAlbOrigin(String originId, String albDnsName, boolean useHttps, int port) {
        CustomOriginConfig.Builder customOriginBuilder = CustomOriginConfig.builder()
                .originProtocolPolicy(useHttps ? OriginProtocolPolicy.HTTPS_ONLY : OriginProtocolPolicy.HTTP_ONLY)
                .originSslProtocols(OriginSslProtocols.builder()
                        .quantity(1)
                        .items(SslProtocol.TLS_V1_2)
                        .build())
                .originReadTimeout(30)
                .originKeepaliveTimeout(5)
                .httpPort(80) // Required field even when using HTTPS
                .httpsPort(443); // Required field even when using HTTP

        // Override with actual port if different
        if (useHttps && port != 443) {
            customOriginBuilder.httpsPort(port);
        } else if (!useHttps && port != 80) {
            customOriginBuilder.httpPort(port);
        }

        return Origin.builder()
                .id(originId)
                .domainName(albDnsName)
                .originPath("")
                .customOriginConfig(customOriginBuilder.build())
                .customHeaders(CustomHeaders.builder()
                        .quantity(0)
                        .build())
                .connectionAttempts(3)
                .connectionTimeout(10)
                .build();
    }

    private Origin buildAlbOriginMatchViewer(String originId, String albDnsName) {
        CustomOriginConfig customOriginConfig = CustomOriginConfig.builder()
                .originProtocolPolicy(OriginProtocolPolicy.MATCH_VIEWER)
                .originSslProtocols(OriginSslProtocols.builder()
                        .quantity(1)
                        .items(SslProtocol.TLS_V1_2)
                        .build())
                .originReadTimeout(30)
                .originKeepaliveTimeout(5)
                .httpPort(80)
                .httpsPort(443)
                .build();

        return Origin.builder()
                .id(originId)
                .domainName(albDnsName)
                .originPath("")
                .customOriginConfig(customOriginConfig)
                .customHeaders(CustomHeaders.builder()
                        .quantity(0)
                        .build())
                .connectionAttempts(3)
                .connectionTimeout(10)
                .build();
    }

    public boolean originExists(String distributionId, String originId) {
        try {
            GetDistributionConfigRequest request = GetDistributionConfigRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionConfigResponse response = cloudFrontClient.getDistributionConfig(request);
            DistributionConfig config = response.distributionConfig();

            return config.origins().items().stream()
                    .anyMatch(origin -> origin.id().equals(originId));

        } catch (Exception e) {
            throw new RuntimeException("Failed to check origin existence: " + e.getMessage(), e);
        }
    }

    public void removeOrigin(String distributionId, String originId) {
        System.out.println("Removing origin from CloudFront distribution: " + distributionId);
        System.out.println("Origin ID: " + originId);

        try {
            GetDistributionConfigRequest getRequest = GetDistributionConfigRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionConfigResponse getResponse = cloudFrontClient.getDistributionConfig(getRequest);
            DistributionConfig config = getResponse.distributionConfig();
            String etag = getResponse.eTag();

            Origins currentOrigins = config.origins();
            List<Origin> originList = new ArrayList<>(currentOrigins.items());

            boolean removed = originList.removeIf(origin -> origin.id().equals(originId));

            if (!removed) {
                System.out.println("Origin not found, nothing to remove");
                return;
            }

            if (originList.isEmpty()) {
                throw new RuntimeException("Cannot remove the last origin from a CloudFront distribution");
            }

            Origins updatedOrigins = Origins.builder()
                    .quantity(originList.size())
                    .items(originList)
                    .build();

            DistributionConfig updatedConfig = config.toBuilder()
                    .origins(updatedOrigins)
                    .build();

            UpdateDistributionRequest updateRequest = UpdateDistributionRequest.builder()
                    .id(distributionId)
                    .distributionConfig(updatedConfig)
                    .ifMatch(etag)
                    .build();

            cloudFrontClient.updateDistribution(updateRequest);

            System.out.println("Origin removed successfully");

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove origin: " + e.getMessage(), e);
        }
    }

    public void updateAllowedMethods(String distributionId, String targetOriginId, boolean allowAllMethods) {
        System.out.println("Updating allowed methods for CloudFront distribution: " + distributionId);
        System.out.println("Target Origin ID: " + targetOriginId);
        System.out.println("Allow all methods: " + allowAllMethods);

        try {
            GetDistributionConfigRequest getRequest = GetDistributionConfigRequest.builder()
                    .id(distributionId)
                    .build();

            GetDistributionConfigResponse getResponse = cloudFrontClient.getDistributionConfig(getRequest);
            DistributionConfig config = getResponse.distributionConfig();
            String etag = getResponse.eTag();

            // Update default cache behavior
            DefaultCacheBehavior defaultBehavior = config.defaultCacheBehavior();
            AllowedMethods allowedMethods;

            if (allowAllMethods) {
                // Allow all HTTP methods: GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE
                // Do not cache any methods - only GET and HEAD are cached
                allowedMethods = AllowedMethods.builder()
                        .quantity(7)
                        .items(Method.GET, Method.HEAD, Method.OPTIONS, Method.PUT, Method.POST, Method.PATCH,
                                Method.DELETE)
                        .cachedMethods(CachedMethods.builder()
                                .quantity(2)
                                .items(Method.GET, Method.HEAD)
                                .build())
                        .build();
            } else {
                // Default: Only GET and HEAD
                allowedMethods = AllowedMethods.builder()
                        .quantity(2)
                        .items(Method.GET, Method.HEAD)
                        .cachedMethods(CachedMethods.builder()
                                .quantity(2)
                                .items(Method.GET, Method.HEAD)
                                .build())
                        .build();
            }

            // Update cache behavior with allowed methods, target origin, and disable
            // caching
            // Use CachingDisabled managed cache policy (ID:
            // 4135ea2d-6df8-44a3-9df3-4b5a84be39ad)
            DefaultCacheBehavior updatedDefaultBehavior = defaultBehavior.toBuilder()
                    .targetOriginId(targetOriginId)
                    .allowedMethods(allowedMethods)
                    .cachePolicyId("4135ea2d-6df8-44a3-9df3-4b5a84be39ad") // AWS Managed CachingDisabled policy
                    .build();

            DistributionConfig updatedConfig = config.toBuilder()
                    .defaultCacheBehavior(updatedDefaultBehavior)
                    .build();

            UpdateDistributionRequest updateRequest = UpdateDistributionRequest.builder()
                    .id(distributionId)
                    .distributionConfig(updatedConfig)
                    .ifMatch(etag)
                    .build();

            UpdateDistributionResponse updateResponse = cloudFrontClient.updateDistribution(updateRequest);

            System.out.println("CloudFront allowed methods updated successfully");
            System.out.println("Default cache behavior now points to origin: " + targetOriginId);
            System.out.println("Cache policy set to CachingDisabled (no caching)");
            System.out.println("Distribution Status: " + updateResponse.distribution().status());

        } catch (NoSuchDistributionException e) {
            throw new RuntimeException("CloudFront distribution not found: " + distributionId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update allowed methods: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (cloudFrontClient != null) {
            cloudFrontClient.close();
        }
    }
}
