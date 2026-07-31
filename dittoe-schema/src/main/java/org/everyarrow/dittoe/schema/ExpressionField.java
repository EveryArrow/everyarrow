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

public class ExpressionField extends Field {
    private String expression;

    public ExpressionField(String name, DataType dataType, String expression) {
        super(name, dataType);
        this.expression = expression;
    }

    public ExpressionField(String name, DataType dataType, boolean isNullable, String expression) {
        super(name, dataType, isNullable);
        this.expression = expression;
    }


    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
