package com.everyarrow.dittoe.utility;

public class Config {
    public static String BASE_DIR = System.getenv().getOrDefault("BASE_DIR", "pipelines");
    public static String LOG_ROOT = System.getenv().getOrDefault("EA_LOG_ROOT", "logs");
    public static String INSTRUMENTATION_URI = System.getenv("INSTRUMENTATION_URI");
    public static String INSTRUMENTATION_SERVICE_TYPE = System.getenv().getOrDefault("INSTRUMENTATION_SERVICE_TYPE", "inmemory"); // Options: "runner", "inmemory"
    public static int PIPELINE_THREADS = Integer.parseInt(System.getenv().getOrDefault("PIPELINE_THREADS", "10"));
    public static int JDBC_FETCH_SIZE = Integer.parseInt(System.getenv().getOrDefault("JDBC_FETCH_SIZE", "10000"));
    public static String PIPELINE_NAME = null;
    public static String PIPELINE_START_TIME = null;
    public static String PIPELINE_METADATA_FILE = "pmd.json";
}
