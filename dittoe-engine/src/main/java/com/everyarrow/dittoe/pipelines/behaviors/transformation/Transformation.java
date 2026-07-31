package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.*;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of Transformation behavior. It is a staged behavior.
 * This behavior is used to transform data using DEAL.
 */
public class Transformation extends StagedBehaviorBase {

    private final Logger logger = LoggerFactory.getLogger(Transformation.class);
    private final ExpressionEvaluator expressionEvaluator;
    private final DealResultValidator resultValidator;

    /**
     * Constructor.
     *
     * @param name             Name of the behavior.
     * @param description      Description of the behavior.
     * @param properties       Properties of the behavior.
     * @param pipelineMetadata Process Metadata Document to which the behavior belongs.
     * @param dataStore        DataStore on which behavior operates.
     */
    public Transformation(String name, String description, Map<String, String> properties,
                          PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        this(name, description, properties, pipelineMetadata, dataStore, new EmbeddedDealExpressionEvaluator());
    }

    Transformation(String name, String description, Map<String, String> properties,
                   PipelineMetadata pipelineMetadata, DataStoreInterface dataStore,
                   ExpressionEvaluator expressionEvaluator) {
        super(new StagedBehaviorInput(name, BehaviorType.Transformation, properties, description,
                Integer.MIN_VALUE, StageType.Process, pipelineMetadata, dataStore));
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator, "expressionEvaluator");
        this.resultValidator = new DealResultValidator();
    }

    /**
     * Method contains the logic of the execution of the behavior.
     *
     * @throws BehaviorException Thrown if any error occurs during the behavior's executions.
     */
    @Override
    public void exec() throws BehaviorException {
        List<SourceField> computedFields = PipelineMetadataUtils.getComputedFields(
                getPipelineMetadata().getSourceResourceToProcess().getFields());

        if (computedFields.isEmpty()) {
            return;
        }

        TransformationPlan plan;
        try {
            List<Field> resourceFields = getPipelineMetadata().getSourceResourceToProcess().getFields();
            plan = TransformationPlan.compile(
                    PmdDealSchemaAdapter.fromResourceFields(resourceFields),
                    computedFields.stream()
                            .map(field -> new TransformationPlan.FieldExpression(
                                    field.getName(), field.getExpression()))
                            .toList(),
                    expressionEvaluator);
        } catch (ExpressionEvaluationException e) {
            getStageStats().incrementRowsErrored(getDataStore().size());
            throw new BehaviorException(e);
        }

        int dataStoreSize = getDataStore().size();
        for (int i = 0; i < dataStoreSize; i++) {
            try {
                Map<String, Object> row = new HashMap<>(getDataStore().get(i));
                ExpressionContext expressionContext = ExpressionContext.forRow(plan.schema(), row);

                for (TransformationPlan.CompiledField compiledField : plan.fields()) {
                    Object value = evaluateField(compiledField, expressionContext, i);
                    String fieldName = compiledField.field().name();
                    expressionContext.setVariable(fieldName, value);
                    row.put(fieldName, value);
                }

                getDataStore().replace(i, row);
                getStageStats().incrementRowsProcessed(1);
            } catch (DataStoreException | ExpressionEvaluationException e) {
                getStageStats().incrementRowsErrored(1);
                throw new BehaviorException(e);
            }
        }
    }

    private Object evaluateField(TransformationPlan.CompiledField compiledField,
                                 ExpressionContext context,
                                 int rowIndex) throws ExpressionEvaluationException {
        try {
            Object value = compiledField.expression().evaluate(context);
            return resultValidator.validate(
                    compiledField.field(), value, compiledField.source(), rowIndex);
        } catch (ExpressionEvaluationException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Invalid DEAL result")) {
                throw e;
            }
            throw new ExpressionEvaluationException(
                    "Failed DEAL transformation field '" + compiledField.field().name() + "' at row " + rowIndex
                            + " for expression '" + compiledField.source() + "'",
                    e);
        }
    }

    public Logger getLogger() {
        return logger;
    }
}
