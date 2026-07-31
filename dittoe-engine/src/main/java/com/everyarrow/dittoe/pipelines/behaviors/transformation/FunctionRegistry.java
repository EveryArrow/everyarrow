package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.Expression;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.BooleanFunctions;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.ConditionalFunctions;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.ConversionFunctions;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.DateTimeFunctions;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.LazyArguments;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.LazyScalarFunction;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.NumericFunctions;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.ScalarFunction;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.functions.StringFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Authoritative catalog and invocation boundary for embedded DEAL functions.
 * Names, aliases, signatures, result types, and evaluation strategy live together.
 */
public final class FunctionRegistry {
    private static final Map<String, RegisteredFunction> FUNCTIONS_BY_NAME;
    private static final List<FunctionDescriptor> DESCRIPTORS;

    static {
        RegistryBuilder registry = new RegistryBuilder();
        registerNumericFunctions(registry);
        registerStringFunctions(registry);
        registerConditionalFunctions(registry);
        registerBooleanFunctions(registry);
        registerDateTimeFunctions(registry);
        registerConversionFunctions(registry);
        FUNCTIONS_BY_NAME = Map.copyOf(registry.functionsByName);
        DESCRIPTORS = List.copyOf(registry.descriptors);
    }

    private FunctionRegistry() {
    }

    /** Returns the immutable engine-owned function catalog. */
    public static List<FunctionDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    /** Validates function existence and arity without evaluating an expression. */
    public static FunctionDescriptor validateInvocation(String functionName, int argumentCount) {
        RegisteredFunction function = FUNCTIONS_BY_NAME.get(normalize(functionName));
        if (function == null) {
            throw new DealCompileFailure(
                    DealDiagnosticCode.UNKNOWN_FUNCTION, "Unknown DEAL function: " + functionName);
        }
        try {
            function.descriptor.requireSignature(argumentCount);
        } catch (IllegalArgumentException e) {
            throw new DealCompileFailure(DealDiagnosticCode.INVALID_ARGUMENT_COUNT, e.getMessage());
        }
        return function.descriptor;
    }

    /** Resolves and validates a function call using compile-time expression types. */
    public static ExpressionType inferInvocation(String functionName, List<ExpressionType> argumentTypes) {
        return FunctionTypeChecker.infer(requireFunction(functionName).descriptor, argumentTypes);
    }

    /** Compatibility invocation API for callers that already have evaluated values. */
    public static Object invoke(String functionName, Object[] args) {
        Objects.requireNonNull(args, "args");
        return requireFunction(functionName).invoke(values(args));
    }

