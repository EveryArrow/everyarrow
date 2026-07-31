package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MicroBatchExecutionPlan {
    private final MicroBatch microBatch;
    private final List<Pair<Long, Long>> batches;
    private final int fromIndex;
    private final int toIndex;

    private MicroBatchExecutionPlan(MicroBatch microBatch, List<Pair<Long, Long>> batches, int fromIndex, int toIndex) {
        this.microBatch = microBatch;
        this.batches = batches;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    static MicroBatchExecutionPlan from(MicroBatch microBatch) throws BehaviorException {
        List<Pair<Long, Long>> batches = microBatch.getBatches();
        int fromIndex = Integer.max(Integer.parseInt(
                        microBatch.getProperties().getOrDefault(MicroBatch.FROM_BATCH, "1")),
                1) - 1;
        int toIndex = Integer.min(Integer.parseInt(
                        microBatch.getProperties().getOrDefault(MicroBatch.TO_BATCH, String.valueOf(batches.size()))),
                batches.size()) - 1;
        return new MicroBatchExecutionPlan(microBatch, batches, fromIndex, toIndex);
    }

    int totalBatchCount() {
        return batches.size();
    }

    int fromIndex() {
        return fromIndex;
    }

    int toIndex() {
        return toIndex;
    }

    int firstBatchNumber() {
        return fromIndex + 1;
    }

    int lastBatchNumber() {
        return toIndex + 1;
    }

    Map<String, Object> statistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("Total micro batches to process", totalBatchCount());
        statistics.put("Start micro batch", firstBatchNumber());
        statistics.put("End micro batch", lastBatchNumber());
        return statistics;
    }

    void applyRangeProperties() {
        microBatch.getProperties().put("_firstMicroBatch", String.valueOf(firstBatchNumber()));
        microBatch.getProperties().put("_lastMicroBatch", String.valueOf(lastBatchNumber()));
    }

    void applyCurrentBatch(int batchIndex) {
        int currentBatchNumber = batchIndex + 1;
        microBatch.getProperties().put(MicroBatch.FROM_BATCH, String.valueOf(currentBatchNumber));
        microBatch.getProperties().put("_rowNumberStart", String.valueOf(batches.get(batchIndex).getLeft()));
        microBatch.getProperties().put("_rowNumberEnd", String.valueOf(batches.get(batchIndex).getRight()));
        microBatch.getProperties().put("_currentMicroBatch", String.valueOf(currentBatchNumber));
    }

    boolean completedAllBatches(int nextBatchIndex) {
        return nextBatchIndex == batches.size();
    }
}
