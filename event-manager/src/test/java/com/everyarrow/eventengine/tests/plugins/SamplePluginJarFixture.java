package com.everyarrow.eventengine.tests.plugins;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

final class SamplePluginJarFixture {
    private static Path compiledJar;

    private SamplePluginJarFixture() {
    }

    static synchronized Path build() throws IOException {
        if (compiledJar != null && Files.exists(compiledJar)) {
            return compiledJar;
        }

        Path sourceRoot = locateSourceRoot();
        Path classesRoot = Files.createTempDirectory("ea-sample-plugin-classes");
        compile(sourceRoot, classesRoot);

        compiledJar = Files.createTempFile("ea-sample-test-plugin-", ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(compiledJar));
             Stream<Path> files = Files.walk(classesRoot)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = classesRoot.relativize(file).toString().replace('\\', '/');
                jar.putNextEntry(new JarEntry(entryName));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
        return compiledJar;
    }

    private static Path locateSourceRoot() {
        List<Path> candidates = List.of(
                Path.of("plugins", "Source", "plugin-sample", "src", "main", "java"),
                Path.of("event-manager", "plugins", "Source", "plugin-sample", "src", "main", "java"));
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Checked-in sample plugin source directory was not found"));
    }

    private static void compile(Path sourceRoot, Path classesRoot) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("A JDK compiler is required to build the sample plugin fixture");
        }

        List<Path> sources;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            sources = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        if (sources.isEmpty()) {
            throw new AssertionError("Sample plugin fixture has no Java sources under " + sourceRoot);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of(
                    "--release", "21",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesRoot.toString());
            boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!compiled) {
                throw new AssertionError("Sample plugin compilation failed:\n" + diagnosticText(diagnostics));
            }
        }
    }

    private static String diagnosticText(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            message.append(diagnostic.getKind())
                    .append(" ")
                    .append(diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName())
                    .append(":")
                    .append(diagnostic.getLineNumber())
                    .append(" ")
                    .append(diagnostic.getMessage(null))
                    .append(System.lineSeparator());
        }
        return message.toString();
    }
}
