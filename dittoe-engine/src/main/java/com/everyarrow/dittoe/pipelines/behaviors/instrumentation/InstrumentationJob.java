package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

public class InstrumentationJob {
    private final String id;
    private final String name;

    public InstrumentationJob(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
