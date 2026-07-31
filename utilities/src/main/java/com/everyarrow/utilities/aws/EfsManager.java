/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.efs.EfsClient;
import software.amazon.awssdk.services.efs.model.*;

import java.util.List;

public class EfsManager {

    private final EfsClient efsClient;

    public EfsManager(Region region) {
        this.efsClient = EfsClient.builder()
                .region(region)
                .build();
    }

    /**
     * Ensures an EFS file system exists with the given name tag.
     * If a file system with that name already exists, returns its ID.
     * Otherwise, creates a new encrypted file system.
     *
     * @param name      The Name tag value for the file system
     * @param kmsKeyArn Optional KMS key ARN for encryption (null for AWS managed key)
     * @return The file system ID
     */
    public String ensureFileSystem(String name, String kmsKeyArn) {
        // Check if file system with this name already exists
        String existingFsId = findFileSystemByName(name);
        if (existingFsId != null) {
            System.out.println("[EFS] File system already exists with name '" + name + "': " + existingFsId);
            return existingFsId;
        }

        // Create new file system
        try {
            CreateFileSystemRequest.Builder requestBuilder = CreateFileSystemRequest.builder()
                    .creationToken(name) // Idempotency token
                    .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                    .throughputMode(ThroughputMode.BURSTING)
                    .encrypted(true)
                    .tags(Tag.builder().key("Name").value(name).build());

            if (kmsKeyArn != null && !kmsKeyArn.isEmpty()) {
                requestBuilder.kmsKeyId(kmsKeyArn);
            }

            CreateFileSystemResponse response = efsClient.createFileSystem(requestBuilder.build());
            String fileSystemId = response.fileSystemId();

            System.out.println("[EFS] File system created: " + fileSystemId);
            System.out.println("[EFS] Name: " + name);
            System.out.println("[EFS] Encrypted: " + response.encrypted());
            System.out.println("[EFS] LifeCycleState: " + response.lifeCycleState());

            // Wait for file system to become available
            waitForFileSystemAvailable(fileSystemId);

            return fileSystemId;
        } catch (FileSystemAlreadyExistsException e) {
            // CreationToken matched an existing file system
            System.out.println("[EFS] File system with creation token '" + name + "' already exists, reusing");
            String fsId = findFileSystemByName(name);
            if (fsId == null) {
                // Fallback: list all and find by creation token
                fsId = findFileSystemByCreationToken(name);
            }
            return fsId;
        } catch (EfsException e) {
            System.err.println("[EFS] Error creating file system: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Finds a file system by its Name tag.
     *
     * @param name The Name tag value
     * @return The file system ID, or null if not found
     */
    public String findFileSystemByName(String name) {
        try {
            DescribeFileSystemsResponse response = efsClient.describeFileSystems();
            for (FileSystemDescription fs : response.fileSystems()) {
                // Check tags for Name
                DescribeTagsResponse tagsResponse = efsClient.describeTags(
                        DescribeTagsRequest.builder().fileSystemId(fs.fileSystemId()).build()
                );
                for (Tag tag : tagsResponse.tags()) {
                    if ("Name".equals(tag.key()) && name.equals(tag.value())) {
                        return fs.fileSystemId();
                    }
                }
            }
            return null;
        } catch (EfsException e) {
            System.err.println("[EFS] Error finding file system by name: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Finds a file system by its creation token.
     */
    private String findFileSystemByCreationToken(String creationToken) {
        try {
            DescribeFileSystemsResponse response = efsClient.describeFileSystems(
                    DescribeFileSystemsRequest.builder().creationToken(creationToken).build()
            );
            if (!response.fileSystems().isEmpty()) {
                return response.fileSystems().get(0).fileSystemId();
            }
            return null;
        } catch (EfsException e) {
            return null;
        }
    }

    /**
     * Waits for a file system to become available.
     */
    public void waitForFileSystemAvailable(String fileSystemId) {
        System.out.println("[EFS] Waiting for file system to become available: " + fileSystemId);
        int maxAttempts = 30;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                DescribeFileSystemsResponse response = efsClient.describeFileSystems(
                        DescribeFileSystemsRequest.builder().fileSystemId(fileSystemId).build()
                );
                if (!response.fileSystems().isEmpty()) {
                    LifeCycleState state = response.fileSystems().get(0).lifeCycleState();
                    if (state == LifeCycleState.AVAILABLE) {
                        System.out.println("[EFS] File system is now available: " + fileSystemId);
                        return;
                    }
                    System.out.println("[EFS] File system state: " + state + ", waiting...");
                }
                Thread.sleep(2000);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for file system", e);
            }
        }
        throw new RuntimeException("Timeout waiting for file system to become available: " + fileSystemId);
    }

    /**
     * Ensures a mount target exists for the given file system in the specified subnet.
     * If a mount target already exists in that subnet, returns its ID.
     *
     * @param fileSystemId    The EFS file system ID
     * @param subnetId        The subnet ID for the mount target
     * @param securityGroupIds List of security group IDs to associate
     * @return The mount target ID
     */
    public String ensureMountTarget(String fileSystemId, String subnetId, List<String> securityGroupIds) {
        // Check if mount target already exists in this subnet
        String existingMtId = findMountTargetBySubnet(fileSystemId, subnetId);
        if (existingMtId != null) {
            System.out.println("[EFS] Mount target already exists in subnet " + subnetId + ": " + existingMtId);
            return existingMtId;
        }

        // Create new mount target
        try {
            CreateMountTargetRequest.Builder requestBuilder = CreateMountTargetRequest.builder()
                    .fileSystemId(fileSystemId)
                    .subnetId(subnetId);

            if (securityGroupIds != null && !securityGroupIds.isEmpty()) {
                requestBuilder.securityGroups(securityGroupIds);
            }

            CreateMountTargetResponse response = efsClient.createMountTarget(requestBuilder.build());
            String mountTargetId = response.mountTargetId();

            System.out.println("[EFS] Mount target created: " + mountTargetId);
            System.out.println("[EFS] Subnet: " + subnetId);
            System.out.println("[EFS] IP Address: " + response.ipAddress());

            return mountTargetId;
        } catch (MountTargetConflictException e) {
            // Mount target already exists for this file system in this AZ/subnet
            System.out.println("[EFS] Mount target already exists in subnet " + subnetId + ", reusing");
            return findMountTargetBySubnet(fileSystemId, subnetId);
        } catch (EfsException e) {
            System.err.println("[EFS] Error creating mount target: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Finds a mount target for a file system in a specific subnet.
     *
     * @param fileSystemId The EFS file system ID
     * @param subnetId     The subnet ID
     * @return The mount target ID, or null if not found
     */
    public String findMountTargetBySubnet(String fileSystemId, String subnetId) {
        try {
            DescribeMountTargetsResponse response = efsClient.describeMountTargets(
                    DescribeMountTargetsRequest.builder().fileSystemId(fileSystemId).build()
            );
            for (MountTargetDescription mt : response.mountTargets()) {
                if (subnetId.equals(mt.subnetId())) {
                    return mt.mountTargetId();
                }
            }
            return null;
        } catch (EfsException e) {
            System.err.println("[EFS] Error finding mount target: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Gets all mount targets for a file system.
     *
     * @param fileSystemId The EFS file system ID
     * @return List of mount target descriptions
     */
    public List<MountTargetDescription> getMountTargets(String fileSystemId) {
        try {
            DescribeMountTargetsResponse response = efsClient.describeMountTargets(
                    DescribeMountTargetsRequest.builder().fileSystemId(fileSystemId).build()
            );
            return response.mountTargets();
        } catch (EfsException e) {
            System.err.println("[EFS] Error getting mount targets: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Tags a file system with a Name tag if not already set.
     *
     * @param fileSystemId The EFS file system ID
     * @param name         The Name tag value
     */
    public void tagFileSystem(String fileSystemId, String name) {
        try {
            // Check if Name tag already exists
            DescribeTagsResponse tagsResponse = efsClient.describeTags(
                    DescribeTagsRequest.builder().fileSystemId(fileSystemId).build()
            );
            for (Tag tag : tagsResponse.tags()) {
                if ("Name".equals(tag.key())) {
                    System.out.println("[EFS] File system already has Name tag: " + tag.value());
                    return;
                }
            }

            // Add Name tag
            efsClient.createTags(CreateTagsRequest.builder()
                    .fileSystemId(fileSystemId)
                    .tags(Tag.builder().key("Name").value(name).build())
                    .build());
            System.out.println("[EFS] Tagged file system " + fileSystemId + " with Name=" + name);
        } catch (EfsException e) {
            System.err.println("[EFS] Error tagging file system: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Describes a file system by ID.
     *
     * @param fileSystemId The EFS file system ID
     * @return The file system description, or null if not found
     */
    public FileSystemDescription describeFileSystem(String fileSystemId) {
        try {
            DescribeFileSystemsResponse response = efsClient.describeFileSystems(
                    DescribeFileSystemsRequest.builder().fileSystemId(fileSystemId).build()
            );
            if (!response.fileSystems().isEmpty()) {
                FileSystemDescription fs = response.fileSystems().get(0);
                System.out.println("[EFS] FileSystemId: " + fs.fileSystemId());
                System.out.println("[EFS] LifeCycleState: " + fs.lifeCycleState());
                System.out.println("[EFS] Encrypted: " + fs.encrypted());
                System.out.println("[EFS] SizeInBytes: " + fs.sizeInBytes().value());
                return fs;
            }
            return null;
        } catch (FileSystemNotFoundException e) {
            return null;
        } catch (EfsException e) {
            System.err.println("[EFS] Error describing file system: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public void close() {
        efsClient.close();
    }
}
