package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch;

import java.util.ArrayList;
import java.util.List;

final class BehaviorPlan {
    private final List<StagedBehaviorBase> stagedBehaviors = new ArrayList<>();
    private MicroBatch microBatch;

    List<StagedBehaviorBase> stagedBehaviors() {
        return stagedBehaviors;
    }

    MicroBatch microBatch() {
        return microBatch;
    }

    void addStagedBehavior(StagedBehaviorBase stagedBehavior) {
        stagedBehaviors.add(stagedBehavior);
    }

    void setMicroBatch(MicroBatch microBatch) {
        this.microBatch = microBatch;
    }
}
