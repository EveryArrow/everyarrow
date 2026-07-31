/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

#ifndef DITTOE_H
#define DITTOE_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================
 * Constants
 * ======================================================================== */

#define DITTOE_MAX_FIELDS       256
#define DITTOE_MAX_BEHAVIORS    32
#define DITTOE_MAX_MAPPINGS     256
#define DITTOE_MAX_PROPS        64
#define DITTOE_MAX_DEPS         32
#define DITTOE_MAX_STR          1024
#define DITTOE_MAX_JSON         65536

/* ========================================================================
 * Enums (6 schema enums + 3 subtype enums)
 * ======================================================================== */

/* 17 data types */
typedef enum {
    DT_STRING = 0, DT_INTEGER, DT_DOUBLE, DT_DECIMAL, DT_BOOLEAN,
    DT_DATE, DT_DATETIME, DT_TIME, DT_UUID,
    DT_TIME_WITH_TZ, DT_DATETIME_WITH_TZ,
    DT_JSON, DT_JSONB, DT_ARRAY, DT_NUMBER, DT_BYTES, DT_XML,
    DT_COUNT
} DittoeDataType;

/* 22 behavior types */
typedef enum {
    BT_FORMATTING = 0, BT_SOURCE_RETRIEVAL, BT_TARGET_INSERT_UPDATE,
    BT_TRANSFORMATION, BT_ENCRYPTION, BT_LOGGING, BT_INSTRUMENTATION,
    BT_LINEAGE_TRACKING, BT_KEY_GENERATION, BT_MASKING,
    BT_DEDUPLICATION, BT_SCHEMA_EVOLUTION, BT_RETRY,
    BT_SCHEMA_VALIDATION, BT_DQ_RULE_CHECKS, BT_ARCHIVE,
    BT_MICRO_BATCH, BT_DATA_SCIENCE_ALGORITHMS, BT_CUSTOM_CODE,
    BT_COMPRESSION, BT_EXTERNAL_WIDGET, BT_CACHE,
    BT_COUNT
} DittoeBehaviorType;

/* 4 server types */
typedef enum {
    ST_STREAM = 0, ST_API, ST_FILE, ST_DATABASE, ST_COUNT
} DittoeServerType;

/* 16 template types */
typedef enum {
    TT_FILE_TO_FILE = 0, TT_FILE_TO_DATABASE, TT_FILE_TO_STREAM, TT_FILE_TO_API,
    TT_DB_TO_DB, TT_DB_TO_FILE, TT_DB_TO_STREAM, TT_DB_TO_API,
    TT_STREAM_TO_STREAM, TT_STREAM_TO_FILE, TT_STREAM_TO_DB, TT_STREAM_TO_API,
    TT_API_TO_API, TT_API_TO_FILE, TT_API_TO_DB, TT_API_TO_STREAM,
    TT_COUNT
} DittoeTemplateType;

/* 6 stage types (BAPPTE) */
typedef enum {
    STAGE_BEGIN = 0, STAGE_ACQUIRE, STAGE_PROCESS,
    STAGE_PACKAGE, STAGE_TRANSMIT, STAGE_END,
    STAGE_COUNT
} DittoeStageType;

/* 4 process execution types */
typedef enum {
    PET_SYNC = 0, PET_ASYNC, PET_QUEUED, PET_JOINED, PET_COUNT
} DittoeProcessExecType;

/* Database subtypes */
typedef enum {
    DBT_POSTGRESQL = 0, DBT_MYSQL, DBT_MSSQL, DBT_SQLITE, DBT_SNOWFLAKE,
    DBT_COUNT
} DittoeDatabaseType;

/* File subtypes */
typedef enum {
    FT_LOCAL = 0, FT_S3, FT_GCS, FT_AZURE, FT_DROPBOX,
    FT_SFTP, FT_FTP, FT_IBM, FT_ORACLE, FT_DIGITAL_OCEAN, FT_GOOGLE_DRIVE,
    FT_COUNT
} DittoeFileType;

/* API subtypes */
typedef enum {
    AT_BIGQUERY = 0, AT_SALESFORCE, AT_COUNT
} DittoeAPIType;

/* ========================================================================
 * String conversion functions for enums
 * ======================================================================== */

