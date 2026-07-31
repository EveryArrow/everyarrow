/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.IpAddressType;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Tag;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

import java.util.List;
import java.util.stream.Collectors;

public class AlbManager implements AutoCloseable {
    
    private final ElasticLoadBalancingV2Client elbClient;
    private final Ec2Client ec2Client;
    private final Region region;
    
    public AlbManager(Region region) {
        this.region = region;
        this.elbClient = ElasticLoadBalancingV2Client.builder()
            .region(region)
            .build();
        this.ec2Client = Ec2Client.builder()
            .region(region)
            .build();
    }
    
    public String createApplicationLoadBalancer(
            String name,
            List<String> subnetIds,
            List<String> securityGroupIds,
            boolean internetFacing) {
        
        System.out.println("Creating Application Load Balancer: " + name);
        
        // Check if ALB already exists
        String existingAlbArn = findLoadBalancerByName(name);
        if (existingAlbArn != null) {
            System.out.println("ALB already exists, skipping creation: " + name);
            System.out.println("  ARN: " + existingAlbArn);
            return existingAlbArn;
        }
        
        try {
            CreateLoadBalancerRequest request = CreateLoadBalancerRequest.builder()
                .name(name)
                .subnets(subnetIds)
                .securityGroups(securityGroupIds)
                .scheme(internetFacing ? "internet-facing" : "internal")
                .type("application")
                .ipAddressType(IpAddressType.IPV4)
                .tags(Tag.builder()
                    .key("Name")
                    .value(name)
                    .build())
                .build();
            
            CreateLoadBalancerResponse response = elbClient.createLoadBalancer(request);
            String albArn = response.loadBalancers().get(0).loadBalancerArn();
            String albDnsName = response.loadBalancers().get(0).dnsName();
            
            System.out.println("ALB created successfully");
            System.out.println("  ARN: " + albArn);
            System.out.println("  DNS Name: " + albDnsName);
            
            return albArn;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ALB: " + e.getMessage(), e);
        }
    }
    
    public String createTargetGroup(
            String name,
            String vpcId,
            int port,
            ProtocolEnum protocol,
            TargetTypeEnum targetType) {
        
        System.out.println("Creating Target Group: " + name);
        
        // Check if target group already exists
        String existingTgArn = findTargetGroupByName(name);
        if (existingTgArn != null) {
            System.out.println("Target Group already exists, skipping creation: " + name);
            System.out.println("  ARN: " + existingTgArn);
            return existingTgArn;
        }
        
        try {
            CreateTargetGroupRequest request = CreateTargetGroupRequest.builder()
                .name(name)
                .protocol(protocol)
                .port(port)
                .vpcId(vpcId)
                .targetType(targetType)
                .healthCheckEnabled(true)
                .healthCheckProtocol(protocol)
                .healthCheckPath("/health")
                .healthCheckIntervalSeconds(30)
                .healthCheckTimeoutSeconds(5)
                .healthyThresholdCount(2)
                .unhealthyThresholdCount(2)
                .matcher(Matcher.builder().httpCode("200,404").build())
                .tags(Tag.builder()
                    .key("Name")
                    .value(name)
                    .build())
                .build();
            
            CreateTargetGroupResponse response = elbClient.createTargetGroup(request);
            String targetGroupArn = response.targetGroups().get(0).targetGroupArn();
            
            System.out.println("Target Group created successfully");
            System.out.println("  ARN: " + targetGroupArn);
            
            return targetGroupArn;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Target Group: " + e.getMessage(), e);
        }
    }
    
    public void registerTargets(String targetGroupArn, List<String> instanceIds) {
        System.out.println("Registering targets to Target Group");
        
        try {
            List<TargetDescription> targets = instanceIds.stream()
                .map(id -> TargetDescription.builder().id(id).build())
                .collect(Collectors.toList());
            
            RegisterTargetsRequest request = RegisterTargetsRequest.builder()
                .targetGroupArn(targetGroupArn)
                .targets(targets)
                .build();
            
            elbClient.registerTargets(request);
            
            System.out.println("Targets registered successfully: " + instanceIds);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to register targets: " + e.getMessage(), e);
        }
    }
    
    public String createListener(
            String loadBalancerArn,
            int port,
            ProtocolEnum protocol,
            String targetGroupArn) {
        return createListener(loadBalancerArn, port, protocol, targetGroupArn, null);
    }
    
