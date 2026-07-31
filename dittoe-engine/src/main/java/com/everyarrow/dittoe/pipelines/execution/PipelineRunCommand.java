package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

import java.io.IOException;

final class PipelineRunCommand {
    private PipelineRunCommand() {
    }

    static PipelineRunRequest parse(String[] args) throws ExecutionException {
        Options options = new Options();
        options.addOption("p", "pipeline-name", true, "Specify Pipeline Name");

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            printHelp(options);
            throw new ExecutionException("Error parsing command line arguments: " + e.getMessage());
        }

        String pipelineName = commandLine.getOptionValue("pipeline-name");
        if (pipelineName == null || pipelineName.trim().isEmpty()) {
            throw new ExecutionException("Pipeline name is required. Use -p or --pipeline-name option.");
        }

        return new PipelineRunRequest(pipelineName);
    }

    private static void printHelp(Options options) throws ExecutionException {
        try {
            HelpFormatter formatter = HelpFormatter.builder().get();
            formatter.printHelp("Driver", "Available options:", options, "", true);
        } catch (IOException e) {
            throw new ExecutionException("Failed to print command line help: " + e.getMessage());
        }
    }
}
