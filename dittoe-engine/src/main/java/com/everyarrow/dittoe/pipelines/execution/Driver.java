package com.everyarrow.dittoe.pipelines.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point for DITTOE pipeline execution.
 */
public class Driver {
    private static final Logger logger = LoggerFactory.getLogger(Driver.class);

    /**
     * Main method.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Compatibility wrapper for existing callers.
     *
     * @deprecated use {@link #run(String[])}.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static void driver(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws Exception {
        new PipelineRunApplication().run(args);
    }
}