    public String createListener(
            String loadBalancerArn,
            int port,
            ProtocolEnum protocol,
            String targetGroupArn,
            String certificateArn) {
        
        System.out.println("Creating Listener on port " + port + " with protocol " + protocol);
        
        // Check if listener already exists for this port
        String existingListenerArn = findListenerByPort(loadBalancerArn, port);
        if (existingListenerArn != null) {
            System.out.println("Listener already exists on port " + port + ", skipping creation");
            System.out.println("  ARN: " + existingListenerArn);
            return existingListenerArn;
        }
        
        try {
            Action defaultAction = Action.builder()
                .type("forward")
                .targetGroupArn(targetGroupArn)
                .build();
            
            CreateListenerRequest.Builder requestBuilder = CreateListenerRequest.builder()
                .loadBalancerArn(loadBalancerArn)
                .protocol(protocol)
                .port(port)
                .defaultActions(defaultAction);
            
            // Add certificate for HTTPS
            if (certificateArn != null && !certificateArn.isEmpty() && 
                (protocol == ProtocolEnum.HTTPS || protocol == ProtocolEnum.TLS)) {
                requestBuilder.certificates(Certificate.builder()
                    .certificateArn(certificateArn)
                    .build());
                System.out.println("  Using certificate: " + certificateArn);
            }
            
            CreateListenerResponse response = elbClient.createListener(requestBuilder.build());
            String listenerArn = response.listeners().get(0).listenerArn();
            
            System.out.println("Listener created successfully");
            System.out.println("  ARN: " + listenerArn);
            
            return listenerArn;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Listener: " + e.getMessage(), e);
        }
    }
    
    public String createRedirectListener(
            String loadBalancerArn,
            int fromPort,
            int toPort,
            ProtocolEnum fromProtocol) {
        
        System.out.println("Creating Redirect Listener from port " + fromPort + " to port " + toPort);
        
        // Check if listener already exists for this port
        String existingListenerArn = findListenerByPort(loadBalancerArn, fromPort);
        if (existingListenerArn != null) {
            System.out.println("Listener already exists on port " + fromPort + ", skipping creation");
            System.out.println("  ARN: " + existingListenerArn);
            return existingListenerArn;
        }
        
        try {
            RedirectActionConfig redirectConfig = RedirectActionConfig.builder()
                .protocol("HTTPS")
                .port(String.valueOf(toPort))
                .statusCode("HTTP_301")
                .build();
            
            Action redirectAction = Action.builder()
                .type("redirect")
                .redirectConfig(redirectConfig)
                .build();
            
            CreateListenerRequest request = CreateListenerRequest.builder()
                .loadBalancerArn(loadBalancerArn)
                .protocol(fromProtocol)
                .port(fromPort)
                .defaultActions(redirectAction)
                .build();
            
            CreateListenerResponse response = elbClient.createListener(request);
            String listenerArn = response.listeners().get(0).listenerArn();
            
            System.out.println("Redirect Listener created successfully");
            System.out.println("  ARN: " + listenerArn);
            System.out.println("  Redirects: " + fromProtocol + ":" + fromPort + " -> HTTPS:" + toPort);
            
            return listenerArn;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Redirect Listener: " + e.getMessage(), e);
        }
    }
    
    public String getLoadBalancerDnsName(String loadBalancerArn) {
        try {
            DescribeLoadBalancersRequest request = DescribeLoadBalancersRequest.builder()
                .loadBalancerArns(loadBalancerArn)
                .build();
            
            DescribeLoadBalancersResponse response = elbClient.describeLoadBalancers(request);
            
            if (response.loadBalancers().isEmpty()) {
                throw new RuntimeException("Load balancer not found: " + loadBalancerArn);
            }
            
            return response.loadBalancers().get(0).dnsName();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ALB DNS name: " + e.getMessage(), e);
        }
    }
    
    public String findLoadBalancerByName(String name) {
        try {
            DescribeLoadBalancersResponse response = elbClient.describeLoadBalancers();
            
            return response.loadBalancers().stream()
                .filter(lb -> lb.loadBalancerName().equals(name))
                .findFirst()
                .map(LoadBalancer::loadBalancerArn)
                .orElse(null);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to find ALB by name: " + e.getMessage(), e);
        }
    }
    
    public List<String> getDefaultSubnets(String vpcId) {
        try {
            DescribeSubnetsRequest request = DescribeSubnetsRequest.builder()
                .filters(Filter.builder()
                    .name("vpc-id")
                    .values(vpcId)
                    .build())
                .build();
            
            DescribeSubnetsResponse response = ec2Client.describeSubnets(request);
            
            List<String> subnetIds = response.subnets().stream()
                .map(Subnet::subnetId)
                .collect(Collectors.toList());
            
            if (subnetIds.size() < 2) {
                throw new RuntimeException("ALB requires at least 2 subnets in different availability zones");
            }
            
            return subnetIds;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get subnets: " + e.getMessage(), e);
        }
    }
    
