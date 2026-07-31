/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws.ses;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.SesV2ClientBuilder;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.Objects;

public class SesEmailService {

    static final String SES_REGION_PROPERTY = "ses.region";
    static final String AWS_REGION_PROPERTY = "aws.region";
    static final String SES_REGION_ENV = "SES_REGION";
    static final String AWS_REGION_ENV = "AWS_REGION";
    static final String AWS_DEFAULT_REGION_ENV = "AWS_DEFAULT_REGION";

    private final SesV2Client ses;
    private final DomainRotator rotator;

    public SesEmailService(DomainRotator rotator) {
        this(rotator, System.getenv("AWS_PROFILE"), resolveRegion());
    }
    
    public SesEmailService(DomainRotator rotator, String profile) {
        this(rotator, profile, resolveRegion());
    }

    public SesEmailService(DomainRotator rotator, Region region) {
        this(rotator, System.getenv("AWS_PROFILE"), region);
    }

    public SesEmailService(DomainRotator rotator, String profile, Region region) {
        this.rotator = Objects.requireNonNull(rotator, "rotator");
        
        SesV2ClientBuilder builder = SesV2Client.builder()
                .region(Objects.requireNonNull(region, "region"));
        
        if (profile != null && !profile.isEmpty()) {
            builder.credentialsProvider(
                software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider.create(profile)
            );
        }
        
        this.ses = builder.build();
    }

    static Region resolveRegion() {
        String region = firstNonBlank(
                System.getProperty(SES_REGION_PROPERTY),
                System.getenv(SES_REGION_ENV),
                System.getProperty(AWS_REGION_PROPERTY),
                System.getenv(AWS_REGION_ENV),
                System.getenv(AWS_DEFAULT_REGION_ENV));

        return region == null ? Region.US_EAST_1 : Region.of(region);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    // Optional but good practice
    public boolean isHealthy() {
        try {
            ses.listEmailIdentities(ListEmailIdentitiesRequest.builder().pageSize(1).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Wraps retry logic and rotation
    public String send(SesEmailRequest req) {
        int retries = 0;

        // Inject rotated from address automatically if none provided
        if (req.from == null) {
            req.from = rotator.next();
        }

        while (true) {
            try {
                return doSend(req);

            } catch (SesV2Exception ex) {
                if (ex.statusCode() == 429 && retries < 5) {
                    try {
                        Thread.sleep((long) Math.pow(2, retries) * 200L);
                    } catch (InterruptedException ignored) {
                    }
                    retries++;
                } else {
                    throw ex;
                }
            }
        }
    }

    private String doSend(SesEmailRequest req) {

        Destination destination = Destination.builder()
                .toAddresses(req.to)
                .build();

        // Subject
        Content subject = Content.builder()
                .data(req.subject)
                .charset("UTF-8")
                .build();

        Body.Builder body = Body.builder();

        // Text body
        if (req.textBody != null) {
            body.text(Content.builder()
                    .data(req.textBody)
                    .charset("UTF-8")
                    .build());
        }

        // HTML body
        if (req.htmlBody != null) {
            body.html(Content.builder()
                    .data(req.htmlBody)
                    .charset("UTF-8")
                    .build());
        }

        Message message = Message.builder()
                .subject(subject)
                .body(body.build())
                .build();

        SendEmailRequest.Builder requestBuilder = SendEmailRequest.builder()
                .fromEmailAddress(req.from)
                .destination(destination)
                .content(EmailContent.builder().simple(message).build());

        // Only add reply-to if specified
        if (req.replyTo != null && !req.replyTo.isEmpty()) {
            requestBuilder.replyToAddresses(req.replyTo);
        }

        // Add configuration set if provided (for event tracking)
        if (req.configurationSetName != null && !req.configurationSetName.isEmpty()) {
            requestBuilder.configurationSetName(req.configurationSetName);
        }

        // Add tags if provided (for campaign tracking)
        if (req.tags != null && !req.tags.isEmpty()) {
            java.util.List<MessageTag> messageTags = req.tags.entrySet().stream()
                    .map(e -> MessageTag.builder()
                            .name(e.getKey())
                            .value(e.getValue())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
            requestBuilder.emailTags(messageTags);
        }

        SendEmailResponse response = ses.sendEmail(requestBuilder.build());
        return response.messageId();
    }
}
