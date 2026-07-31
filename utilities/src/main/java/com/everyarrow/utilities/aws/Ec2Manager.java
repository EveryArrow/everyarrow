/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public class Ec2Manager {
    private static final String PUBLIC_IPV4_CIDR = "0.0.0.0/0";
    private static final String PUBLIC_IPV6_CIDR = "::/0";
    private static final Set<Integer> SENSITIVE_PORTS = Set.of(22, 5432, 2049);

    private final Ec2Client ec2Client;

    public Ec2Manager(Region region) {
        this.ec2Client = Ec2Client.builder()
                .region(region)
                .build();
    }

    /**
     * Creates a security group with specified rules
     */
    public String createSecurityGroup(String groupName, String description, String vpcId) {
        try {
            CreateSecurityGroupRequest request = CreateSecurityGroupRequest.builder()
                    .groupName(groupName)
                    .description(description)
                    .vpcId(vpcId)
                    .build();

            CreateSecurityGroupResponse response = ec2Client.createSecurityGroup(request);
            String groupId = response.groupId();
            System.out.println("[EC2] Security group created: " + groupId);
            return groupId;
        } catch (Ec2Exception e) {
            String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
            if ("InvalidGroup.Duplicate".equals(errorCode)) {
                // Security group with this name already exists in the VPC; look it up and reuse
                // it
                System.out.println("[EC2] Security group already exists, reusing: " + groupName);
                DescribeSecurityGroupsRequest describeRequest = DescribeSecurityGroupsRequest.builder()
                        .filters(
                                Filter.builder().name("group-name").values(groupName).build(),
                                Filter.builder().name("vpc-id").values(vpcId).build())
                        .build();
                DescribeSecurityGroupsResponse describeResponse = ec2Client.describeSecurityGroups(describeRequest);
                if (!describeResponse.securityGroups().isEmpty()) {
                    String existingGroupId = describeResponse.securityGroups().get(0).groupId();
                    System.out.println("[EC2] Using existing security group: " + existingGroupId);
                    return existingGroupId;
                }
            }
            System.err.println("[EC2] Error creating security group: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Adds public web ingress only. Sensitive ports such as SSH, Postgres, and
     * NFS require explicit scoped rules through the overloads below.
     */
    public void authorizeSecurityGroupIngress(String groupId) {
        authorizeWebSecurityGroupIngress(groupId, PUBLIC_IPV4_CIDR);
    }

    public void authorizeWebSecurityGroupIngress(String groupId, String cidr) {
        try {
            IpPermission httpPermission = IpPermission.builder()
                    .ipProtocol("tcp")
                    .fromPort(80)
                    .toPort(80)
                    .ipRanges(IpRange.builder().cidrIp(cidr).build())
                    .build();

            IpPermission httpsPermission = IpPermission.builder()
                    .ipProtocol("tcp")
                    .fromPort(443)
                    .toPort(443)
                    .ipRanges(IpRange.builder().cidrIp(cidr).build())
                    .build();

            AuthorizeSecurityGroupIngressRequest request = AuthorizeSecurityGroupIngressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(httpPermission, httpsPermission)
                    .build();

            ec2Client.authorizeSecurityGroupIngress(request);
            System.out.println("[EC2] Web ingress rules added for: " + groupId + " from " + cidr);
        } catch (Ec2Exception e) {
            handleIngressError(e, groupId);
        }
    }

    /**
     * Adds a specific ingress rule to a security group
     */
    public void authorizeSecurityGroupIngress(String groupId, int port, String protocol, String cidr) {
        validateIngressRule(port, cidr);
        try {
            IpPermission permission = IpPermission.builder()
                    .ipProtocol(protocol)
                    .fromPort(port)
                    .toPort(port)
                    .ipRanges(IpRange.builder().cidrIp(cidr).build())
                    .build();

            AuthorizeSecurityGroupIngressRequest request = AuthorizeSecurityGroupIngressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(permission)
                    .build();

            ec2Client.authorizeSecurityGroupIngress(request);
            System.out
                    .println("[EC2] Ingress rule added to " + groupId + ": " + protocol + "/" + port + " from " + cidr);
        } catch (Ec2Exception e) {
            handleIngressError(e, groupId);
        }
    }

    public void authorizeSecurityGroupIngressFromSecurityGroup(String groupId,
                                                              int port,
                                                              String protocol,
                                                              String sourceSecurityGroupId) {
        if (sourceSecurityGroupId == null || sourceSecurityGroupId.isBlank()) {
            throw new IllegalArgumentException("sourceSecurityGroupId is required for scoped ingress");
        }
        try {
            IpPermission permission = IpPermission.builder()
                    .ipProtocol(protocol)
                    .fromPort(port)
                    .toPort(port)
                    .userIdGroupPairs(UserIdGroupPair.builder().groupId(sourceSecurityGroupId).build())
                    .build();

            AuthorizeSecurityGroupIngressRequest request = AuthorizeSecurityGroupIngressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(permission)
                    .build();

            ec2Client.authorizeSecurityGroupIngress(request);
            System.out.println("[EC2] Ingress rule added to " + groupId + ": " + protocol + "/" + port
                    + " from security group " + sourceSecurityGroupId);
        } catch (Ec2Exception e) {
            handleIngressError(e, groupId);
        }
    }

    static void validateIngressRule(int port, String cidr) {
        if (isSensitivePort(port) && isPublicCidr(cidr)) {
            throw new IllegalArgumentException(
                    "Refusing public ingress for sensitive port " + port + ". Provide a scoped CIDR or source security group.");
        }
    }

    static boolean isSensitivePort(int port) {
        return SENSITIVE_PORTS.contains(port);
    }

    static boolean isPublicCidr(String cidr) {
        return PUBLIC_IPV4_CIDR.equals(cidr) || PUBLIC_IPV6_CIDR.equals(cidr);
    }

    private void handleIngressError(Ec2Exception e, String groupId) {
        String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
        if ("InvalidPermission.Duplicate".equals(errorCode)) {
            // Rules already exist; treat as success for idempotency
            System.out.println("[EC2] Security group rules already exist for: " + groupId + ", skipping");
            return;
        }
        System.err.println("[EC2] Error authorizing ingress: " + e.awsErrorDetails().errorMessage());
        throw e;
    }

    /**
     * Creates a key pair for SSH access
     */
    public String createKeyPair(String keyName) {
        try {
            CreateKeyPairRequest request = CreateKeyPairRequest.builder()
                    .keyName(keyName)
                    .build();

            CreateKeyPairResponse response = ec2Client.createKeyPair(request);
            String privateKey = response.keyMaterial();
            System.out.println("[EC2] Key pair created: " + keyName);
            System.out.println("[EC2] Private key material returned to caller; it was not printed.");
            return privateKey;
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error creating key pair: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public boolean keyPairExists(String keyName) {
        try {
            DescribeKeyPairsRequest request = DescribeKeyPairsRequest.builder()
                    .keyNames(keyName)
                    .build();
            ec2Client.describeKeyPairs(request);
            return true;
        } catch (Ec2Exception e) {
            String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
            if ("InvalidKeyPair.NotFound".equals(errorCode)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Launches an EC2 instance
     */
    public String launchInstance(String amiId, InstanceType instanceType, String keyName,
            String securityGroupId, String userData) {
        try {
            RunInstancesRequest.Builder requestBuilder = RunInstancesRequest.builder()
                    .imageId(amiId)
                    .instanceType(instanceType)
                    .keyName(keyName)
                    .securityGroupIds(securityGroupId)
                    .minCount(1)
                    .maxCount(1);

            // Add user data if provided
            if (userData != null && !userData.isEmpty()) {
                String encodedUserData = Base64.getEncoder().encodeToString(userData.getBytes());
                requestBuilder.userData(encodedUserData);
            }

            RunInstancesResponse response = ec2Client.runInstances(requestBuilder.build());
            String instanceId = response.instances().get(0).instanceId();
            System.out.println("[EC2] Instance launched: " + instanceId);
            return instanceId;
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error launching instance: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Allocates an Elastic IP address
     */
    public String allocateElasticIp() {
        try {
            AllocateAddressRequest request = AllocateAddressRequest.builder()
                    .domain(DomainType.VPC)
                    .build();

            AllocateAddressResponse response = ec2Client.allocateAddress(request);
            String allocationId = response.allocationId();
            String publicIp = response.publicIp();
            System.out.println("[EC2] Elastic IP allocated: " + publicIp + " (ID: " + allocationId + ")");
            return allocationId;
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error allocating Elastic IP: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Associates an Elastic IP with an instance
     */
    public void associateElasticIp(String allocationId, String instanceId) {
        try {
            AssociateAddressRequest request = AssociateAddressRequest.builder()
                    .allocationId(allocationId)
                    .instanceId(instanceId)
                    .build();

            ec2Client.associateAddress(request);
            System.out.println("[EC2] Elastic IP associated with instance: " + instanceId);
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error associating Elastic IP: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Tags an EC2 resource
     */
    public void tagResource(String resourceId, String key, String value) {
        try {
            Tag tag = Tag.builder()
                    .key(key)
                    .value(value)
                    .build();

            CreateTagsRequest request = CreateTagsRequest.builder()
                    .resources(resourceId)
                    .tags(tag)
                    .build();

            ec2Client.createTags(request);
            System.out.println("[EC2] Tagged resource " + resourceId + " with " + key + "=" + value);
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error tagging resource: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Gets the default VPC ID
     */
    public String getDefaultVpcId() {
        try {
            DescribeVpcsRequest request = DescribeVpcsRequest.builder()
                    .filters(Filter.builder()
                            .name("isDefault")
                            .values("true")
                            .build())
                    .build();

            DescribeVpcsResponse response = ec2Client.describeVpcs(request);
            if (!response.vpcs().isEmpty()) {
                String vpcId = response.vpcs().get(0).vpcId();
                System.out.println("[EC2] Default VPC ID: " + vpcId);
                return vpcId;
            }
            throw new RuntimeException("No default VPC found");
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error getting default VPC: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Gets all subnet IDs in the default VPC
     */
    public List<String> getDefaultVpcSubnetIds() {
        try {
            String vpcId = getDefaultVpcId();
            DescribeSubnetsRequest request = DescribeSubnetsRequest.builder()
                    .filters(Filter.builder()
                            .name("vpc-id")
                            .values(vpcId)
                            .build())
                    .build();

            DescribeSubnetsResponse response = ec2Client.describeSubnets(request);
            List<String> subnetIds = new java.util.ArrayList<>();
            for (Subnet subnet : response.subnets()) {
                subnetIds.add(subnet.subnetId());
                System.out.println("[EC2] Found subnet: " + subnet.subnetId() + " in AZ " + subnet.availabilityZone());
            }
            return subnetIds;
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error getting default VPC subnets: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Waits for instance to be in running state
     */
    public void waitForInstanceRunning(String instanceId) {
        try {
            System.out.println("[EC2] Waiting for instance to be running: " + instanceId);
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            ec2Client.waiter().waitUntilInstanceRunning(request);
            System.out.println("[EC2] Instance is now running: " + instanceId);
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error waiting for instance: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public String findInstanceByName(String instanceName) {
        try {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(
                            Filter.builder()
                                    .name("tag:Name")
                                    .values(instanceName)
                                    .build(),
                            Filter.builder()
                                    .name("instance-state-name")
                                    .values("pending", "running", "stopping", "stopped")
                                    .build())
                    .build();
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            if (response.reservations() != null) {
                for (Reservation reservation : response.reservations()) {
                    if (reservation.instances() != null && !reservation.instances().isEmpty()) {
                        return reservation.instances().get(0).instanceId();
                    }
                }
            }
            return null;
        } catch (Ec2Exception e) {
            System.err.println("[EC2] Error finding instance by name: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public void close() {
        ec2Client.close();
    }
}
