package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import java.util.Objects;

public class InstrumentationTask {
    private final String id;
    private final String name;
    private final String description;

    public InstrumentationTask(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InstrumentationTask other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
