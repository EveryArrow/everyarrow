/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.*;

import java.util.List;

public class CertificateManager {
    
    private final AcmClient acmClient;
    
    public CertificateManager(Region region) {
        this.acmClient = AcmClient.builder()
                .region(region)
                .build();
    }
    
    /**
     * Requests a new SSL/TLS certificate from ACM
     * Note: For CloudFront, certificates must be in us-east-1 region
     */
    public String requestCertificate(String domainName, List<String> subjectAlternativeNames, 
                                     ValidationMethod validationMethod) {
        try {
            RequestCertificateRequest.Builder requestBuilder = RequestCertificateRequest.builder()
                    .domainName(domainName)
                    .validationMethod(validationMethod);
            
            if (subjectAlternativeNames != null && !subjectAlternativeNames.isEmpty()) {
                requestBuilder.subjectAlternativeNames(subjectAlternativeNames);
            }
            
            RequestCertificateResponse response = acmClient.requestCertificate(requestBuilder.build());
            String certificateArn = response.certificateArn();
            System.out.println("[ACM] Certificate requested: " + certificateArn);
            System.out.println("[ACM] Domain: " + domainName);
            System.out.println("[ACM] Validation method: " + validationMethod);
            return certificateArn;
        } catch (AcmException e) {
            System.err.println("[ACM] Error requesting certificate: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Describes a certificate and returns its details
     */
    public CertificateDetail describeCertificate(String certificateArn) {
        try {
            DescribeCertificateRequest request = DescribeCertificateRequest.builder()
                    .certificateArn(certificateArn)
                    .build();
            
            DescribeCertificateResponse response = acmClient.describeCertificate(request);
            CertificateDetail certificate = response.certificate();
            
            System.out.println("[ACM] Certificate status: " + certificate.status());
            System.out.println("[ACM] Domain: " + certificate.domainName());
            
            return certificate;
        } catch (AcmException e) {
            System.err.println("[ACM] Error describing certificate: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Lists all certificates
     */
    public List<CertificateSummary> listCertificates() {
        try {
            ListCertificatesRequest request = ListCertificatesRequest.builder()
                    .build();
            
            ListCertificatesResponse response = acmClient.listCertificates(request);
            List<CertificateSummary> certificates = response.certificateSummaryList();
            
            System.out.println("[ACM] Found " + certificates.size() + " certificates");
            for (CertificateSummary cert : certificates) {
                System.out.println("[ACM] - " + cert.domainName() + " (" + cert.certificateArn() + ")");
            }
            
            return certificates;
        } catch (AcmException e) {
            System.err.println("[ACM] Error listing certificates: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Gets DNS validation records for a certificate
     * These records need to be added to your DNS to validate domain ownership
     */
    public void printDnsValidationRecords(String certificateArn) {
        try {
            CertificateDetail certificate = describeCertificate(certificateArn);
            
            if (certificate.domainValidationOptions() != null) {
                System.out.println("[ACM] DNS Validation Records:");
                for (DomainValidation validation : certificate.domainValidationOptions()) {
                    if (validation.resourceRecord() != null) {
                        ResourceRecord record = validation.resourceRecord();
                        System.out.println("[ACM] Domain: " + validation.domainName());
                        System.out.println("[ACM]   Type: " + record.type());
                        System.out.println("[ACM]   Name: " + record.name());
                        System.out.println("[ACM]   Value: " + record.value());
                        System.out.println();
                    }
                }
            }
        } catch (AcmException e) {
            System.err.println("[ACM] Error getting validation records: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Waits for certificate to be validated and issued
     */
    public void waitForCertificateValidation(String certificateArn) {
        try {
            System.out.println("[ACM] Waiting for certificate validation: " + certificateArn);
            System.out.println("[ACM] This may take several minutes...");
            
            DescribeCertificateRequest request = DescribeCertificateRequest.builder()
                    .certificateArn(certificateArn)
                    .build();
            
            acmClient.waiter().waitUntilCertificateValidated(request);
            System.out.println("[ACM] Certificate validated successfully!");
        } catch (AcmException e) {
            System.err.println("[ACM] Error waiting for validation: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Adds tags to a certificate
     */
    public void tagCertificate(String certificateArn, String key, String value) {
        try {
            Tag tag = Tag.builder()
                    .key(key)
                    .value(value)
                    .build();
            
            AddTagsToCertificateRequest request = AddTagsToCertificateRequest.builder()
                    .certificateArn(certificateArn)
                    .tags(tag)
                    .build();
            
            acmClient.addTagsToCertificate(request);
            System.out.println("[ACM] Tagged certificate with " + key + "=" + value);
        } catch (AcmException e) {
            System.err.println("[ACM] Error tagging certificate: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    public void close() {
        acmClient.close();
    }
}
