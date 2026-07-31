package com.everyarrow.dittoe.io;

import org.everyarrow.dittoe.schema.Resource;

import java.util.List;
import java.util.Map;

public interface MergePropertyProvider {
    Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources);
}
