/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.*;

import java.time.Instant;

public class CloudFrontManager {
    
    private final CloudFrontClient cloudFrontClient;
    
    public CloudFrontManager() {
        // CloudFront is a global service, but the client should use us-east-1
        this.cloudFrontClient = CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .build();
    }
    
    /**
     * Creates a CloudFront distribution for an S3 bucket
     */
    public String createDistribution(String s3BucketDomainName, String s3BucketName, 
                                     String certificateArn, String[] cnames,
                                     String viewerRequestLambdaArn) {
        try {
            // Check if a distribution for this bucket already exists
            ListDistributionsResponse listDistributionsResponse = cloudFrontClient.listDistributions();
            if (listDistributionsResponse.distributionList() != null &&
                listDistributionsResponse.distributionList().items() != null) {
                for (DistributionSummary summary : listDistributionsResponse.distributionList().items()) {
                    boolean originMatches = summary.origins().items().stream()
                            .anyMatch(o -> o.domainName().equals(s3BucketDomainName));
                    boolean commentMatches = ("Distribution for " + s3BucketName).equals(summary.comment());
                    if (originMatches || commentMatches) {
                        System.out.println("[CloudFront] Distribution already exists for bucket, reusing: " + summary.id());
                        return summary.id();
                    }
                }
            }

            String oacName = "oac-" + s3BucketName;

            String oacId;
            try {
                // Create Origin Access Control for this S3 origin
                OriginAccessControlConfig oacConfig = OriginAccessControlConfig.builder()
                        .name(oacName)
                        .description("Origin Access Control for " + s3BucketName)
                        .originAccessControlOriginType(OriginAccessControlOriginTypes.S3)
                        .signingBehavior(OriginAccessControlSigningBehaviors.NO_OVERRIDE)
                        .signingProtocol(OriginAccessControlSigningProtocols.SIGV4)
                        .build();

                CreateOriginAccessControlRequest createOacRequest = CreateOriginAccessControlRequest.builder()
                        .originAccessControlConfig(oacConfig)
                        .build();

                CreateOriginAccessControlResponse createOacResponse = cloudFrontClient.createOriginAccessControl(createOacRequest);
                oacId = createOacResponse.originAccessControl().id();
                System.out.println("[CloudFront] Origin Access Control created: " + oacId);
            } catch (OriginAccessControlAlreadyExistsException e) {
                System.out.println("[CloudFront] Origin Access Control already exists, reusing name: " + oacName);
                ListOriginAccessControlsRequest listRequest = ListOriginAccessControlsRequest.builder().build();
                ListOriginAccessControlsResponse listResponse = cloudFrontClient.listOriginAccessControls(listRequest);
                oacId = listResponse.originAccessControlList().items().stream()
                        .filter(oac -> oac.name().equals(oacName))
                        .findFirst()
                        .map(OriginAccessControlSummary::id)
                        .orElseThrow(() -> e);
            }
            
            // Origin configuration
            String originId = "S3-" + s3BucketName;
            S3OriginConfig s3OriginConfig = S3OriginConfig.builder()
                    .originAccessIdentity("")
                    .build();
            
            Origin origin = Origin.builder()
                    .id(originId)
                    .domainName(s3BucketDomainName)
                    .s3OriginConfig(s3OriginConfig)
                    .originAccessControlId(oacId)
                    .build();
            
            Origins origins = Origins.builder()
                    .quantity(1)
                    .items(origin)
                    .build();
            
            // Default cache behavior
            TrustedSigners trustedSigners = TrustedSigners.builder()
                    .enabled(false)
                    .quantity(0)
                    .build();
            
            DefaultCacheBehavior.Builder dcbBuilder = DefaultCacheBehavior.builder()
                    .targetOriginId(originId)
                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                    .trustedSigners(trustedSigners)
                    .compress(true)
                    .cachePolicyId("4135ea2d-6df8-44a3-9df3-4b5a84be39ad") // Managed-CachingDisabled
                    .allowedMethods(AllowedMethods.builder()
                            .quantity(2)
                            .items(Method.GET, Method.HEAD)
                            .cachedMethods(CachedMethods.builder()
                                    .quantity(2)
                                    .items(Method.GET, Method.HEAD)
                                    .build())
                            .build());

            if (viewerRequestLambdaArn != null && !viewerRequestLambdaArn.isEmpty()) {
                LambdaFunctionAssociation association = LambdaFunctionAssociation.builder()
                        .eventType(EventType.VIEWER_REQUEST)
                        .lambdaFunctionARN(viewerRequestLambdaArn)
                        .includeBody(false)
                        .build();

                LambdaFunctionAssociations associations = LambdaFunctionAssociations.builder()
                        .quantity(1)
                        .items(association)
                        .build();

                dcbBuilder.lambdaFunctionAssociations(associations);
            }

            DefaultCacheBehavior defaultCacheBehavior = dcbBuilder.build();
            
            // Viewer certificate configuration
            ViewerCertificate.Builder viewerCertBuilder = ViewerCertificate.builder();
            
            if (certificateArn != null && !certificateArn.isEmpty()) {
                viewerCertBuilder
                        .acmCertificateArn(certificateArn)
                        .sslSupportMethod(SSLSupportMethod.SNI_ONLY);
            } else {
                viewerCertBuilder.cloudFrontDefaultCertificate(true);
            }
            
            ViewerCertificate viewerCertificate = viewerCertBuilder.build();
            
            // Aliases (CNAMEs)
            Aliases aliases = null;
            if (cnames != null && cnames.length > 0) {
                aliases = Aliases.builder()
                        .quantity(cnames.length)
                        .items(cnames)
                        .build();
            } else {
                aliases = Aliases.builder()
                        .quantity(0)
                        .build();
            }
            
            // Distribution configuration
            DistributionConfig distributionConfig = DistributionConfig.builder()
                    .callerReference(String.valueOf(Instant.now().toEpochMilli()))
                    .comment("Distribution for " + s3BucketName)
                    .enabled(true)
                    .origins(origins)
                    .defaultCacheBehavior(defaultCacheBehavior)
                    .defaultRootObject("index.html")
                    .aliases(aliases)
                    .viewerCertificate(viewerCertificate)
                    .priceClass(PriceClass.PRICE_CLASS_100) // Use only North America and Europe
                    .httpVersion(HttpVersion.HTTP2)
                    .isIPV6Enabled(true)
                    .build();
            
            CreateDistributionRequest request = CreateDistributionRequest.builder()
                    .distributionConfig(distributionConfig)
                    .build();
            
            CreateDistributionResponse response = cloudFrontClient.createDistribution(request);
            String distributionId = response.distribution().id();
            String domainName = response.distribution().domainName();
            
            System.out.println("[CloudFront] Distribution created: " + distributionId);
            System.out.println("[CloudFront] Domain name: " + domainName);
            System.out.println("[CloudFront] Status: " + response.distribution().status());
            
            return distributionId;
        } catch (CloudFrontException e) {
            System.err.println("[CloudFront] Error creating distribution: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Creates an Origin Access Identity for secure S3 access
     */
    public String createOriginAccessIdentity(String comment) {
        try {
            CloudFrontOriginAccessIdentityConfig config = CloudFrontOriginAccessIdentityConfig.builder()
                    .callerReference(String.valueOf(Instant.now().toEpochMilli()))
                    .comment(comment)
                    .build();
            
            CreateCloudFrontOriginAccessIdentityRequest request = 
                    CreateCloudFrontOriginAccessIdentityRequest.builder()
                            .cloudFrontOriginAccessIdentityConfig(config)
                            .build();
            
            CreateCloudFrontOriginAccessIdentityResponse response = 
                    cloudFrontClient.createCloudFrontOriginAccessIdentity(request);
            
            String oaiId = response.cloudFrontOriginAccessIdentity().id();
            System.out.println("[CloudFront] Origin Access Identity created: " + oaiId);
            return oaiId;
        } catch (CloudFrontException e) {
            System.err.println("[CloudFront] Error creating OAI: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Gets distribution details
     */
    public Distribution getDistribution(String distributionId) {
        try {
            GetDistributionRequest request = GetDistributionRequest.builder()
                    .id(distributionId)
                    .build();
            
            GetDistributionResponse response = cloudFrontClient.getDistribution(request);
            Distribution distribution = response.distribution();
            
            System.out.println("[CloudFront] Distribution ID: " + distribution.id());
            System.out.println("[CloudFront] Domain: " + distribution.domainName());
            System.out.println("[CloudFront] Status: " + distribution.status());
            
            return distribution;
        } catch (CloudFrontException e) {
            System.err.println("[CloudFront] Error getting distribution: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Waits for distribution to be deployed
     */
    public void waitForDistributionDeployed(String distributionId) {
        try {
            System.out.println("[CloudFront] Waiting for distribution to be deployed: " + distributionId);
            System.out.println("[CloudFront] This may take 15-20 minutes...");
            
            GetDistributionRequest request = GetDistributionRequest.builder()
                    .id(distributionId)
                    .build();
            
            cloudFrontClient.waiter().waitUntilDistributionDeployed(request);
            System.out.println("[CloudFront] Distribution deployed successfully!");
        } catch (CloudFrontException e) {
            System.err.println("[CloudFront] Error waiting for deployment: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Creates an invalidation to clear the cache
     */
    public String createInvalidation(String distributionId, String... paths) {
        try {
            Paths invalidationPaths = Paths.builder()
                    .quantity(paths.length)
                    .items(paths)
                    .build();
            
            InvalidationBatch batch = InvalidationBatch.builder()
                    .callerReference(String.valueOf(Instant.now().toEpochMilli()))
                    .paths(invalidationPaths)
                    .build();
            
            CreateInvalidationRequest request = CreateInvalidationRequest.builder()
                    .distributionId(distributionId)
                    .invalidationBatch(batch)
                    .build();
            
            CreateInvalidationResponse response = cloudFrontClient.createInvalidation(request);
            String invalidationId = response.invalidation().id();
            
            System.out.println("[CloudFront] Invalidation created: " + invalidationId);
            return invalidationId;
        } catch (CloudFrontException e) {
            System.err.println("[CloudFront] Error creating invalidation: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    public void close() {
        cloudFrontClient.close();
    }
}