const char* dittoe_data_type_str(DittoeDataType dt);
const char* dittoe_behavior_type_str(DittoeBehaviorType bt);
const char* dittoe_server_type_str(DittoeServerType st);
const char* dittoe_template_type_str(DittoeTemplateType tt);
const char* dittoe_stage_type_str(DittoeStageType st);
const char* dittoe_database_type_str(DittoeDatabaseType dt);
const char* dittoe_file_type_str(DittoeFileType ft);
const char* dittoe_api_type_str(DittoeAPIType at);

/* ========================================================================
 * Strategy constants (fully-qualified Java class names)
 * ======================================================================== */

/* Database read */
extern const char* STRATEGY_JDBC_SELECT;

/* Database write - JDBC */
extern const char* STRATEGY_JDBC_INSERT;
extern const char* STRATEGY_JDBC_REPLACE;

/* Database write - PostgreSQL */
extern const char* STRATEGY_PG_UPSERT;
extern const char* STRATEGY_PG_INSERT_DO_NOTHING;
extern const char* STRATEGY_PG_COPY;
extern const char* STRATEGY_PG_COPY_OR_AUTO_ID;
extern const char* STRATEGY_PG_INSERT_OR_UPDATE;

/* Database write - MSSQL */
extern const char* STRATEGY_MSSQL_MERGE;

/* Database write - SQLite */
extern const char* STRATEGY_SQLITE_UPSERT;

/* File read */
extern const char* STRATEGY_LOCAL_CSV_READ;
extern const char* STRATEGY_LOCAL_JSON_READ;
extern const char* STRATEGY_S3_CSV_READ;
extern const char* STRATEGY_GCS_CSV_READ;
extern const char* STRATEGY_AZURE_CSV_READ;
extern const char* STRATEGY_DROPBOX_CSV_READ;
extern const char* STRATEGY_SFTP_CSV_READ;
extern const char* STRATEGY_FTP_CSV_READ;
extern const char* STRATEGY_IBM_CSV_READ;
extern const char* STRATEGY_ORACLE_CSV_READ;
extern const char* STRATEGY_DO_CSV_READ;
extern const char* STRATEGY_GDRIVE_CSV_READ;

/* File write */
extern const char* STRATEGY_LOCAL_CSV_WRITE;
extern const char* STRATEGY_LOCAL_JSON_WRITE;
extern const char* STRATEGY_S3_CSV_WRITE;
extern const char* STRATEGY_GCS_CSV_WRITE;
extern const char* STRATEGY_AZURE_CSV_WRITE;
extern const char* STRATEGY_DROPBOX_CSV_WRITE;
extern const char* STRATEGY_SFTP_CSV_WRITE;
extern const char* STRATEGY_FTP_CSV_WRITE;
extern const char* STRATEGY_IBM_CSV_WRITE;
extern const char* STRATEGY_ORACLE_CSV_WRITE;
extern const char* STRATEGY_DO_CSV_WRITE;
extern const char* STRATEGY_GDRIVE_CSV_WRITE;

/* API */
extern const char* STRATEGY_BQ_QUERY;
extern const char* STRATEGY_BQ_AVRO;
extern const char* STRATEGY_BQ_WRITE;
extern const char* STRATEGY_SF_READ;
extern const char* STRATEGY_SF_WRITE;

/* ========================================================================
 * Key-Value pair for properties
 * ======================================================================== */

typedef struct {
    char key[DITTOE_MAX_STR];
    char value[DITTOE_MAX_STR];
} DittoeProperty;

/* ========================================================================
 * Structs (12 schema classes)
 * ======================================================================== */

/* Field */
typedef struct {
    char name[DITTOE_MAX_STR];
    DittoeDataType data_type;
    bool is_nullable;
    char description[DITTOE_MAX_STR];
    /* ExpressionField / SourceField support */
    bool has_expression;
    char expression[DITTOE_MAX_STR];
} DittoeField;

/* Server */
typedef struct {
    int id;
    char name[DITTOE_MAX_STR];
    DittoeServerType type;
    DittoeProperty connection_details[DITTOE_MAX_PROPS];
    int conn_detail_count;
    DittoeProperty additional_properties[DITTOE_MAX_PROPS];
    int add_prop_count;
} DittoeServer;

/* Resource */
typedef struct {
    int resource_id;
    char resource_name[DITTOE_MAX_STR];
    DittoeServer server;
    char strategy[DITTOE_MAX_STR];
    DittoeProperty resource_properties[DITTOE_MAX_PROPS];
    int res_prop_count;
    DittoeField fields[DITTOE_MAX_FIELDS];
    int field_count;
} DittoeResource;

