package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerchRuntimeMetadataTest {

    @Test
    void normalizesQueuePropertiesIntoServiceCallMetadata() {
        ServiceCall call = new ServiceCall();

        PerchRuntimeMetadata.normalizeFromQueueAttributes(call, Map.of(
                PerchRuntimeMetadata.QUEUE_JOB_GROUP_EXECUTION_INSTANCE_ID, "group-exec-1",
                PerchRuntimeMetadata.QUEUE_JOB_EXECUTION_INSTANCE_ID, "job-exec-1",
                PerchRuntimeMetadata.QUEUE_TASK_EXECUTION_INSTANCE_ID, "task-exec-1"));

        assertEquals("group-exec-1", call.getMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID));
        assertEquals("job-exec-1", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("task-exec-1", call.getMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID));
    }
}
