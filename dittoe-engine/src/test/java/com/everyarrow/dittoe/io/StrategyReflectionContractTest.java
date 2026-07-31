package com.everyarrow.dittoe.io;

import com.everyarrow.utilities.reflection.Reflection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyReflectionContractTest {
    private static final Path MAIN_JAVA_ROOT = Path.of("src", "main", "java");

    @Test
    void concreteStrategiesCanBeLoadedThroughEngineReflection() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Class<?> strategyClass : discoverStrategyClasses()) {
            try {
                Reflection.instantiateClass(strategyClass.getName());
            } catch (RuntimeException e) {
                failures.add(strategyClass.getName() + ": " + rootCauseMessage(e));
            }
        }

        assertTrue(failures.isEmpty(),
                "Concrete read/write strategies must expose public no-arg constructors for PMD loading: " + failures);
    }

    private static List<Class<?>> discoverStrategyClasses() throws Exception {
        Path ioRoot = MAIN_JAVA_ROOT.resolve(Path.of("com", "everyarrow", "dittoe", "io"));
        try (Stream<Path> paths = Files.walk(ioRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Strategy.java"))
                    .map(StrategyReflectionContractTest::loadClass)
                    .filter(StrategyReflectionContractTest::isConcreteReadOrWriteStrategy)
                    .sorted(Comparator.comparing(Class::getName))
                    .toList();
        }
    }

    private static boolean isConcreteReadOrWriteStrategy(Class<?> strategyClass) {
        return (ReadStrategy.class.isAssignableFrom(strategyClass)
                || WriteStrategy.class.isAssignableFrom(strategyClass))
                && !Modifier.isAbstract(strategyClass.getModifiers());
    }

    private static Class<?> loadClass(Path sourceFile) {
        Path relativeSourceFile = MAIN_JAVA_ROOT.relativize(sourceFile);
        String className = relativeSourceFile.toString()
                .replace('\\', '.')
                .replace('/', '.')
                .replace(".java", "");

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load strategy class " + className, e);
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