    /** Invokes a parsed function call while preserving lazy/short-circuit semantics. */
    public static Object invoke(String functionName,
                                List<Expression> arguments,
                                ExpressionContext context) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(context, "context");
        List<Supplier<Object>> suppliers = arguments.stream()
                .map(argument -> (Supplier<Object>) () -> argument.evaluate(context))
                .toList();
        return requireFunction(functionName).invoke(suppliers);
    }

    private static RegisteredFunction requireFunction(String functionName) {
        Objects.requireNonNull(functionName, "functionName");
        RegisteredFunction function = FUNCTIONS_BY_NAME.get(normalize(functionName));
        if (function == null) {
            throw new IllegalArgumentException("Unknown DEAL function: " + functionName);
        }
        return function;
    }

    private static List<Supplier<Object>> values(Object[] values) {
        List<Supplier<Object>> suppliers = new ArrayList<>(values.length);
        for (Object value : values) {
            suppliers.add(() -> value);
        }
        return suppliers;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void registerNumericFunctions(RegistryBuilder registry) {
        registry.eager("sum", Set.of(), FunctionCategory.NUMERIC,
                "Returns the sum of all arguments", "sum(number1, number2, ...)", NumericFunctions::sum,
                FunctionSignature.variadic(FunctionValueType.NUMBER, FunctionValueType.NUMBER, 1));
        registry.eager("multiply", Set.of("mul"), FunctionCategory.NUMERIC,
                "Returns the product of all arguments", "multiply(number1, number2, ...)", NumericFunctions::multiply,
                FunctionSignature.variadic(FunctionValueType.NUMBER, FunctionValueType.NUMBER, 1));
        registry.eager("abs", Set.of(), FunctionCategory.NUMERIC,
                "Returns the absolute value", "abs(number)", NumericFunctions::abs, numericUnary());
        registry.eager("acos", Set.of(), FunctionCategory.NUMERIC,
                "Returns arc cosine in radians", "acos(number)", NumericFunctions::acos, numericUnary());
        registry.eager("asin", Set.of(), FunctionCategory.NUMERIC,
                "Returns arc sine in radians", "asin(number)", NumericFunctions::asin, numericUnary());
        registry.eager("atan", Set.of(), FunctionCategory.NUMERIC,
                "Returns arc tangent in radians", "atan(number)", NumericFunctions::atan, numericUnary());
        registry.eager("atan2", Set.of(), FunctionCategory.NUMERIC,
                "Returns arc tangent of two values", "atan2(y, x)", NumericFunctions::atan2, numericBinary());
        registry.eager("ceiling", Set.of("ceil"), FunctionCategory.NUMERIC,
                "Returns the smallest integer greater than or equal to a number", "ceiling(number)",
                NumericFunctions::ceiling, numericUnary());
        registry.eager("cos", Set.of(), FunctionCategory.NUMERIC,
                "Returns cosine of an angle", "cos(number)", NumericFunctions::cos, numericUnary());
        registry.eager("cot", Set.of(), FunctionCategory.NUMERIC,
                "Returns cotangent of an angle", "cot(number)", NumericFunctions::cot, numericUnary());
        registry.eager("degrees", Set.of(), FunctionCategory.NUMERIC,
                "Converts radians to degrees", "degrees(number)", NumericFunctions::degrees, numericUnary());
        registry.eager("exp", Set.of(), FunctionCategory.NUMERIC,
                "Returns e raised to the supplied power", "exp(number)", NumericFunctions::exp, numericUnary());
        registry.eager("floor", Set.of(), FunctionCategory.NUMERIC,
                "Returns the largest integer less than or equal to a number", "floor(number)",
                NumericFunctions::floor, numericUnary());
        registry.eager("log", Set.of(), FunctionCategory.NUMERIC,
                "Returns the natural logarithm", "log(number)", NumericFunctions::log, numericUnary());
        registry.eager("log10", Set.of(), FunctionCategory.NUMERIC,
                "Returns the base-10 logarithm", "log10(number)", NumericFunctions::log10, numericUnary());
        registry.eager("mod", Set.of(), FunctionCategory.NUMERIC,
                "Returns the remainder of division", "mod(dividend, divisor)", NumericFunctions::mod, numericBinary());
        registry.eager("power", Set.of(), FunctionCategory.NUMERIC,
                "Raises a number to a power", "power(base, exponent)", NumericFunctions::power, numericBinary());
        registry.eager("radians", Set.of(), FunctionCategory.NUMERIC,
                "Converts degrees to radians", "radians(number)", NumericFunctions::radians, numericUnary());
        registry.eager("round", Set.of(), FunctionCategory.NUMERIC,
                "Rounds a number with an optional decimal count", "round(number, [decimals])",
                NumericFunctions::round,
                numericUnary(),
                FunctionSignature.fixed(FunctionValueType.NUMBER,
                        FunctionValueType.NUMBER, FunctionValueType.INTEGER));
        registry.eager("sign", Set.of(), FunctionCategory.NUMERIC,
                "Returns the sign of a number", "sign(number)", NumericFunctions::sign, numericUnary());
        registry.eager("sin", Set.of(), FunctionCategory.NUMERIC,
                "Returns sine of an angle", "sin(number)", NumericFunctions::sin, numericUnary());
        registry.eager("sqrt", Set.of(), FunctionCategory.NUMERIC,
                "Returns the square root", "sqrt(number)", NumericFunctions::sqrt, numericUnary());
        registry.eager("tan", Set.of(), FunctionCategory.NUMERIC,
                "Returns tangent of an angle", "tan(number)", NumericFunctions::tan, numericUnary());
        registry.eager("greatest", Set.of(), FunctionCategory.NUMERIC,
                "Returns the largest numeric argument", "greatest(number1, number2, ...)", NumericFunctions::greatest,
                FunctionSignature.variadic(FunctionValueType.NUMBER, FunctionValueType.NUMBER, 1));
        registry.eager("least", Set.of(), FunctionCategory.NUMERIC,
                "Returns the smallest numeric argument", "least(number1, number2, ...)", NumericFunctions::least,
                FunctionSignature.variadic(FunctionValueType.NUMBER, FunctionValueType.NUMBER, 1));
        registry.eager("trunc", Set.of(), FunctionCategory.NUMERIC,
                "Truncates a number with an optional decimal count", "trunc(number, [decimals])",
                NumericFunctions::trunc,
                numericUnary(),
                FunctionSignature.fixed(FunctionValueType.NUMBER,
                        FunctionValueType.NUMBER, FunctionValueType.INTEGER));
    }

    private static void registerStringFunctions(RegistryBuilder registry) {
        registry.eager("concat", Set.of(), FunctionCategory.STRING,
                "Concatenates all arguments into a string", "concat(value1, value2, ...)", StringFunctions::concat,
                FunctionSignature.variadic(FunctionValueType.STRING, FunctionValueType.ANY, 0));
        registry.eager("lower", Set.of(), FunctionCategory.STRING,
                "Converts a string to lowercase", "lower(string)", StringFunctions::lower, stringUnary());
        registry.eager("upper", Set.of("toupper"), FunctionCategory.STRING,
                "Converts a string to uppercase", "upper(string)", StringFunctions::upper, stringUnary());
        registry.eager("length", Set.of(), FunctionCategory.STRING,
                "Returns the length of a string", "length(string)", StringFunctions::length,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.STRING));
        registry.eager("substring", Set.of("substr"), FunctionCategory.STRING,
                "Extracts a substring", "substring(string, start, length)", StringFunctions::substring,
                FunctionSignature.fixed(FunctionValueType.STRING,
                        FunctionValueType.STRING, FunctionValueType.INTEGER, FunctionValueType.INTEGER));
        registry.eager("trim", Set.of(), FunctionCategory.STRING,
                "Removes leading and trailing spaces", "trim(string)", StringFunctions::trim, stringUnary());
        registry.eager("ltrim", Set.of(), FunctionCategory.STRING,
                "Removes leading spaces", "ltrim(string)", StringFunctions::ltrim, stringUnary());
        registry.eager("rtrim", Set.of(), FunctionCategory.STRING,
                "Removes trailing spaces", "rtrim(string)", StringFunctions::rtrim, stringUnary());
        registry.eager("replace", Set.of(), FunctionCategory.STRING,
                "Replaces occurrences of a substring", "replace(string, old, new)", StringFunctions::replace,
                FunctionSignature.fixed(FunctionValueType.STRING,
                        FunctionValueType.STRING, FunctionValueType.STRING, FunctionValueType.STRING));
        registry.eager("lpad", Set.of(), FunctionCategory.STRING,
                "Pads the left side of a string", "lpad(string, length, pad)", StringFunctions::lpad,
                FunctionSignature.fixed(FunctionValueType.STRING,
                        FunctionValueType.STRING, FunctionValueType.INTEGER, FunctionValueType.STRING));
        registry.eager("rpad", Set.of(), FunctionCategory.STRING,
                "Pads the right side of a string", "rpad(string, length, pad)", StringFunctions::rpad,
                FunctionSignature.fixed(FunctionValueType.STRING,
                        FunctionValueType.STRING, FunctionValueType.INTEGER, FunctionValueType.STRING));
        registry.eager("instr", Set.of("position"), FunctionCategory.STRING,
                "Returns the one-based position of a substring, or zero", "instr(string, substring)",
                StringFunctions::instr,
                FunctionSignature.fixed(FunctionValueType.INTEGER,
                        FunctionValueType.STRING, FunctionValueType.STRING));
        registry.eager("initcap", Set.of(), FunctionCategory.STRING,
                "Capitalizes the first letter of each word", "initcap(string)", StringFunctions::initcap,
                stringUnary());
        registry.eager("ascii", Set.of(), FunctionCategory.STRING,
                "Returns the character code of the first character", "ascii(string)", StringFunctions::ascii,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.STRING));
        registry.eager("chr", Set.of(), FunctionCategory.STRING,
                "Returns the character for a character code", "chr(code)", StringFunctions::chr,
                FunctionSignature.fixed(FunctionValueType.STRING, FunctionValueType.INTEGER));
    }

    private static void registerConditionalFunctions(RegistryBuilder registry) {
        registry.lazy("coalesce", Set.of(), FunctionCategory.CONDITIONAL,
                "Returns the first non-null argument", "coalesce(expr1, expr2, ...)",
                ConditionalFunctions::coalesceLazy,
                FunctionSignature.variadic(FunctionValueType.ANY, FunctionValueType.ANY, 0));
        registry.eager("nullif", Set.of(), FunctionCategory.CONDITIONAL,
                "Returns null when both arguments are equal", "nullif(expr1, expr2)", ConditionalFunctions::nullif,
                FunctionSignature.fixed(FunctionValueType.ANY, FunctionValueType.ANY, FunctionValueType.ANY));

        FunctionSignature conditionalSignature = FunctionSignature.repeating(
                FunctionValueType.ANY,
                List.of(FunctionValueType.BOOLEAN, FunctionValueType.ANY),
                1,
                List.of(FunctionValueType.ANY));
        registry.lazy("if", Set.of(), FunctionCategory.CONDITIONAL,
                "Returns the result for the first true condition, otherwise the final value",
                "if(cond1, then1, cond2, then2, ..., elseValue)", ConditionalFunctions::ifLazy,
                conditionalSignature);
        registry.lazy("caseWhen", Set.of(), FunctionCategory.CONDITIONAL,
                "Returns the result for the first true condition, otherwise the default value",
                "caseWhen(cond1, then1, cond2, then2, ..., defaultValue)", ConditionalFunctions::caseWhenLazy,
                conditionalSignature);
    }

    private static void registerBooleanFunctions(RegistryBuilder registry) {
        registry.eager("eq", Set.of(), FunctionCategory.BOOLEAN,
                "Returns true when all arguments are equal", "eq(value1, value2, ...)", BooleanFunctions::eq,
                FunctionSignature.variadic(FunctionValueType.BOOLEAN, FunctionValueType.ANY, 2));
        registry.eager("lessThan", Set.of(), FunctionCategory.BOOLEAN,
                "Returns true when the first number is less than the second", "lessThan(value1, value2)",
                BooleanFunctions::lessThan,
                FunctionSignature.fixed(FunctionValueType.BOOLEAN,
                        FunctionValueType.NUMBER, FunctionValueType.NUMBER));
        registry.eager("not", Set.of(), FunctionCategory.BOOLEAN,
                "Returns the logical inverse", "not(boolean)", BooleanFunctions::not,
                FunctionSignature.fixed(FunctionValueType.BOOLEAN, FunctionValueType.BOOLEAN));
        registry.lazy("and", Set.of(), FunctionCategory.BOOLEAN,
                "Returns true when all arguments are true", "and(boolean1, boolean2, ...)", BooleanFunctions::andLazy,
                FunctionSignature.variadic(FunctionValueType.BOOLEAN, FunctionValueType.BOOLEAN, 0));
        registry.lazy("or", Set.of(), FunctionCategory.BOOLEAN,
                "Returns true when any argument is true", "or(boolean1, boolean2, ...)", BooleanFunctions::orLazy,
                FunctionSignature.variadic(FunctionValueType.BOOLEAN, FunctionValueType.BOOLEAN, 0));
    }

    private static void registerDateTimeFunctions(RegistryBuilder registry) {
        registry.eager("current_datetime", Set.of("currentDateTime"), FunctionCategory.DATE_TIME,
                "Returns the current local date and time", "current_datetime()", DateTimeFunctions::currentDateTime,
                FunctionSignature.fixed(FunctionValueType.DATE_TIME));
        registry.eager("current_date", Set.of("currentDate"), FunctionCategory.DATE_TIME,
                "Returns the current local date", "current_date()", DateTimeFunctions::currentDate,
                FunctionSignature.fixed(FunctionValueType.DATE));
        registry.eager("current_time", Set.of("currentTime"), FunctionCategory.DATE_TIME,
                "Returns the current local time", "current_time()", DateTimeFunctions::currentTime,
                FunctionSignature.fixed(FunctionValueType.TIME));
        registry.eager("year", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the year component", "year(date)", DateTimeFunctions::year,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.DATE_OR_DATE_TIME));
        registry.eager("month", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the month component", "month(date)", DateTimeFunctions::month,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.DATE_OR_DATE_TIME));
        registry.eager("day", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the day component", "day(date)", DateTimeFunctions::day,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.DATE_OR_DATE_TIME));
        registry.eager("hour", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the hour component", "hour(time)", DateTimeFunctions::hour,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.TIME_OR_DATE_TIME));
        registry.eager("minute", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the minute component", "minute(time)", DateTimeFunctions::minute,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.TIME_OR_DATE_TIME));
        registry.eager("second", Set.of(), FunctionCategory.DATE_TIME,
                "Returns the second component", "second(time)", DateTimeFunctions::second,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.TIME_OR_DATE_TIME));
        registry.eager("date_add", Set.of("dateAdd"), FunctionCategory.DATE_TIME,
                "Adds days to a date", "date_add(date, days)", DateTimeFunctions::dateAdd,
                FunctionSignature.fixed(FunctionValueType.DATE,
                        FunctionValueType.DATE, FunctionValueType.INTEGER));
        registry.eager("date_sub", Set.of("dateSub"), FunctionCategory.DATE_TIME,
                "Subtracts days from a date", "date_sub(date, days)", DateTimeFunctions::dateSub,
                FunctionSignature.fixed(FunctionValueType.DATE,
                        FunctionValueType.DATE, FunctionValueType.INTEGER));
        registry.eager("extract", Set.of(), FunctionCategory.DATE_TIME,
                "Extracts a named component from a date or time", "extract(field, dateOrTime)",
                DateTimeFunctions::extract,
                FunctionSignature.fixed(FunctionValueType.INTEGER,
                        FunctionValueType.STRING, FunctionValueType.TEMPORAL));
    }

    private static void registerConversionFunctions(RegistryBuilder registry) {
        registry.eager("to_string", Set.of("toString"), FunctionCategory.CONVERSION,
                "Converts a value to text and propagates null", "to_string(value)",
                ConversionFunctions::toStringValue,
                FunctionSignature.fixed(FunctionValueType.STRING, FunctionValueType.ANY));
        registry.eager("to_integer", Set.of("toInteger"), FunctionCategory.CONVERSION,
                "Converts an exact numeric or text value to an integer", "to_integer(value)",
                ConversionFunctions::toInteger,
                FunctionSignature.fixed(FunctionValueType.INTEGER, FunctionValueType.ANY));
        registry.eager("to_double", Set.of("toDouble"), FunctionCategory.CONVERSION,
                "Converts a finite numeric or text value to a double", "to_double(value)",
                ConversionFunctions::toDouble,
                FunctionSignature.fixed(FunctionValueType.DOUBLE, FunctionValueType.ANY));
        registry.eager("to_decimal", Set.of("toDecimal"), FunctionCategory.CONVERSION,
                "Converts a finite numeric or text value to a decimal", "to_decimal(value)",
                ConversionFunctions::toDecimal,
                FunctionSignature.fixed(FunctionValueType.DECIMAL, FunctionValueType.ANY));
        registry.eager("to_boolean", Set.of("toBoolean"), FunctionCategory.CONVERSION,
                "Converts true or false text to a boolean", "to_boolean(value)",
                ConversionFunctions::toBoolean,
                FunctionSignature.fixed(FunctionValueType.BOOLEAN, FunctionValueType.ANY));
        registry.eager("to_date", Set.of("toDate"), FunctionCategory.CONVERSION,
                "Converts an ISO-8601 value to a local date", "to_date(value)",
                ConversionFunctions::toDate,
                FunctionSignature.fixed(FunctionValueType.DATE, FunctionValueType.ANY));
        registry.eager("to_datetime", Set.of("toDateTime", "to_date_time"), FunctionCategory.CONVERSION,
                "Converts an ISO-8601 value to a local date-time", "to_datetime(value)",
                ConversionFunctions::toDateTime,
                FunctionSignature.fixed(FunctionValueType.DATE_TIME, FunctionValueType.ANY));
        registry.eager("to_time", Set.of("toTime"), FunctionCategory.CONVERSION,
                "Converts an ISO-8601 value to a local time", "to_time(value)",
                ConversionFunctions::toTime,
                FunctionSignature.fixed(FunctionValueType.TIME, FunctionValueType.ANY));
        registry.eager("to_uuid", Set.of("toUuid"), FunctionCategory.CONVERSION,
                "Converts a standard UUID string to a UUID", "to_uuid(value)",
                ConversionFunctions::toUuid,
                FunctionSignature.fixed(FunctionValueType.UUID, FunctionValueType.ANY));
        registry.eager("is_null", Set.of("isNull"), FunctionCategory.CONVERSION,
                "Returns true when a value is null", "is_null(value)", ConversionFunctions::isNull,
                FunctionSignature.fixed(FunctionValueType.BOOLEAN, FunctionValueType.ANY));
        registry.eager("is_not_null", Set.of("isNotNull"), FunctionCategory.CONVERSION,
                "Returns true when a value is not null", "is_not_null(value)", ConversionFunctions::isNotNull,
                FunctionSignature.fixed(FunctionValueType.BOOLEAN, FunctionValueType.ANY));
    }

    private static FunctionSignature numericUnary() {
        return FunctionSignature.fixed(FunctionValueType.NUMBER, FunctionValueType.NUMBER);
    }

    private static FunctionSignature numericBinary() {
        return FunctionSignature.fixed(
                FunctionValueType.NUMBER, FunctionValueType.NUMBER, FunctionValueType.NUMBER);
    }

    private static FunctionSignature stringUnary() {
        return FunctionSignature.fixed(FunctionValueType.STRING, FunctionValueType.STRING);
    }

    private record RegisteredFunction(FunctionDescriptor descriptor,
                                      ScalarFunction eagerFunction,
                                      LazyScalarFunction lazyFunction) {
        private RegisteredFunction {
            if ((eagerFunction == null) == (lazyFunction == null)) {
                throw new IllegalArgumentException("A function must have exactly one implementation strategy");
            }
        }

        Object invoke(List<Supplier<Object>> suppliers) {
            FunctionSignature signature = descriptor.requireSignature(suppliers.size());
            ValidatingLazyArguments arguments = new ValidatingLazyArguments(
                    descriptor.name(), suppliers, signature.argumentTypes(suppliers.size()).orElseThrow());
            return descriptor.evaluationStrategy() == FunctionEvaluationStrategy.LAZY
                    ? lazyFunction.apply(arguments)
                    : eagerFunction.apply(arguments.evaluateAll());
        }
    }

    private static final class ValidatingLazyArguments implements LazyArguments {
        private final String functionName;
        private final List<Supplier<Object>> suppliers;
        private final List<FunctionValueType> argumentTypes;
        private final Object[] values;
        private final boolean[] evaluated;

        private ValidatingLazyArguments(String functionName,
                                        List<Supplier<Object>> suppliers,
                                        List<FunctionValueType> argumentTypes) {
            this.functionName = functionName;
            this.suppliers = suppliers;
            this.argumentTypes = argumentTypes;
            this.values = new Object[suppliers.size()];
            this.evaluated = new boolean[suppliers.size()];
        }

        @Override
        public int size() {
            return suppliers.size();
        }

        @Override
        public Object get(int index) {
            Objects.checkIndex(index, size());
            if (!evaluated[index]) {
                Object value = suppliers.get(index).get();
                FunctionValueType expectedType = argumentTypes.get(index);
                if (!expectedType.accepts(value)) {
                    String actualType = value == null ? "null" : value.getClass().getSimpleName();
                    throw new IllegalArgumentException(
                            "DEAL function '" + functionName + "' argument " + (index + 1)
                                    + " expected " + expectedType + " but received " + actualType);
                }
                values[index] = value;
                evaluated[index] = true;
            }
            return values[index];
        }

        Object[] evaluateAll() {
            for (int i = 0; i < size(); i++) {
                get(i);
            }
            return values.clone();
        }
    }

    private static final class RegistryBuilder {
        private final Map<String, RegisteredFunction> functionsByName = new HashMap<>();
        private final Map<String, FunctionDescriptor> descriptorsByCanonicalName = new LinkedHashMap<>();
        private final List<FunctionDescriptor> descriptors = new ArrayList<>();

        void eager(String name,
                   Set<String> aliases,
                   FunctionCategory category,
                   String description,
                   String syntax,
                   ScalarFunction function,
                   FunctionSignature... signatures) {
            register(name, aliases, category, description, syntax, FunctionEvaluationStrategy.EAGER,
                    function, null, signatures);
        }

        void lazy(String name,
                  Set<String> aliases,
                  FunctionCategory category,
                  String description,
                  String syntax,
                  LazyScalarFunction function,
                  FunctionSignature... signatures) {
            register(name, aliases, category, description, syntax, FunctionEvaluationStrategy.LAZY,
                    null, function, signatures);
        }

        private void register(String name,
                              Set<String> aliases,
                              FunctionCategory category,
                              String description,
                              String syntax,
                              FunctionEvaluationStrategy strategy,
                              ScalarFunction eagerFunction,
                              LazyScalarFunction lazyFunction,
                              FunctionSignature... signatures) {
            Set<String> stableAliases = new LinkedHashSet<>(aliases);
            FunctionDescriptor descriptor = new FunctionDescriptor(
                    name, stableAliases, category, description, syntax, List.of(signatures), strategy);
            String canonicalKey = normalize(name);
            if (descriptorsByCanonicalName.putIfAbsent(canonicalKey, descriptor) != null) {
                throw new IllegalStateException("Duplicate DEAL function: " + name);
            }

            RegisteredFunction registered = new RegisteredFunction(descriptor, eagerFunction, lazyFunction);
            registerName(canonicalKey, registered);
            for (String alias : stableAliases) {
                registerName(normalize(alias), registered);
            }
            descriptors.add(descriptor);
        }

        private void registerName(String name, RegisteredFunction function) {
            if (functionsByName.putIfAbsent(name, function) != null) {
                throw new IllegalStateException("Duplicate DEAL function name or alias: " + name);
            }
        }
    }
}
