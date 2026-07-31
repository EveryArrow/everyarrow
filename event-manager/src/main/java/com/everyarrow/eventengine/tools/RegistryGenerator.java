package com.everyarrow.eventengine.tools;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.utils.ReflectionHelper;
import com.everyarrow.utilities.reflection.Reflection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scans classes within com.everyarrow.eventengine and its plugins
 * for @Monitored, @Secured, @RestRoute, and @Webhook annotations,
 * then generates a JSON registry file at build time.
 */
public class RegistryGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OUTPUT_FILE = "src/main/resources/service_registry.json";

    public static void main(String[] args) throws Exception {


        List<ServiceCallDefinition> registry = generateRegistry("com.everyarrow.eventengine");
        writeRegistryToJson(registry, OUTPUT_FILE);
        System.out.println("✅ Registry generated: " + OUTPUT_FILE);
    }

    public static List<ServiceCallDefinition> generateRegistry(String basePackage) throws IOException, ClassNotFoundException {
        List<ServiceCallDefinition> definitions = new ArrayList<>();
        List<Class<?>> classes = Reflection.getClassesInPackage(basePackage);
        ServiceCallDefinition def = null;
        for (Class<?> clazz : classes) {
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) continue;
            try {
                def = ReflectionHelper.extractDefinitionFromAnnotations(clazz);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (def != null) definitions.add(def);
        }
        return definitions;
    }

    private static void writeRegistryToJson(List<ServiceCallDefinition> defs, String filePath) throws IOException {
        Path path = Path.of(filePath);
        Files.createDirectories(path.getParent());
        try (FileWriter writer = new FileWriter(path.toFile())) {
            GSON.toJson(defs, writer);
        }
    }
}
