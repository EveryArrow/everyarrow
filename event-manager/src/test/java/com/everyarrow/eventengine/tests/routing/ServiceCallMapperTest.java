package com.everyarrow.eventengine.tests.routing;

import com.everyarrow.eventengine.core.adapters.IncomingEvent;
import com.everyarrow.eventengine.core.adapters.ServiceCallMapper;
import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceCallMapperTest {

    @Test
    void mapNormalizesPerchHeadersFromRestEventMetadata() {
        ServiceCall call = ServiceCallMapper.map(new TestIncomingEvent(
                "{\"message\":\"hello\"}",
                Map.of(
                        "header:ea_perch_job_group_execution_instance_id", "group-exec-rest",
                        "header:ea_perch_job_execution_instance_id", "job-exec-rest",
                        "header:ea_perch_task_execution_instance_id", "task-exec-rest"
                )));

        assertEquals("group-exec-rest", call.getMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID));
        assertEquals("job-exec-rest", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("task-exec-rest", call.getMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID));
    }

    @Test
    void mapStillNormalizesQueueStylePerchAttributes() {
        ServiceCall call = ServiceCallMapper.map(new TestIncomingEvent(
                "{\"message\":\"hello\"}",
                Map.of(PerchRuntimeMetadata.QUEUE_JOB_EXECUTION_INSTANCE_ID, "job-exec-queue")));

        assertEquals("job-exec-queue", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
    }

    private record TestIncomingEvent(String body, Map<String, String> metadata) implements IncomingEvent {
        @Override
        public String getBody() {
            return body;
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }

        @Override
        public Instant getTimestamp() {
            return Instant.parse("2026-05-13T00:00:00Z");
        }

        @Override
        public String getTransport() {
            return "REST";
        }
    }
}
