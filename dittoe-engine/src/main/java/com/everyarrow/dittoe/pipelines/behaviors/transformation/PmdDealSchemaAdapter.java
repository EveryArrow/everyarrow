package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.ExpressionField;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceField;

import java.util.List;
import java.util.Objects;

/**
 * The only adapter that translates PMD Resource fields into DEAL runtime types.
 */
final class PmdDealSchemaAdapter {
    private PmdDealSchemaAdapter() {
    }

    static DealSchema fromResourceFields(List<? extends Field> resourceFields) {
        Objects.requireNonNull(resourceFields, "resourceFields");
        return DealSchema.of(resourceFields.stream()
                .map(PmdDealSchemaAdapter::toFieldDefinition)
                .toList());
    }

    private static DealSchema.FieldDefinition toFieldDefinition(Field field) {
        Objects.requireNonNull(field, "resourceFields must not contain null");
        String name = Objects.requireNonNull(field.getName(), "PMD Resource field name");
        DataType dataType = Objects.requireNonNull(
                field.getDataType(), "PMD Resource field dataType: " + name);
        return new DealSchema.FieldDefinition(name, toDealType(dataType), field.isNullable(), isComputed(field));
    }

    private static DealType toDealType(DataType dataType) {
        return switch (dataType) {
            case String -> DealType.STRING;
            case Integer -> DealType.INTEGER;
            case Double -> DealType.DOUBLE;
            case Decimal -> DealType.DECIMAL;
            case Boolean -> DealType.BOOLEAN;
            case Date -> DealType.DATE;
            case DateTime -> DealType.DATE_TIME;
            case Time -> DealType.TIME;
            case UUID -> DealType.UUID;
            case TimeWithTimeZone -> DealType.TIME_WITH_TIME_ZONE;
            case DateTimeWithTimeZone -> DealType.DATE_TIME_WITH_TIME_ZONE;
            case JSON -> DealType.JSON;
            case JSONB -> DealType.JSONB;
            case Array -> DealType.ARRAY;
            case Number -> DealType.NUMBER;
            case Bytes -> DealType.BYTES;
            case XML -> DealType.XML;
        };
    }

    private static boolean isComputed(Field field) {
        if (field instanceof SourceField sourceField) {
            return hasExpression(sourceField.getExpression());
        }
        if (field instanceof ExpressionField expressionField) {
            return hasExpression(expressionField.getExpression());
        }
        return false;
    }

    private static boolean hasExpression(String expression) {
        return expression != null && !expression.isBlank();
    }
}
