/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

package org.everyarrow.dittoe.schema;

import java.io.Serializable;

/**
 * Represents a rule for the data quality. Data is inserted into the target resource only when it passes the data
 * quality rule.
 */
public class DQRule implements Serializable {

    /**
     * Name of the rule.
     */
    private String ruleName;

    /**
     * Expression of the rule.
     */
    private String ruleExpression;

    /**
     * Default constructor.
     */
    public DQRule() {
    }

    /**
     * Parameterized constructor.
     *
     * @param ruleName       Name of the data quality rule.
     * @param ruleExpression Expression of the data quality rule.
     */
    public DQRule(String ruleName, String ruleExpression) {
        this.ruleName = ruleName;
        this.ruleExpression = ruleExpression;
    }

    /**
     * Gets the name of the data quality rule.
     *
     * @return Name of the data quality rule.
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * Sets the name of the data quality rule.
     *
     * @param ruleName Name that is to be set.
     */
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    /**
     * Gets the expression of the data quality rule.
     *
     * @return Expression of the data quality rule.
     */
    public String getRuleExpression() {
        return ruleExpression;
    }

    /**
     * Sets the expression of the data quality rule.
     *
     * @param ruleExpression Expression that is to be set.
     */
    public void setRuleExpression(String ruleExpression) {
        this.ruleExpression = ruleExpression;
    }
}
