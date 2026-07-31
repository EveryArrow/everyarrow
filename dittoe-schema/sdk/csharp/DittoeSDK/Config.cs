// DITTOE SDK for C# - Configuration
// Centralizes runtime settings with environment variable support and validation.

using System;
using System.Collections.Generic;
using System.IO;

namespace DittoeSDK
{
    /// <summary>
    /// Exception thrown when DITTOE configuration validation fails.
    /// </summary>
    public class DittoeConfigException : Exception
    {
        public DittoeConfigException(string message) : base(message) { }
    }

    /// <summary>
    /// Central configuration for DITTOE SDK.
    /// All settings can be loaded from environment variables with sensible defaults.
    /// 
    /// Environment variables:
    ///   DITTOE_JAR      - Path to the DITTOE execution JAR (required)
    ///   DITTOE_BASE_DIR - Base directory for pipeline files (default: ./pipelines)
    ///   DITTOE_JAVA     - Path to java executable (default: java)
    ///   DITTOE_JAVA_OPTS - Comma-separated JVM options (default: empty)
    ///   DITTOE_TIMEOUT  - Default execution timeout in seconds (default: 300)
    /// </summary>
    public class DittoeConfig
    {
        public string JarPath { get; set; } = "";
        public string BaseDir { get; set; } = "./pipelines";
        public string JavaPath { get; set; } = "java";
        public List<string> JavaOpts { get; set; } = new();
        public int Timeout { get; set; } = 300;

        /// <summary>
        /// Load configuration from environment variables with defaults.
        /// </summary>
        public static DittoeConfig FromEnv()
        {
            var javaOptsStr = Environment.GetEnvironmentVariable("DITTOE_JAVA_OPTS") ?? "";
            var javaOpts = new List<string>();
            if (!string.IsNullOrEmpty(javaOptsStr))
            {
                foreach (var opt in javaOptsStr.Split(','))
                {
                    var trimmed = opt.Trim();
                    if (!string.IsNullOrEmpty(trimmed)) javaOpts.Add(trimmed);
                }
            }

            var timeoutStr = Environment.GetEnvironmentVariable("DITTOE_TIMEOUT") ?? "300";
            if (!int.TryParse(timeoutStr, out int timeout)) timeout = 300;

            return new DittoeConfig
            {
                JarPath = Environment.GetEnvironmentVariable("DITTOE_JAR") ?? "",
                BaseDir = Environment.GetEnvironmentVariable("DITTOE_BASE_DIR") ?? "./pipelines",
                JavaPath = Environment.GetEnvironmentVariable("DITTOE_JAVA") ?? "java",
                JavaOpts = javaOpts,
                Timeout = timeout,
            };
        }

        /// <summary>
        /// Validate this configuration. Throws DittoeConfigException on failure.
        /// Returns list of error messages (empty if valid).
        /// </summary>
        public List<string> Validate()
        {
            var errors = new List<string>();

            if (string.IsNullOrEmpty(JarPath))
                errors.Add("JarPath is required (set DITTOE_JAR env var or pass directly)");
            else if (!File.Exists(JarPath))
                errors.Add($"JAR file not found: {JarPath}");

            if (string.IsNullOrEmpty(JavaPath))
                errors.Add("JavaPath is required");

            if (string.IsNullOrEmpty(BaseDir))
                errors.Add("BaseDir is required");

            if (Timeout <= 0)
                errors.Add($"Timeout must be positive, got: {Timeout}");

            if (errors.Count > 0)
                throw new DittoeConfigException(
                    "Configuration validation failed:\n" +
                    string.Join("\n", errors.ConvertAll(e => $"  - {e}"))
                );

            return errors;
        }

        /// <summary>
        /// Create BaseDir if it doesn't exist, return absolute path.
        /// </summary>
        public string EnsureBaseDir()
        {
            var absPath = Path.GetFullPath(BaseDir);
            Directory.CreateDirectory(absPath);
            return absPath;
        }
    }
}
