package com.everyarrow.dittoe;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

/**
 * Unified Log4j2 configuration for pipeline-scoped logging:
 *   - SUCCESS (DEBUG/INFO)
 *   - ERROR (WARN/ERROR/FATAL)
 *   - Console output
 *   - WebSocket/Socket JSON output with metadata
 */
public class Log4jConfig {

    public static void configure(String logDirectory) {
        ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationFactory.newConfigurationBuilder();

        builder.setConfigurationName("PipelineLogConfig");
        builder.setStatusLevel(Level.WARN);

        // ---------------------------
        // Layouts
        // ---------------------------
        String pattern = "[%d{dd MMM yyyy HH:mm:ss.SSS} - %rms] [%t] [%p] [%c:%L]: %m%n";
        LayoutComponentBuilder textLayout = builder.newLayout("PatternLayout")
                .addAttribute("pattern", pattern);

        // ---------------------------
        // Console Appender
        // ---------------------------
        AppenderComponentBuilder console = builder
                .newAppender("CONSOLE", "Console")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(textLayout);

        builder.add(console);

        // ---------------------------
        // SUCCESS Appender
        // ---------------------------
        AppenderComponentBuilder success = builder
                .newAppender("SUCCESS", "File")
                .addAttribute("fileName", logDirectory + "/dittoe-engine.log")
                .addAttribute("append", false)
                .add(textLayout);

        success.add(builder.newFilter(
                        "ThresholdFilter",
                        Filter.Result.DENY,
                        Filter.Result.NEUTRAL)
                .addAttribute("level", Level.WARN));

        builder.add(success);

        // ---------------------------
        // ERROR Appender
        // ---------------------------
        AppenderComponentBuilder error = builder
                .newAppender("ERROR", "File")
                .addAttribute("fileName", logDirectory + "/dittoe-engine.error.log")
                .addAttribute("append", false)
                .add(textLayout);

        error.add(builder.newFilter(
                        "ThresholdFilter",
                        Filter.Result.ACCEPT,
                        Filter.Result.DENY)
                .addAttribute("level", Level.WARN));

        builder.add(error);

        // ---------------------------
        // Noisy library log level suppression
        // ---------------------------
        builder.add(builder.newLogger("com.zaxxer.hikari", Level.INFO));
        builder.add(builder.newLogger("net.snowflake.client", Level.INFO));
        builder.add(builder.newLogger("io.grpc.netty", Level.INFO));
        builder.add(builder.newLogger("software.amazon.awssdk", Level.INFO));
        builder.add(builder.newLogger("org.apache.http", Level.INFO));

        // ---------------------------
        // Root logger
        // ---------------------------
        RootLoggerComponentBuilder root = builder.newRootLogger(Level.INFO);

        root.add(builder.newAppenderRef("CONSOLE"));
        root.add(builder.newAppenderRef("SUCCESS"));
        root.add(builder.newAppenderRef("ERROR"));

        builder.add(root);

        Configurator.reconfigure(builder.build());
    }
}
