package com.everyarrow.dittoe.io;

import org.everyarrow.dittoe.schema.Resource;

import java.util.List;
import java.util.Map;

public interface ResourceDependencyProvider {
    Map<String, List<String>> getResourcesDependencies(List<Resource> resources);
}
