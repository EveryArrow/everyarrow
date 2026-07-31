package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.compression.Compression;
import com.everyarrow.dittoe.pipelines.behaviors.encryption.Encryption;
import com.everyarrow.dittoe.pipelines.behaviors.formatting.Formatter;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch;
import com.everyarrow.dittoe.pipelines.behaviors.schemavalidation.SchemaValidation;
import com.everyarrow.dittoe.pipelines.behaviors.sourceretrieval.SourceRetrieval;
import com.everyarrow.dittoe.pipelines.behaviors.targetinsertupdate.TargetInsertUpdate;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.Transformation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.Cache;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.DataInsertionObserver;
import com.everyarrow.dittoe.utility.DataReadObserver;
import com.everyarrow.dittoe.utility.security.PluginClassSafety;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;

final class BehaviorPlanFactory {
    private final Logger logger;

    BehaviorPlanFactory(Logger logger) {
        this.logger = logger;
    }

    BehaviorPlan create(PipelineMetadata pipelineMetadata,
                        DataStoreInterface dataStore,
                        DataReadObserver dataReadObserver,
                        DataInsertionObserver dataInsertionObserver,
                        PipelineExecutionStats stats)
            throws BehaviorException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        BehaviorPlan plan = new BehaviorPlan();
        boolean explicitInstrumentation = false;
        for (Behavior behavior : pipelineMetadata.getBehaviors()) {
            explicitInstrumentation = explicitInstrumentation
                    || behavior.getBehaviorType() == BehaviorType.Instrumentation;
            addBehavior(plan, behavior, pipelineMetadata, dataStore, dataReadObserver, dataInsertionObserver, stats);
        }
        new PipelineInstrumentation(logger).ensureAvailable(pipelineMetadata, explicitInstrumentation);
        return plan;
    }

    private void addBehavior(BehaviorPlan plan,
                             Behavior behavior,
                             PipelineMetadata pipelineMetadata,
                             DataStoreInterface dataStore,
                             DataReadObserver dataReadObserver,
                             DataInsertionObserver dataInsertionObserver,
                             PipelineExecutionStats stats)
            throws BehaviorException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        switch (behavior.getBehaviorType()) {
            case SourceRetrieval -> plan.addStagedBehavior(createSourceRetrieval(
                    behavior, pipelineMetadata, dataStore, dataReadObserver));
            case SchemaValidation -> plan.addStagedBehavior(new SchemaValidation(
                    behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore));
            case Formatting -> plan.addStagedBehavior(new Formatter(
                    behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore));
            case Compression -> {
                logger.info("Compression is enabled");
                plan.addStagedBehavior(new Compression(
                        behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore));
            }
            case Encryption -> {
                logger.info("Encryption is enabled");
                plan.addStagedBehavior(new Encryption(
                        behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore));
            }
            case TargetInsertUpdate -> plan.addStagedBehavior(createTargetInsertUpdate(
                    behavior, pipelineMetadata, dataStore, dataInsertionObserver, stats));
            case MicroBatch -> {
                logger.info("Micro Batching is enabled");
                plan.setMicroBatch(new MicroBatch(
                        behavior.getName(), behavior.getProperties(), behavior.getDescription(), pipelineMetadata));
            }
            case Instrumentation -> {
                logger.info("Instrumentation is enabled");
                Instrumentation.setInstance(new Instrumentation(
                        behavior.getName(), behavior.getDescription(), behavior.getProperties()));
            }
            case Cache -> {
                logger.info("Initializing Cache: {}", behavior.getName());
                plan.addStagedBehavior(createCache(behavior, pipelineMetadata, dataStore));
            }
            case Transformation -> {
                logger.info("Transformation is enabled");
                plan.addStagedBehavior(new Transformation(
                        behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore));
            }
            default -> throw new BehaviorException(
                    EngineBehaviorSupport.unsupportedBehaviorMessage(behavior.getBehaviorType()));
        }
    }

    private SourceRetrieval createSourceRetrieval(Behavior behavior,
                                                 PipelineMetadata pipelineMetadata,
                                                 DataStoreInterface dataStore,
                                                 DataReadObserver dataReadObserver) {
        SourceRetrieval sourceRetrieval = new SourceRetrieval(
                behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore);
        sourceRetrieval.getDataReadObservers().add(dataReadObserver);
        return sourceRetrieval;
    }

    private TargetInsertUpdate createTargetInsertUpdate(Behavior behavior,
                                                       PipelineMetadata pipelineMetadata,
                                                       DataStoreInterface dataStore,
                                                       DataInsertionObserver dataInsertionObserver,
                                                       PipelineExecutionStats stats) {
        TargetInsertUpdate targetInsertUpdate = new TargetInsertUpdate(
                behavior.getName(), behavior.getDescription(), behavior.getProperties(), pipelineMetadata, dataStore);
        targetInsertUpdate.getDataInsertionObservers().add(dataInsertionObserver);
        if (stats != null) {
            targetInsertUpdate.setStats(stats);
        }
        return targetInsertUpdate;
    }

    private StagedBehaviorBase createCache(Behavior behavior,
                                           PipelineMetadata pipelineMetadata,
                                           DataStoreInterface dataStore)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException, BehaviorException {
        validateProperties(behavior.getProperties(), "class");
        String cacheClass;
        try {
            cacheClass = PluginClassSafety.requireAllowedEngineClass(
                    behavior.getProperties().get("class"), behavior.getProperties());
        } catch (ExecutionException e) {
            throw new BehaviorException(e);
        }
        return (Cache) ClassLoader.getSystemClassLoader().loadClass(cacheClass)
                .getDeclaredConstructor(String.class, String.class, Integer.class, Map.class,
                        PipelineMetadata.class, DataStoreInterface.class)
                .newInstance(behavior.getName(), behavior.getDescription(),
                        Optional.ofNullable(behavior.getProperties().get("order")).map(Integer::parseInt).orElse(0),
                        behavior.getProperties(), pipelineMetadata, dataStore);
    }
}
