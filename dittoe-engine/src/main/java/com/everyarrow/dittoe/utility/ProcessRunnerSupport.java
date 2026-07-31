package com.everyarrow.dittoe.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

final class ProcessRunnerSupport {
    private ProcessRunnerSupport() {
    }

    static String run(File workingDirectory, Map<String, String> environment,
                      String... arguments)
            throws IOException, InterruptedException, Utils.ProcessRunnerException {
        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder
                .directory(workingDirectory)
                .environment().putAll(environment);

        Process process = processBuilder.start();
        String log = captureLog(process.getInputStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Utils.ProcessRunnerException("Shell script execution failed with exit code %d. Error message: %s"
                    .formatted(exitCode, captureLog(process.getErrorStream())));
        }

        return log;
    }

    private static String captureLog(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder output = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }

            return output.toString();
        }
    }
}