/* DQRule */
typedef struct {
    char rule_name[DITTOE_MAX_STR];
    char rule_expression[DITTOE_MAX_STR];
} DittoeDQRule;

/* Behavior */
typedef struct {
    char name[DITTOE_MAX_STR];
    DittoeBehaviorType behavior_type;
    char description[DITTOE_MAX_STR];
    DittoeProperty properties[DITTOE_MAX_PROPS];
    int prop_count;
    /* StagedBehavior fields */
    bool is_staged;
    char plugin_reference[DITTOE_MAX_STR];
    int order;
    DittoeStageType stage_type;
} DittoeBehavior;

/* SourceToTargetMapping */
typedef struct {
    DittoeField source_field;
    DittoeField target_field;
} DittoeS2TMapping;

/* ResourceDependencyMapping */
typedef struct {
    DittoeResource resource;
    DittoeResource dependencies[DITTOE_MAX_DEPS];
    int dep_count;
} DittoeResDependency;

/* PipelineMetadata */
typedef struct {
    char id[DITTOE_MAX_STR];
    char short_name[DITTOE_MAX_STR];
    char description[DITTOE_MAX_STR];
    DittoeTemplateType template_type;
    DittoeResource source_resource;
    DittoeResource target_resource;
    DittoeS2TMapping field_mappings[DITTOE_MAX_MAPPINGS];
    int mapping_count;
    DittoeBehavior behaviors[DITTOE_MAX_BEHAVIORS];
    int behavior_count;
    DittoeProperty global_properties[DITTOE_MAX_PROPS];
    int global_prop_count;
    char create_date[64];
    char created_by[DITTOE_MAX_STR];
    char last_updated_by[DITTOE_MAX_STR];
    char last_updated_date[64];
} DittoePipeline;

/* Execution Result */
typedef struct {
    bool success;
    int return_code;
    char stdout_buf[DITTOE_MAX_JSON];
    char stderr_buf[DITTOE_MAX_JSON];
    int error_count;
    int rows_processed;
} DittoeExecResult;

/* ========================================================================
 * Init / helper functions
 * ======================================================================== */

void dittoe_field_init(DittoeField* f, const char* name, DittoeDataType dt, bool nullable);
void dittoe_field_set_expression(DittoeField* f, const char* expr);
void dittoe_field_set_description(DittoeField* f, const char* desc);

void dittoe_server_init(DittoeServer* s, const char* name, DittoeServerType type);
void dittoe_server_add_conn_detail(DittoeServer* s, const char* key, const char* value);
void dittoe_server_add_property(DittoeServer* s, const char* key, const char* value);

/* Convenience: database server setup */
void dittoe_server_postgresql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);
void dittoe_server_mysql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);
void dittoe_server_mssql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);
void dittoe_server_sqlite(DittoeServer* s, const char* name, const char* path);
void dittoe_server_snowflake(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);

/* Convenience: file server setup */
void dittoe_server_local_file(DittoeServer* s, const char* name, const char* path);
void dittoe_server_s3(DittoeServer* s, const char* name,
    const char* bucket, const char* region,
    const char* access_key, const char* secret_key);
void dittoe_server_sftp(DittoeServer* s, const char* name,
    const char* host, int port, const char* path,
    const char* user, const char* password);

/* Convenience: API server setup */
void dittoe_server_bigquery(DittoeServer* s, const char* name,
    const char* project_id, const char* dataset, const char* creds_file);
void dittoe_server_salesforce(DittoeServer* s, const char* name,
    const char* login_url, const char* username,
    const char* password, const char* token);

/* Resource */
void dittoe_resource_init(DittoeResource* r, const char* name, DittoeServer* server);
void dittoe_resource_add_field(DittoeResource* r, const char* name,
    DittoeDataType dt, bool nullable);
void dittoe_resource_set_strategy(DittoeResource* r, const char* strategy);
void dittoe_resource_set_schema(DittoeResource* r, const char* schema);
void dittoe_resource_add_property(DittoeResource* r, const char* key, const char* value);

/* Behavior */
void dittoe_behavior_init(DittoeBehavior* b, const char* name, DittoeBehaviorType type);
void dittoe_behavior_set_description(DittoeBehavior* b, const char* desc);
void dittoe_behavior_add_property(DittoeBehavior* b, const char* key, const char* value);
void dittoe_behavior_set_error_threshold(DittoeBehavior* b, int threshold);
void dittoe_behavior_set_staged(DittoeBehavior* b, DittoeStageType stage, int order);

