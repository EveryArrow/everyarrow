package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.pipelines.behaviors.schemavalidation.JSONSchemaGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JSONSchemaGeneratorSecurityTest {
    @Test
    void generatesSchemaWithoutWritingPojoSourceFiles() throws Exception {
        Path legacyPojoDirectory = Path.of("pojo classes");
        boolean legacyPojoDirectoryExisted = Files.exists(legacyPojoDirectory);
        Resource resource = new Resource();
        resource.setResourceName("../UnsafeName");
        resource.setFields(List.of(
                new Field("created_at", DataType.DateTime),
                new Field("count", DataType.Integer)));

        String schema = new JSONSchemaGenerator(resource).generate();
        JsonNode parsed = new ObjectMapper().readTree(schema);

        assertEquals("date-time", parsed.get("properties").get("created_at").get("format").asText());
        assertEquals("integer", parsed.get("properties").get("count").get("type").asText());
        assertEquals(legacyPojoDirectoryExisted, Files.exists(legacyPojoDirectory));
    }
}