    public String getDefaultVpcId() {
        try {
            DescribeVpcsRequest request = DescribeVpcsRequest.builder()
                .filters(Filter.builder()
                    .name("isDefault")
                    .values("true")
                    .build())
                .build();
            
            DescribeVpcsResponse response = ec2Client.describeVpcs(request);
            
            if (response.vpcs().isEmpty()) {
                throw new RuntimeException("No default VPC found");
            }
            
            return response.vpcs().get(0).vpcId();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default VPC: " + e.getMessage(), e);
        }
    }
    
    public String createSecurityGroup(String name, String description, String vpcId) {
        try {
            CreateSecurityGroupRequest request = CreateSecurityGroupRequest.builder()
                .groupName(name)
                .description(description)
                .vpcId(vpcId)
                .build();
            
            CreateSecurityGroupResponse response = ec2Client.createSecurityGroup(request);
            String securityGroupId = response.groupId();
            
            System.out.println("Security Group created: " + securityGroupId);
            
            authorizeSecurityGroupIngress(securityGroupId, 80, "0.0.0.0/0");
            authorizeSecurityGroupIngress(securityGroupId, 443, "0.0.0.0/0");
            
            return securityGroupId;
            
        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidGroup.Duplicate")) {
                System.out.println("Security group already exists, finding it...");
                return findSecurityGroupByName(name, vpcId);
            }
            throw new RuntimeException("Failed to create security group: " + e.getMessage(), e);
        }
    }
    
    private String findSecurityGroupByName(String name, String vpcId) {
        try {
            DescribeSecurityGroupsRequest request = DescribeSecurityGroupsRequest.builder()
                .filters(
                    Filter.builder().name("group-name").values(name).build(),
                    Filter.builder().name("vpc-id").values(vpcId).build()
                )
                .build();
            
            DescribeSecurityGroupsResponse response = ec2Client.describeSecurityGroups(request);
            
            if (response.securityGroups().isEmpty()) {
                throw new RuntimeException("Security group not found: " + name);
            }
            
            return response.securityGroups().get(0).groupId();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to find security group: " + e.getMessage(), e);
        }
    }
    
    private void authorizeSecurityGroupIngress(String securityGroupId, int port, String cidr) {
        try {
            IpPermission permission = IpPermission.builder()
                .ipProtocol("tcp")
                .fromPort(port)
                .toPort(port)
                .ipRanges(IpRange.builder().cidrIp(cidr).build())
                .build();
            
            AuthorizeSecurityGroupIngressRequest request = AuthorizeSecurityGroupIngressRequest.builder()
                .groupId(securityGroupId)
                .ipPermissions(permission)
                .build();
            
            ec2Client.authorizeSecurityGroupIngress(request);
            
            System.out.println("  Authorized ingress: port " + port + " from " + cidr);
            
        } catch (Ec2Exception e) {
            if (!e.awsErrorDetails().errorCode().equals("InvalidPermission.Duplicate")) {
                throw new RuntimeException("Failed to authorize ingress: " + e.getMessage(), e);
            }
        }
    }
    
    public void deleteLoadBalancer(String loadBalancerArn) {
        try {
            DeleteLoadBalancerRequest request = DeleteLoadBalancerRequest.builder()
                .loadBalancerArn(loadBalancerArn)
                .build();
            
            elbClient.deleteLoadBalancer(request);
            System.out.println("Load balancer deleted: " + loadBalancerArn);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete load balancer: " + e.getMessage(), e);
        }
    }
    
    public void deleteTargetGroup(String targetGroupArn) {
        try {
            DeleteTargetGroupRequest request = DeleteTargetGroupRequest.builder()
                .targetGroupArn(targetGroupArn)
                .build();
            
            elbClient.deleteTargetGroup(request);
            System.out.println("Target group deleted: " + targetGroupArn);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete target group: " + e.getMessage(), e);
        }
    }
    
    public String findTargetGroupByName(String name) {
        try {
            DescribeTargetGroupsResponse response = elbClient.describeTargetGroups();
            
            return response.targetGroups().stream()
                .filter(tg -> tg.targetGroupName().equals(name))
                .findFirst()
                .map(TargetGroup::targetGroupArn)
                .orElse(null);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to find target group by name: " + e.getMessage(), e);
        }
    }
    
    public String findListenerByPort(String loadBalancerArn, int port) {
        try {
            DescribeListenersRequest request = DescribeListenersRequest.builder()
                .loadBalancerArn(loadBalancerArn)
                .build();
            
            DescribeListenersResponse response = elbClient.describeListeners(request);
            
            return response.listeners().stream()
                .filter(listener -> listener.port() != null && listener.port() == port)
                .findFirst()
                .map(Listener::listenerArn)
                .orElse(null);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to find listener by port: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() {
        if (elbClient != null) {
            elbClient.close();
        }
        if (ec2Client != null) {
            ec2Client.close();
        }
    }
}
