package com.everyarrow.dittoe.io;

import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;

import java.util.List;
import java.util.Map;

public interface ResourceFieldProvider {
    Map<String, List<Field>> getResourcesFields(List<Resource> resources);
}
