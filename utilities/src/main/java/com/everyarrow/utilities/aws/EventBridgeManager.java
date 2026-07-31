/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;

import java.util.List;

public class EventBridgeManager {

    private final EventBridgeClient eventBridgeClient;
    private final LambdaClient lambdaClient;
    private final Region region;

    public EventBridgeManager(Region region) {
        this.region = region;
        this.eventBridgeClient = EventBridgeClient.builder()
                .region(region)
                .build();
        this.lambdaClient = LambdaClient.builder()
                .region(region)
                .build();
    }

    /**
     * Creates or updates a scheduled rule that triggers a Lambda function.
     * Idempotent - if the rule already exists with the same schedule, it will be reused.
     *
     * @param ruleName           Unique name for the rule
     * @param lambdaArn          ARN of the Lambda function to invoke
     * @param scheduleExpression Schedule expression (e.g., "rate(1 hour)", "cron(0 12 * * ? *)")
     * @param description        Optional description for the rule
     * @return The ARN of the created/existing rule
     */
    public String ensureScheduledLambdaTrigger(String ruleName, String lambdaArn, 
                                                String scheduleExpression, String description) {
        try {
            // Check if rule already exists
            String existingRuleArn = findRuleByName(ruleName);
            if (existingRuleArn != null) {
                System.out.println("[EventBridge] Rule already exists, reusing: " + ruleName);
                // Ensure target is set
                ensureTarget(ruleName, lambdaArn);
                return existingRuleArn;
            }

            // Create the rule
            PutRuleRequest putRuleRequest = PutRuleRequest.builder()
                    .name(ruleName)
                    .scheduleExpression(scheduleExpression)
                    .state(RuleState.ENABLED)
                    .description(description != null ? description : "Scheduled trigger for " + lambdaArn)
                    .build();

            PutRuleResponse putRuleResponse = eventBridgeClient.putRule(putRuleRequest);
            String ruleArn = putRuleResponse.ruleArn();
            System.out.println("[EventBridge] Rule created: " + ruleArn);

            // Add Lambda as target
            ensureTarget(ruleName, lambdaArn);

            // Add permission for EventBridge to invoke Lambda
            addLambdaPermission(lambdaArn, ruleArn, ruleName);

            return ruleArn;
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error creating rule: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Creates or updates a scheduled rule with default description.
     */
    public String ensureScheduledLambdaTrigger(String ruleName, String lambdaArn, String scheduleExpression) {
        return ensureScheduledLambdaTrigger(ruleName, lambdaArn, scheduleExpression, null);
    }

    /**
     * Finds a rule by name.
     *
     * @param ruleName The rule name to search for
     * @return The rule ARN if found, null otherwise
     */
    public String findRuleByName(String ruleName) {
        try {
            DescribeRuleRequest request = DescribeRuleRequest.builder()
                    .name(ruleName)
                    .build();
            DescribeRuleResponse response = eventBridgeClient.describeRule(request);
            return response.arn();
        } catch (ResourceNotFoundException e) {
            return null;
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error finding rule: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Ensures a Lambda target is set for the rule.
     */
    private void ensureTarget(String ruleName, String lambdaArn) {
        try {
            // Check existing targets
            ListTargetsByRuleRequest listRequest = ListTargetsByRuleRequest.builder()
                    .rule(ruleName)
                    .build();
            ListTargetsByRuleResponse listResponse = eventBridgeClient.listTargetsByRule(listRequest);
            
            // Check if Lambda is already a target
            for (Target target : listResponse.targets()) {
                if (target.arn().equals(lambdaArn)) {
                    System.out.println("[EventBridge] Target already exists for rule: " + ruleName);
                    return;
                }
            }

            // Add Lambda as target
            Target lambdaTarget = Target.builder()
                    .id("lambda-target-1")
                    .arn(lambdaArn)
                    .build();

            PutTargetsRequest putTargetsRequest = PutTargetsRequest.builder()
                    .rule(ruleName)
                    .targets(lambdaTarget)
                    .build();

            PutTargetsResponse putTargetsResponse = eventBridgeClient.putTargets(putTargetsRequest);
            if (putTargetsResponse.failedEntryCount() > 0) {
                System.err.println("[EventBridge] Failed to add target: " + 
                        putTargetsResponse.failedEntries().get(0).errorMessage());
            } else {
                System.out.println("[EventBridge] Target added to rule: " + ruleName);
            }
        } catch (ResourceNotFoundException e) {
            // Rule doesn't exist yet, target will be added after rule creation
            System.out.println("[EventBridge] Rule not found, will add target after creation");
        }
    }

    /**
     * Adds permission for EventBridge to invoke the Lambda function.
     */
    private void addLambdaPermission(String lambdaArn, String ruleArn, String ruleName) {
        try {
            String statementId = "EventBridge-" + ruleName.replaceAll("[^a-zA-Z0-9_-]", "-");
            
            AddPermissionRequest permissionRequest = AddPermissionRequest.builder()
                    .functionName(lambdaArn)
                    .statementId(statementId)
                    .action("lambda:InvokeFunction")
                    .principal("events.amazonaws.com")
                    .sourceArn(ruleArn)
                    .build();

            lambdaClient.addPermission(permissionRequest);
            System.out.println("[EventBridge] Lambda permission added for rule: " + ruleName);
        } catch (ResourceConflictException e) {
            // Permission already exists
            System.out.println("[EventBridge] Lambda permission already exists for rule: " + ruleName);
        } catch (Exception e) {
            System.err.println("[EventBridge] Error adding Lambda permission: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Disables a scheduled rule.
     *
     * @param ruleName The rule name to disable
     */
    public void disableRule(String ruleName) {
        try {
            DisableRuleRequest request = DisableRuleRequest.builder()
                    .name(ruleName)
                    .build();
            eventBridgeClient.disableRule(request);
            System.out.println("[EventBridge] Rule disabled: " + ruleName);
        } catch (ResourceNotFoundException e) {
            System.out.println("[EventBridge] Rule not found: " + ruleName);
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error disabling rule: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Enables a scheduled rule.
     *
     * @param ruleName The rule name to enable
     */
    public void enableRule(String ruleName) {
        try {
            EnableRuleRequest request = EnableRuleRequest.builder()
                    .name(ruleName)
                    .build();
            eventBridgeClient.enableRule(request);
            System.out.println("[EventBridge] Rule enabled: " + ruleName);
        } catch (ResourceNotFoundException e) {
            System.out.println("[EventBridge] Rule not found: " + ruleName);
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error enabling rule: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Deletes a rule and its targets.
     *
     * @param ruleName The rule name to delete
     */
    public void deleteRule(String ruleName) {
        try {
            // First remove all targets
            ListTargetsByRuleRequest listRequest = ListTargetsByRuleRequest.builder()
                    .rule(ruleName)
                    .build();
            ListTargetsByRuleResponse listResponse = eventBridgeClient.listTargetsByRule(listRequest);
            
            if (!listResponse.targets().isEmpty()) {
                List<String> targetIds = listResponse.targets().stream()
                        .map(Target::id)
                        .toList();
                
                RemoveTargetsRequest removeRequest = RemoveTargetsRequest.builder()
                        .rule(ruleName)
                        .ids(targetIds)
                        .build();
                eventBridgeClient.removeTargets(removeRequest);
                System.out.println("[EventBridge] Targets removed from rule: " + ruleName);
            }

            // Then delete the rule
            DeleteRuleRequest deleteRequest = DeleteRuleRequest.builder()
                    .name(ruleName)
                    .build();
            eventBridgeClient.deleteRule(deleteRequest);
            System.out.println("[EventBridge] Rule deleted: " + ruleName);
        } catch (ResourceNotFoundException e) {
            System.out.println("[EventBridge] Rule not found: " + ruleName);
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error deleting rule: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Lists all rules with optional prefix filter.
     *
     * @param namePrefix Optional prefix to filter rules
     * @return List of rule names
     */
    public List<String> listRules(String namePrefix) {
        try {
            ListRulesRequest.Builder requestBuilder = ListRulesRequest.builder();
            if (namePrefix != null && !namePrefix.isEmpty()) {
                requestBuilder.namePrefix(namePrefix);
            }

            ListRulesResponse response = eventBridgeClient.listRules(requestBuilder.build());
            List<String> ruleNames = response.rules().stream()
                    .map(Rule::name)
                    .toList();

            System.out.println("[EventBridge] Found " + ruleNames.size() + " rules");
            for (String name : ruleNames) {
                System.out.println("[EventBridge] - " + name);
            }
            return ruleNames;
        } catch (EventBridgeException e) {
            System.err.println("[EventBridge] Error listing rules: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public void close() {
        eventBridgeClient.close();
        lambdaClient.close();
    }
}
