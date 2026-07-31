/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws.ses;

import java.util.HashMap;
import java.util.Map;

/**
 * Email request for AWS SES v2.
 * Supports configuration sets and tags for event tracking.
 */
public class SesEmailRequest {
    public String from;
    public String to;
    public String replyTo;
    public String subject;
    public String textBody;
    public String htmlBody;
    
    // Configuration set for event tracking (bounces, complaints, delivery)
    public String configurationSetName;
    
    // Tags for campaign tracking and analytics
    public Map<String, String> tags;
    
    // External message ID for webhook correlation (optional)
    public String externalMessageId;
    
    public SesEmailRequest() {
        this.tags = new HashMap<>();
    }
    
    /**
     * Add a tag for tracking.
     */
    public SesEmailRequest addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }
    
    /**
     * Set configuration set for event notifications.
     */
    public SesEmailRequest withConfigurationSet(String configSetName) {
        this.configurationSetName = configSetName;
        return this;
    }
}