/* Pipeline */
void dittoe_pipeline_init(DittoePipeline* p, const char* short_name, DittoeTemplateType tt);
void dittoe_pipeline_set_source(DittoePipeline* p, DittoeResource* source);
void dittoe_pipeline_set_target(DittoePipeline* p, DittoeResource* target);
void dittoe_pipeline_add_mapping(DittoePipeline* p, DittoeField* src, DittoeField* tgt);
void dittoe_pipeline_map_same_name(DittoePipeline* p);
void dittoe_pipeline_add_behavior(DittoePipeline* p, DittoeBehavior* b);
void dittoe_pipeline_add_default_behaviors(DittoePipeline* p);
void dittoe_pipeline_add_global_prop(DittoePipeline* p, const char* key, const char* value);

/* ========================================================================
 * JSON Serialization
 * ======================================================================== */

int dittoe_field_to_json(const DittoeField* f, char* buf, int buf_size);
int dittoe_server_to_json(const DittoeServer* s, char* buf, int buf_size);
int dittoe_resource_to_json(const DittoeResource* r, char* buf, int buf_size);
int dittoe_behavior_to_json(const DittoeBehavior* b, char* buf, int buf_size);
int dittoe_pipeline_to_json(const DittoePipeline* p, char* buf, int buf_size);

/* Save PMD JSON to file */
int dittoe_pipeline_save(const DittoePipeline* p, const char* path);

/* ========================================================================
 * Executor
 * ======================================================================== */

/* Executor command safety helpers. */
bool dittoe_is_valid_pipeline_name(const char* name);
bool dittoe_is_safe_executor_path(const char* path);

DittoeExecResult dittoe_execute(const char* jar_path, DittoePipeline* pipeline,
    const char* base_dir, int timeout_sec);

/* Validate pipeline before execution */
int dittoe_pipeline_validate(const DittoePipeline* p, char* errors, int buf_size);

/* ========================================================================
 * Config
 * ======================================================================== */

typedef struct {
    char jar_path[DITTOE_MAX_STR];
    char base_dir[DITTOE_MAX_STR];
    char java_path[DITTOE_MAX_STR];
    char java_opts[DITTOE_MAX_PROPS][DITTOE_MAX_STR];
    int java_opts_count;
    int timeout;
} DittoeConfig;

void dittoe_config_init(DittoeConfig* cfg);
void dittoe_config_from_env(DittoeConfig* cfg);
int dittoe_config_validate(const DittoeConfig* cfg, char* errors, int buf_size);

/* ========================================================================
 * Facade: Connection + migrate
 * ======================================================================== */

typedef struct {
    char name[DITTOE_MAX_STR];
    DittoeServer server;
    DittoeField fields[DITTOE_MAX_FIELDS];
    int field_count;
    char strategy[DITTOE_MAX_STR];
    char schema_name[DITTOE_MAX_STR];
    DittoeProperty properties[DITTOE_MAX_PROPS];
    int prop_count;
} DittoeConnection;

void dittoe_connection_init_pg(DittoeConnection* c, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);
void dittoe_connection_init_mysql(DittoeConnection* c, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password);
void dittoe_connection_init_local_file(DittoeConnection* c, const char* name,
    const char* path);
void dittoe_connection_init_s3(DittoeConnection* c, const char* name,
    const char* bucket, const char* region,
    const char* access_key, const char* secret_key);

void dittoe_connection_add_field(DittoeConnection* c, const char* name,
    DittoeDataType dt, bool nullable);
void dittoe_connection_set_schema(DittoeConnection* c, const char* schema);
void dittoe_connection_set_as_source(DittoeConnection* c);
void dittoe_connection_set_as_target(DittoeConnection* c);
int dittoe_connection_validate(const DittoeConnection* c, char* errors, int buf_size);

const char* dittoe_default_read_strategy(DittoeServerType type, const char* subtype);
const char* dittoe_default_write_strategy(DittoeServerType type, const char* subtype);

DittoeExecResult dittoe_migrate(const DittoeConfig* cfg,
    DittoeConnection* source, DittoeConnection* target,
    const char* pipeline_name);

#ifdef __cplusplus
}
#endif

#endif /* DITTOE_H */
