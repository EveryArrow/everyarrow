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

/*
 * DITTOE SDK for C - Implementation
 * JSON builder using string concatenation. No external dependencies.
 */

#define _POSIX_C_SOURCE 200809L
#include "dittoe.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <ctype.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

/* ========================================================================
 * Enum string tables
 * ======================================================================== */

static const char* DATA_TYPE_STRINGS[] = {
    "String", "Integer", "Double", "Decimal", "Boolean",
    "Date", "DateTime", "Time", "UUID",
    "TimeWithTimeZone", "DateTimeWithTimeZone",
    "JSON", "JSONB", "Array", "Number", "Bytes", "XML"
};

static const char* BEHAVIOR_TYPE_STRINGS[] = {
    "Formatting", "SourceRetrieval", "TargetInsertUpdate",
    "Transformation", "Encryption", "Logging", "Instrumentation",
    "lineageTracking", "KeyGeneration", "Masking",
    "Deduplication", "SchemaEvolution", "Retry",
    "SchemaValidation", "DQRuleChecks", "Archive",
    "MicroBatch", "DataScienceAlgorithms", "CustomCode",
    "Compression", "ExternalWidget", "Cache"
};

static const char* SERVER_TYPE_STRINGS[] = {
    "Stream", "API", "File", "Database"
};

static const char* TEMPLATE_TYPE_STRINGS[] = {
    "FileToFile", "FileToDatabase", "FileToStream", "FileToAPI",
    "DatabaseToDatabase", "DatabaseToFile", "DatabaseToStream", "DatabaseToAPI",
    "StreamToStream", "StreamToFile", "StreamToDatabase", "StreamToAPI",
    "APIToAPI", "APIToFile", "APIToDatabase", "APIToStream"
};

static const char* STAGE_TYPE_STRINGS[] = {
    "Begin", "Acquire", "Process", "Package", "Transmit", "End"
};

static const char* DATABASE_TYPE_STRINGS[] = {
    "PostgreSQLServer", "MySQLServer", "MSSQLServer", "SQLiteServer", "SnowflakeServer"
};

static const char* FILE_TYPE_STRINGS[] = {
    "LocalFileServer", "S3Server", "GCSServer", "AzureServer", "DropboxServer",
    "SFTPServer", "FTPServer", "IBMServer", "OracleServer", "DigitalOceanServer", "GoogleDriveServer"
};

static const char* API_TYPE_STRINGS[] = {
    "BigQueryServer", "SalesforceServer"
};

const char* dittoe_data_type_str(DittoeDataType dt)     { return (dt < DT_COUNT) ? DATA_TYPE_STRINGS[dt] : "String"; }
const char* dittoe_behavior_type_str(DittoeBehaviorType bt) { return (bt < BT_COUNT) ? BEHAVIOR_TYPE_STRINGS[bt] : "SourceRetrieval"; }
const char* dittoe_server_type_str(DittoeServerType st)  { return (st < ST_COUNT) ? SERVER_TYPE_STRINGS[st] : "Database"; }
const char* dittoe_template_type_str(DittoeTemplateType tt) { return (tt < TT_COUNT) ? TEMPLATE_TYPE_STRINGS[tt] : "DatabaseToDatabase"; }
const char* dittoe_stage_type_str(DittoeStageType st)    { return (st < STAGE_COUNT) ? STAGE_TYPE_STRINGS[st] : "Process"; }
const char* dittoe_database_type_str(DittoeDatabaseType dt) { return (dt < DBT_COUNT) ? DATABASE_TYPE_STRINGS[dt] : "PostgreSQLServer"; }
const char* dittoe_file_type_str(DittoeFileType ft)      { return (ft < FT_COUNT) ? FILE_TYPE_STRINGS[ft] : "LocalFileServer"; }
const char* dittoe_api_type_str(DittoeAPIType at)        { return (at < AT_COUNT) ? API_TYPE_STRINGS[at] : "BigQueryServer"; }

/* ========================================================================
 * Strategy constant definitions (all 45)
 * ======================================================================== */

/* Database read */
const char* STRATEGY_JDBC_SELECT = "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy";

/* Database write - JDBC */
const char* STRATEGY_JDBC_INSERT = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy";
const char* STRATEGY_JDBC_REPLACE = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.ReplaceStrategy";

/* Database write - PostgreSQL */
const char* STRATEGY_PG_UPSERT = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy";
const char* STRATEGY_PG_INSERT_DO_NOTHING = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertDoNothingOnConflictStrategy";
const char* STRATEGY_PG_COPY = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy";
const char* STRATEGY_PG_COPY_OR_AUTO_ID = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyOrAutoIdStrategy";
const char* STRATEGY_PG_INSERT_OR_UPDATE = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertOrUpdateStrategy";

/* Database write - MSSQL */
const char* STRATEGY_MSSQL_MERGE = "com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy";

/* Database write - SQLite */
const char* STRATEGY_SQLITE_UPSERT = "com.everyarrow.dittoe.io.database.servers.sqlite.writer.strategy.UpsertOnConflictStrategy";

/* File read */
const char* STRATEGY_LOCAL_CSV_READ = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.CSVReadStrategy";
const char* STRATEGY_LOCAL_JSON_READ = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.JSONReadStrategy";
const char* STRATEGY_S3_CSV_READ = "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.CSVReadStrategy";
const char* STRATEGY_GCS_CSV_READ = "com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy.CSVReadStrategy";
const char* STRATEGY_AZURE_CSV_READ = "com.everyarrow.dittoe.io.file.servers.azure.reader.strategy.CSVReadStrategy";
const char* STRATEGY_DROPBOX_CSV_READ = "com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy.CSVReadStrategy";
const char* STRATEGY_SFTP_CSV_READ = "com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy.CSVReadStrategy";
const char* STRATEGY_FTP_CSV_READ = "com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy.CSVReadStrategy";
const char* STRATEGY_IBM_CSV_READ = "com.everyarrow.dittoe.io.file.servers.ibm.reader.strategy.CSVReadStrategy";
const char* STRATEGY_ORACLE_CSV_READ = "com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy.CSVReadStrategy";
const char* STRATEGY_DO_CSV_READ = "com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy.CSVReadStrategy";
const char* STRATEGY_GDRIVE_CSV_READ = "com.everyarrow.dittoe.io.file.servers.googledrive.reader.strategy.CSVReadStrategy";

/* File write */
const char* STRATEGY_LOCAL_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_LOCAL_JSON_WRITE = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.JSONWriteStrategy";
const char* STRATEGY_S3_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.s3.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_GCS_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_AZURE_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.azure.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_DROPBOX_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_SFTP_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_FTP_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_IBM_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_ORACLE_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_DO_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.digitalocean.writer.strategy.CSVWriteStrategy";
const char* STRATEGY_GDRIVE_CSV_WRITE = "com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy.CSVWriteStrategy";

/* API */
const char* STRATEGY_BQ_QUERY = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.QueryStrategy";
const char* STRATEGY_BQ_AVRO = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.AvroStrategy";
const char* STRATEGY_BQ_WRITE = "com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.DataStoreStrategy";
const char* STRATEGY_SF_READ = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI";
const char* STRATEGY_SF_WRITE = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI";

/* ========================================================================
 * Internal JSON helpers
 * ======================================================================== */

static int json_append(char* buf, int pos, int buf_size, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int written = vsnprintf(buf + pos, buf_size - pos, fmt, args);
    va_end(args);
    if (written < 0 || pos + written >= buf_size) return buf_size - 1;
    return pos + written;
}

/* Escape JSON string */
static void json_escape(const char* src, char* dst, int dst_size) {
    int j = 0;
    for (int i = 0; src[i] && j < dst_size - 2; i++) {
        switch (src[i]) {
            case '"':  if (j + 2 < dst_size) { dst[j++] = '\\'; dst[j++] = '"'; } break;
            case '\\': if (j + 2 < dst_size) { dst[j++] = '\\'; dst[j++] = '\\'; } break;
            case '\n': if (j + 2 < dst_size) { dst[j++] = '\\'; dst[j++] = 'n'; } break;
            case '\t': if (j + 2 < dst_size) { dst[j++] = '\\'; dst[j++] = 't'; } break;
            default:   dst[j++] = src[i]; break;
        }
    }
    dst[j] = '\0';
}

/* Write a map of DittoeProperty entries as JSON object */
static int json_write_props(char* buf, int pos, int buf_size,
                             const DittoeProperty* props, int count) {
    pos = json_append(buf, pos, buf_size, "{");
    for (int i = 0; i < count; i++) {
        char ek[DITTOE_MAX_STR * 2], ev[DITTOE_MAX_STR * 2];
        json_escape(props[i].key, ek, sizeof(ek));
        json_escape(props[i].value, ev, sizeof(ev));
        pos = json_append(buf, pos, buf_size, "%s\"%s\":\"%s\"",
                          i > 0 ? "," : "", ek, ev);
    }
    pos = json_append(buf, pos, buf_size, "}");
    return pos;
}

/* ========================================================================
 * Init functions
 * ======================================================================== */

void dittoe_field_init(DittoeField* f, const char* name, DittoeDataType dt, bool nullable) {
    memset(f, 0, sizeof(*f));
    strncpy(f->name, name, DITTOE_MAX_STR - 1);
    f->data_type = dt;
    f->is_nullable = nullable;
}

void dittoe_field_set_expression(DittoeField* f, const char* expr) {
    f->has_expression = true;
    strncpy(f->expression, expr, DITTOE_MAX_STR - 1);
}

void dittoe_field_set_description(DittoeField* f, const char* desc) {
    strncpy(f->description, desc, DITTOE_MAX_STR - 1);
}

void dittoe_server_init(DittoeServer* s, const char* name, DittoeServerType type) {
    memset(s, 0, sizeof(*s));
    strncpy(s->name, name, DITTOE_MAX_STR - 1);
    s->type = type;
}

void dittoe_server_add_conn_detail(DittoeServer* s, const char* key, const char* value) {
    if (s->conn_detail_count < DITTOE_MAX_PROPS) {
        strncpy(s->connection_details[s->conn_detail_count].key, key, DITTOE_MAX_STR - 1);
        strncpy(s->connection_details[s->conn_detail_count].value, value, DITTOE_MAX_STR - 1);
        s->conn_detail_count++;
    }
}

void dittoe_server_add_property(DittoeServer* s, const char* key, const char* value) {
    if (s->add_prop_count < DITTOE_MAX_PROPS) {
        strncpy(s->additional_properties[s->add_prop_count].key, key, DITTOE_MAX_STR - 1);
        strncpy(s->additional_properties[s->add_prop_count].value, value, DITTOE_MAX_STR - 1);
        s->add_prop_count++;
    }
}

/* ========================================================================
 * Database server convenience
 * ======================================================================== */

static void setup_db_server(DittoeServer* s, const char* name, DittoeDatabaseType dbt,
                             const char* host, int port, const char* database,
                             const char* user, const char* password) {
    dittoe_server_init(s, name, ST_DATABASE);
    dittoe_server_add_conn_detail(s, "databaseType", dittoe_database_type_str(dbt));
    dittoe_server_add_conn_detail(s, "host", host);
    char port_str[16]; snprintf(port_str, sizeof(port_str), "%d", port);
    dittoe_server_add_conn_detail(s, "port", port_str);
    dittoe_server_add_conn_detail(s, "database", database);
    dittoe_server_add_conn_detail(s, "user", user);
    dittoe_server_add_conn_detail(s, "password", password);
}

void dittoe_server_postgresql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    setup_db_server(s, name, DBT_POSTGRESQL, host, port, database, user, password);
}

void dittoe_server_mysql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    setup_db_server(s, name, DBT_MYSQL, host, port, database, user, password);
}

void dittoe_server_mssql(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    setup_db_server(s, name, DBT_MSSQL, host, port, database, user, password);
}

void dittoe_server_sqlite(DittoeServer* s, const char* name, const char* path) {
    dittoe_server_init(s, name, ST_DATABASE);
    dittoe_server_add_conn_detail(s, "databaseType", dittoe_database_type_str(DBT_SQLITE));
    dittoe_server_add_conn_detail(s, "path", path);
}

void dittoe_server_snowflake(DittoeServer* s, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    setup_db_server(s, name, DBT_SNOWFLAKE, host, port, database, user, password);
}

/* ========================================================================
 * File server convenience
 * ======================================================================== */

void dittoe_server_local_file(DittoeServer* s, const char* name, const char* path) {
    dittoe_server_init(s, name, ST_FILE);
    dittoe_server_add_conn_detail(s, "fileType", dittoe_file_type_str(FT_LOCAL));
    dittoe_server_add_conn_detail(s, "path", path);
}

void dittoe_server_s3(DittoeServer* s, const char* name,
    const char* bucket, const char* region,
    const char* access_key, const char* secret_key) {
    dittoe_server_init(s, name, ST_FILE);
    dittoe_server_add_conn_detail(s, "fileType", dittoe_file_type_str(FT_S3));
    dittoe_server_add_conn_detail(s, "bucket", bucket);
    dittoe_server_add_conn_detail(s, "region", region);
    dittoe_server_add_conn_detail(s, "accessKey", access_key);
    dittoe_server_add_conn_detail(s, "secretKey", secret_key);
}

void dittoe_server_sftp(DittoeServer* s, const char* name,
    const char* host, int port, const char* path,
    const char* user, const char* password) {
    dittoe_server_init(s, name, ST_FILE);
    dittoe_server_add_conn_detail(s, "fileType", dittoe_file_type_str(FT_SFTP));
    dittoe_server_add_conn_detail(s, "host", host);
    char port_str[16]; snprintf(port_str, sizeof(port_str), "%d", port);
    dittoe_server_add_conn_detail(s, "port", port_str);
    dittoe_server_add_conn_detail(s, "path", path);
    dittoe_server_add_conn_detail(s, "user", user);
    dittoe_server_add_conn_detail(s, "password", password);
}

/* ========================================================================
 * API server convenience
 * ======================================================================== */

void dittoe_server_bigquery(DittoeServer* s, const char* name,
    const char* project_id, const char* dataset, const char* creds_file) {
    dittoe_server_init(s, name, ST_API);
    dittoe_server_add_conn_detail(s, "apiType", dittoe_api_type_str(AT_BIGQUERY));
    dittoe_server_add_conn_detail(s, "projectId", project_id);
    dittoe_server_add_conn_detail(s, "dataset", dataset);
    dittoe_server_add_conn_detail(s, "credentialsFile", creds_file);
}

void dittoe_server_salesforce(DittoeServer* s, const char* name,
    const char* login_url, const char* username,
    const char* password, const char* token) {
    dittoe_server_init(s, name, ST_API);
    dittoe_server_add_conn_detail(s, "apiType", dittoe_api_type_str(AT_SALESFORCE));
    dittoe_server_add_conn_detail(s, "loginUrl", login_url);
    dittoe_server_add_conn_detail(s, "username", username);
    dittoe_server_add_conn_detail(s, "password", password);
    dittoe_server_add_conn_detail(s, "securityToken", token);
}

/* ========================================================================
 * Resource functions
 * ======================================================================== */

void dittoe_resource_init(DittoeResource* r, const char* name, DittoeServer* server) {
    memset(r, 0, sizeof(*r));
    r->resource_id = 1;
    strncpy(r->resource_name, name, DITTOE_MAX_STR - 1);
    r->server = *server;
}

void dittoe_resource_add_field(DittoeResource* r, const char* name,
    DittoeDataType dt, bool nullable) {
    if (r->field_count < DITTOE_MAX_FIELDS) {
        dittoe_field_init(&r->fields[r->field_count], name, dt, nullable);
        r->field_count++;
    }
}

void dittoe_resource_set_strategy(DittoeResource* r, const char* strategy) {
    strncpy(r->strategy, strategy, DITTOE_MAX_STR - 1);
}

void dittoe_resource_set_schema(DittoeResource* r, const char* schema) {
    dittoe_resource_add_property(r, "schema", schema);
}

void dittoe_resource_add_property(DittoeResource* r, const char* key, const char* value) {
    if (r->res_prop_count < DITTOE_MAX_PROPS) {
        strncpy(r->resource_properties[r->res_prop_count].key, key, DITTOE_MAX_STR - 1);
        strncpy(r->resource_properties[r->res_prop_count].value, value, DITTOE_MAX_STR - 1);
        r->res_prop_count++;
    }
}

/* ========================================================================
 * Behavior functions
 * ======================================================================== */

void dittoe_behavior_init(DittoeBehavior* b, const char* name, DittoeBehaviorType type) {
    memset(b, 0, sizeof(*b));
    strncpy(b->name, name, DITTOE_MAX_STR - 1);
    b->behavior_type = type;
}

void dittoe_behavior_set_description(DittoeBehavior* b, const char* desc) {
    strncpy(b->description, desc, DITTOE_MAX_STR - 1);
}

void dittoe_behavior_add_property(DittoeBehavior* b, const char* key, const char* value) {
    if (b->prop_count < DITTOE_MAX_PROPS) {
        strncpy(b->properties[b->prop_count].key, key, DITTOE_MAX_STR - 1);
        strncpy(b->properties[b->prop_count].value, value, DITTOE_MAX_STR - 1);
        b->prop_count++;
    }
}

void dittoe_behavior_set_error_threshold(DittoeBehavior* b, int threshold) {
    char val[16]; snprintf(val, sizeof(val), "%d", threshold);
    dittoe_behavior_add_property(b, "errorCountThreshold", val);
}

void dittoe_behavior_set_staged(DittoeBehavior* b, DittoeStageType stage, int order) {
    b->is_staged = true;
    b->stage_type = stage;
    b->order = order;
}

/* ========================================================================
 * Pipeline functions
 * ======================================================================== */

void dittoe_pipeline_init(DittoePipeline* p, const char* short_name, DittoeTemplateType tt) {
    memset(p, 0, sizeof(*p));
    strncpy(p->short_name, short_name, DITTOE_MAX_STR - 1);
    p->template_type = tt;
    /* Generate a simple ID */
    snprintf(p->id, DITTOE_MAX_STR, "pipeline-%s", short_name);
}

void dittoe_pipeline_set_source(DittoePipeline* p, DittoeResource* source) {
    p->source_resource = *source;
}

void dittoe_pipeline_set_target(DittoePipeline* p, DittoeResource* target) {
    p->target_resource = *target;
}

void dittoe_pipeline_add_mapping(DittoePipeline* p, DittoeField* src, DittoeField* tgt) {
    if (p->mapping_count < DITTOE_MAX_MAPPINGS) {
        p->field_mappings[p->mapping_count].source_field = *src;
        p->field_mappings[p->mapping_count].target_field = *tgt;
        p->mapping_count++;
    }
}

void dittoe_pipeline_map_same_name(DittoePipeline* p) {
    for (int i = 0; i < p->source_resource.field_count; i++) {
        for (int j = 0; j < p->target_resource.field_count; j++) {
            if (strcmp(p->source_resource.fields[i].name,
                       p->target_resource.fields[j].name) == 0) {
                dittoe_pipeline_add_mapping(p,
                    &p->source_resource.fields[i],
                    &p->target_resource.fields[j]);
                break;
            }
        }
    }
}

void dittoe_pipeline_add_behavior(DittoePipeline* p, DittoeBehavior* b) {
    if (p->behavior_count < DITTOE_MAX_BEHAVIORS) {
        p->behaviors[p->behavior_count] = *b;
        p->behavior_count++;
    }
}

void dittoe_pipeline_add_default_behaviors(DittoePipeline* p) {
    DittoeBehavior b;
    dittoe_behavior_init(&b, "SourceRetrieval", BT_SOURCE_RETRIEVAL);
    dittoe_behavior_set_error_threshold(&b, 1);
    dittoe_pipeline_add_behavior(p, &b);

    dittoe_behavior_init(&b, "TargetInsertUpdate", BT_TARGET_INSERT_UPDATE);
    dittoe_behavior_set_error_threshold(&b, 1);
    dittoe_pipeline_add_behavior(p, &b);

    dittoe_behavior_init(&b, "Instrumentation", BT_INSTRUMENTATION);
    dittoe_pipeline_add_behavior(p, &b);

    dittoe_behavior_init(&b, "Transformation", BT_TRANSFORMATION);
    dittoe_pipeline_add_behavior(p, &b);
}

void dittoe_pipeline_add_global_prop(DittoePipeline* p, const char* key, const char* value) {
    if (p->global_prop_count < DITTOE_MAX_PROPS) {
        strncpy(p->global_properties[p->global_prop_count].key, key, DITTOE_MAX_STR - 1);
        strncpy(p->global_properties[p->global_prop_count].value, value, DITTOE_MAX_STR - 1);
        p->global_prop_count++;
    }
}

/* ========================================================================
 * JSON Serialization
 * ======================================================================== */

int dittoe_field_to_json(const DittoeField* f, char* buf, int buf_size) {
    char ename[DITTOE_MAX_STR * 2], edesc[DITTOE_MAX_STR * 2];
    json_escape(f->name, ename, sizeof(ename));
    json_escape(f->description, edesc, sizeof(edesc));

    int pos = 0;
    pos = json_append(buf, pos, buf_size,
        "{\"name\":\"%s\",\"dataType\":\"%s\",\"isNullable\":%s",
        ename, dittoe_data_type_str(f->data_type),
        f->is_nullable ? "true" : "false");

    if (f->description[0]) {
        pos = json_append(buf, pos, buf_size, ",\"description\":\"%s\"", edesc);
    }
    if (f->has_expression) {
        char eexpr[DITTOE_MAX_STR * 2];
        json_escape(f->expression, eexpr, sizeof(eexpr));
        pos = json_append(buf, pos, buf_size, ",\"expression\":\"%s\"", eexpr);
    }
    pos = json_append(buf, pos, buf_size, "}");
    return pos;
}

int dittoe_server_to_json(const DittoeServer* s, char* buf, int buf_size) {
    char ename[DITTOE_MAX_STR * 2];
    json_escape(s->name, ename, sizeof(ename));
    int pos = 0;
    pos = json_append(buf, pos, buf_size,
        "{\"id\":%d,\"name\":\"%s\",\"type\":\"%s\",\"connectionDetails\":",
        s->id, ename, dittoe_server_type_str(s->type));
    pos = json_write_props(buf, pos, buf_size, s->connection_details, s->conn_detail_count);
    pos = json_append(buf, pos, buf_size, ",\"additionalProperties\":");
    pos = json_write_props(buf, pos, buf_size, s->additional_properties, s->add_prop_count);
    pos = json_append(buf, pos, buf_size, "}");
    return pos;
}

int dittoe_resource_to_json(const DittoeResource* r, char* buf, int buf_size) {
    char ename[DITTOE_MAX_STR * 2], estrategy[DITTOE_MAX_STR * 2];
    json_escape(r->resource_name, ename, sizeof(ename));
    json_escape(r->strategy, estrategy, sizeof(estrategy));

    int pos = 0;
    pos = json_append(buf, pos, buf_size,
        "{\"resourceID\":%d,\"resourceName\":\"%s\",\"server\":",
        r->resource_id, ename);

    char srv_buf[DITTOE_MAX_JSON];
    dittoe_server_to_json(&r->server, srv_buf, sizeof(srv_buf));
    pos = json_append(buf, pos, buf_size, "%s", srv_buf);

    pos = json_append(buf, pos, buf_size, ",\"strategy\":\"%s\",\"resourceProperties\":", estrategy);
    pos = json_write_props(buf, pos, buf_size, r->resource_properties, r->res_prop_count);

    pos = json_append(buf, pos, buf_size, ",\"fields\":[");
    for (int i = 0; i < r->field_count; i++) {
        char field_buf[4096];
        dittoe_field_to_json(&r->fields[i], field_buf, sizeof(field_buf));
        pos = json_append(buf, pos, buf_size, "%s%s", i > 0 ? "," : "", field_buf);
    }
    pos = json_append(buf, pos, buf_size, "]}");
    return pos;
}

int dittoe_behavior_to_json(const DittoeBehavior* b, char* buf, int buf_size) {
    char ename[DITTOE_MAX_STR * 2], edesc[DITTOE_MAX_STR * 2];
    json_escape(b->name, ename, sizeof(ename));
    json_escape(b->description, edesc, sizeof(edesc));

    int pos = 0;
    pos = json_append(buf, pos, buf_size,
        "{\"name\":\"%s\",\"behaviorType\":\"%s\",\"description\":\"%s\",\"properties\":",
        ename, dittoe_behavior_type_str(b->behavior_type), edesc);
    pos = json_write_props(buf, pos, buf_size, b->properties, b->prop_count);

    if (b->is_staged) {
        char eplugin[DITTOE_MAX_STR * 2];
        json_escape(b->plugin_reference, eplugin, sizeof(eplugin));
        pos = json_append(buf, pos, buf_size,
            ",\"pluginReference\":\"%s\",\"order\":%d,\"stageType\":\"%s\"",
            eplugin, b->order, dittoe_stage_type_str(b->stage_type));
    }
    pos = json_append(buf, pos, buf_size, "}");
    return pos;
}

int dittoe_pipeline_to_json(const DittoePipeline* p, char* buf, int buf_size) {
    char eid[DITTOE_MAX_STR*2], ename[DITTOE_MAX_STR*2], edesc[DITTOE_MAX_STR*2];
    json_escape(p->id, eid, sizeof(eid));
    json_escape(p->short_name, ename, sizeof(ename));
    json_escape(p->description, edesc, sizeof(edesc));

    int pos = 0;
    pos = json_append(buf, pos, buf_size,
        "{\"id\":\"%s\",\"shortName\":\"%s\",\"description\":\"%s\","
        "\"templateType\":\"%s\",\"globalProperties\":",
        eid, ename, edesc, dittoe_template_type_str(p->template_type));
    pos = json_write_props(buf, pos, buf_size, p->global_properties, p->global_prop_count);

    /* Behaviors */
    pos = json_append(buf, pos, buf_size, ",\"behaviors\":[");
    for (int i = 0; i < p->behavior_count; i++) {
        char beh_buf[4096];
        dittoe_behavior_to_json(&p->behaviors[i], beh_buf, sizeof(beh_buf));
        pos = json_append(buf, pos, buf_size, "%s%s", i > 0 ? "," : "", beh_buf);
    }
    pos = json_append(buf, pos, buf_size, "]");

    /* Source resource */
    pos = json_append(buf, pos, buf_size, ",\"sourceResourceToProcess\":");
    char res_buf[DITTOE_MAX_JSON];
    dittoe_resource_to_json(&p->source_resource, res_buf, sizeof(res_buf));
    pos = json_append(buf, pos, buf_size, "%s", res_buf);

    /* Target resource */
    pos = json_append(buf, pos, buf_size, ",\"targetResourceToProcess\":");
    dittoe_resource_to_json(&p->target_resource, res_buf, sizeof(res_buf));
    pos = json_append(buf, pos, buf_size, "%s", res_buf);

    /* S2TMap */
    pos = json_append(buf, pos, buf_size, ",\"S2TMap\":[");
    for (int i = 0; i < p->mapping_count; i++) {
        char sf_buf[4096], tf_buf[4096];
        dittoe_field_to_json(&p->field_mappings[i].source_field, sf_buf, sizeof(sf_buf));
        dittoe_field_to_json(&p->field_mappings[i].target_field, tf_buf, sizeof(tf_buf));
        pos = json_append(buf, pos, buf_size,
            "%s{\"sourceField\":%s,\"targetField\":%s}",
            i > 0 ? "," : "", sf_buf, tf_buf);
    }
    pos = json_append(buf, pos, buf_size, "]");

    /* Audit fields */
    if (p->create_date[0]) {
        char e[128]; json_escape(p->create_date, e, sizeof(e));
        pos = json_append(buf, pos, buf_size, ",\"createDate\":\"%s\"", e);
    }
    if (p->created_by[0]) {
        char e[DITTOE_MAX_STR*2]; json_escape(p->created_by, e, sizeof(e));
        pos = json_append(buf, pos, buf_size, ",\"createdBy\":\"%s\"", e);
    }
    if (p->last_updated_by[0]) {
        char e[DITTOE_MAX_STR*2]; json_escape(p->last_updated_by, e, sizeof(e));
        pos = json_append(buf, pos, buf_size, ",\"lastUpdatedBy\":\"%s\"", e);
    }
    if (p->last_updated_date[0]) {
        char e[128]; json_escape(p->last_updated_date, e, sizeof(e));
        pos = json_append(buf, pos, buf_size, ",\"lastUpdatedDate\":\"%s\"", e);
    }

    pos = json_append(buf, pos, buf_size, "}");
    return pos;
}

/* ========================================================================
 * File I/O
 * ======================================================================== */

static int dittoe_mkdir(const char* path) {
#ifdef _WIN32
    return mkdir(path);
#else
    return mkdir(path, 0755);
#endif
}

static int mkdirs(const char* path) {
    char tmp[DITTOE_MAX_STR];
    strncpy(tmp, path, sizeof(tmp) - 1);
    for (char* p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            dittoe_mkdir(tmp);
            *p = '/';
        }
    }
    return 0;
}

int dittoe_pipeline_save(const DittoePipeline* p, const char* path) {
    /* Create parent directories */
    char dir[DITTOE_MAX_STR];
    strncpy(dir, path, sizeof(dir) - 1);
    char* last_slash = strrchr(dir, '/');
    if (last_slash) {
        *last_slash = '\0';
        mkdirs(dir);
        dittoe_mkdir(dir);
    }

    char* buf = (char*)malloc(DITTOE_MAX_JSON);
    if (!buf) return -1;

    int len = dittoe_pipeline_to_json(p, buf, DITTOE_MAX_JSON);
    FILE* f = fopen(path, "w");
    if (!f) { free(buf); return -1; }
    fwrite(buf, 1, len, f);
    fclose(f);
    free(buf);
    return 0;
}

/* ========================================================================
 * Executor
 * ======================================================================== */

bool dittoe_is_valid_pipeline_name(const char* name) {
    if (!name || !name[0]) return false;
    if (!isalnum((unsigned char)name[0])) return false;

    for (int i = 0; name[i]; i++) {
        unsigned char c = (unsigned char)name[i];
        if (i >= DITTOE_MAX_STR - 1) return false;
        if (isalnum(c) || c == '_' || c == '-' || c == '.') continue;
        return false;
    }
    return true;
}

bool dittoe_is_safe_executor_path(const char* path) {
    if (!path || !path[0]) return false;

    for (int i = 0; path[i]; i++) {
        unsigned char c = (unsigned char)path[i];
        if (i >= DITTOE_MAX_STR - 1) return false;
        if (iscntrl(c) || c == '\'') return false;
    }
    return true;
}

static int shell_quote(const char* input, char* output, int output_size) {
    int pos = 0;
    if (!input || !output || output_size < 3) return -1;
    if (pos >= output_size - 1) return -1;
    output[pos++] = '\'';
    for (int i = 0; input[i]; i++) {
        if (input[i] == '\'' || iscntrl((unsigned char)input[i])) return -1;
        if (pos >= output_size - 2) return -1;
        output[pos++] = input[i];
    }
    if (pos >= output_size - 1) return -1;
    output[pos++] = '\'';
    output[pos] = '\0';
    return pos;
}

DittoeExecResult dittoe_execute(const char* jar_path, DittoePipeline* pipeline,
    const char* base_dir, int timeout_sec) {
    DittoeExecResult result = {0};
    (void)timeout_sec;

    if (!pipeline || !dittoe_is_valid_pipeline_name(pipeline->short_name)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf),
            "Invalid pipeline name. Use letters, digits, underscore, hyphen, or dot, starting with a letter or digit.");
        return result;
    }
    if (!dittoe_is_safe_executor_path(jar_path) || !dittoe_is_safe_executor_path(base_dir)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf),
            "Invalid executor path. Paths must be non-empty and must not contain control characters or single quotes.");
        return result;
    }

    /* Save PMD */
    char pmd_dir[DITTOE_MAX_STR * 2];
    int path_len = snprintf(pmd_dir, sizeof(pmd_dir), "%s/pipelines/%s", base_dir, pipeline->short_name);
    if (path_len < 0 || path_len >= (int)sizeof(pmd_dir)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "PMD directory path is too long");
        return result;
    }
    mkdirs(pmd_dir);
    dittoe_mkdir(pmd_dir);

    char pmd_path[DITTOE_MAX_STR * 2];
    path_len = snprintf(pmd_path, sizeof(pmd_path), "%s/pmd.json", pmd_dir);
    if (path_len < 0 || path_len >= (int)sizeof(pmd_path)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "PMD file path is too long");
        return result;
    }
    if (dittoe_pipeline_save(pipeline, pmd_path) != 0) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "Failed to save PMD");
        return result;
    }

    /* Build command: use BASE_DIR env var and -p flag. Values are single-quoted
     * after validation to avoid shell interpretation of SDK-controlled input.
     */
    char pipelines_dir[DITTOE_MAX_STR * 2];
    path_len = snprintf(pipelines_dir, sizeof(pipelines_dir), "%s/pipelines", base_dir);
    if (path_len < 0 || path_len >= (int)sizeof(pipelines_dir)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "Pipelines directory path is too long");
        return result;
    }
    char quoted_pipelines_dir[DITTOE_MAX_STR * 5];
    char quoted_jar_path[DITTOE_MAX_STR * 5];
    char quoted_pipeline_name[DITTOE_MAX_STR * 5];
    if (shell_quote(pipelines_dir, quoted_pipelines_dir, sizeof(quoted_pipelines_dir)) < 0 ||
        shell_quote(jar_path, quoted_jar_path, sizeof(quoted_jar_path)) < 0 ||
        shell_quote(pipeline->short_name, quoted_pipeline_name, sizeof(quoted_pipeline_name)) < 0) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "Failed to quote executor command arguments");
        return result;
    }

    char cmd[DITTOE_MAX_JSON];
    int cmd_len = snprintf(cmd, sizeof(cmd),
        "BASE_DIR=%s java -jar %s -p %s 2>&1",
        quoted_pipelines_dir, quoted_jar_path, quoted_pipeline_name);
    if (cmd_len < 0 || cmd_len >= (int)sizeof(cmd)) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "Executor command is too long");
        return result;
    }

    /* Execute */
    FILE* fp = popen(cmd, "r");
    if (!fp) {
        result.success = false;
        snprintf(result.stderr_buf, sizeof(result.stderr_buf), "Failed to execute: %s", strerror(errno));
        return result;
    }

    int pos = 0;
    while (fgets(result.stdout_buf + pos, sizeof(result.stdout_buf) - pos, fp) && pos < (int)sizeof(result.stdout_buf) - 1) {
        pos = (int)strlen(result.stdout_buf);
    }

    result.return_code = pclose(fp);
    result.success = (result.return_code == 0);
    return result;
}

/* ========================================================================
 * Validation
 * ======================================================================== */

int dittoe_pipeline_validate(const DittoePipeline* p, char* errors, int buf_size) {
    int pos = 0;
    if (!p->short_name[0])
        pos = json_append(errors, pos, buf_size, "short_name is required\n");
    if (!p->source_resource.resource_name[0])
        pos = json_append(errors, pos, buf_size, "source resource is required\n");
    if (!p->target_resource.resource_name[0])
        pos = json_append(errors, pos, buf_size, "target resource is required\n");
    if (!p->source_resource.strategy[0])
        pos = json_append(errors, pos, buf_size, "source strategy is required\n");
    if (!p->target_resource.strategy[0])
        pos = json_append(errors, pos, buf_size, "target strategy is required\n");
    if (p->source_resource.field_count == 0)
        pos = json_append(errors, pos, buf_size, "source must have fields\n");
    if (p->target_resource.field_count == 0)
        pos = json_append(errors, pos, buf_size, "target must have fields\n");
    if (p->behavior_count == 0)
        pos = json_append(errors, pos, buf_size, "pipeline must have behaviors\n");
    if (p->mapping_count == 0)
        pos = json_append(errors, pos, buf_size, "pipeline must have field mappings\n");
    return pos;
}

/* ========================================================================
 * Config functions
 * ======================================================================== */

void dittoe_config_init(DittoeConfig* cfg) {
    memset(cfg, 0, sizeof(*cfg));
    strncpy(cfg->base_dir, "./pipelines", DITTOE_MAX_STR - 1);
    strncpy(cfg->java_path, "java", DITTOE_MAX_STR - 1);
    cfg->timeout = 300;
}

void dittoe_config_from_env(DittoeConfig* cfg) {
    dittoe_config_init(cfg);
    const char* val;

    val = getenv("DITTOE_JAR");
    if (val) strncpy(cfg->jar_path, val, DITTOE_MAX_STR - 1);

    val = getenv("DITTOE_BASE_DIR");
    if (val) strncpy(cfg->base_dir, val, DITTOE_MAX_STR - 1);

    val = getenv("DITTOE_JAVA");
    if (val) strncpy(cfg->java_path, val, DITTOE_MAX_STR - 1);

    val = getenv("DITTOE_TIMEOUT");
    if (val) {
        int t = atoi(val);
        if (t > 0) cfg->timeout = t;
    }

    val = getenv("DITTOE_JAVA_OPTS");
    if (val) {
        char tmp[DITTOE_MAX_STR * 4];
        strncpy(tmp, val, sizeof(tmp) - 1);
        tmp[sizeof(tmp) - 1] = '\0';
        char* tok = strtok(tmp, ",");
        while (tok && cfg->java_opts_count < DITTOE_MAX_PROPS) {
            /* trim leading spaces */
            while (*tok == ' ') tok++;
            strncpy(cfg->java_opts[cfg->java_opts_count], tok, DITTOE_MAX_STR - 1);
            cfg->java_opts_count++;
            tok = strtok(NULL, ",");
        }
    }
}

int dittoe_config_validate(const DittoeConfig* cfg, char* errors, int buf_size) {
    int pos = 0;
    if (!cfg->jar_path[0])
        pos = json_append(errors, pos, buf_size, "jar_path is required (set DITTOE_JAR)\n");
    else if (access(cfg->jar_path, F_OK) != 0)
        pos = json_append(errors, pos, buf_size, "JAR file not found: %s\n", cfg->jar_path);
    if (!cfg->java_path[0])
        pos = json_append(errors, pos, buf_size, "java_path is required\n");
    if (!cfg->base_dir[0])
        pos = json_append(errors, pos, buf_size, "base_dir is required\n");
    if (cfg->timeout <= 0)
        pos = json_append(errors, pos, buf_size, "timeout must be positive\n");
    return pos;
}

/* ========================================================================
 * Facade: Connection functions
 * ======================================================================== */

static void connection_init_base(DittoeConnection* c, const char* name) {
    memset(c, 0, sizeof(*c));
    strncpy(c->name, name, DITTOE_MAX_STR - 1);
}

void dittoe_connection_init_pg(DittoeConnection* c, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    connection_init_base(c, name);
    dittoe_server_postgresql(&c->server, name, host, port, database, user, password);
}

void dittoe_connection_init_mysql(DittoeConnection* c, const char* name,
    const char* host, int port, const char* database,
    const char* user, const char* password) {
    connection_init_base(c, name);
    dittoe_server_mysql(&c->server, name, host, port, database, user, password);
}

void dittoe_connection_init_local_file(DittoeConnection* c, const char* name,
    const char* path) {
    connection_init_base(c, name);
    dittoe_server_local_file(&c->server, name, path);
}

void dittoe_connection_init_s3(DittoeConnection* c, const char* name,
    const char* bucket, const char* region,
    const char* access_key, const char* secret_key) {
    connection_init_base(c, name);
    dittoe_server_s3(&c->server, name, bucket, region, access_key, secret_key);
}

void dittoe_connection_add_field(DittoeConnection* c, const char* name,
    DittoeDataType dt, bool nullable) {
    if (c->field_count < DITTOE_MAX_FIELDS) {
        dittoe_field_init(&c->fields[c->field_count], name, dt, nullable);
        c->field_count++;
    }
}

void dittoe_connection_set_schema(DittoeConnection* c, const char* schema) {
    strncpy(c->schema_name, schema, DITTOE_MAX_STR - 1);
}

const char* dittoe_default_read_strategy(DittoeServerType type, const char* subtype) {
    if (type == ST_DATABASE) return STRATEGY_JDBC_SELECT;
    if (type == ST_FILE) {
        if (subtype && strcmp(subtype, "S3Server") == 0) return STRATEGY_S3_CSV_READ;
        if (subtype && strcmp(subtype, "GCSServer") == 0) return STRATEGY_GCS_CSV_READ;
        return STRATEGY_LOCAL_CSV_READ;
    }
    if (type == ST_API) {
        if (subtype && strcmp(subtype, "SalesforceServer") == 0) return STRATEGY_SF_READ;
        return STRATEGY_BQ_QUERY;
    }
    return "";
}

const char* dittoe_default_write_strategy(DittoeServerType type, const char* subtype) {
    if (type == ST_DATABASE) return STRATEGY_JDBC_INSERT;
    if (type == ST_FILE) {
        if (subtype && strcmp(subtype, "S3Server") == 0) return STRATEGY_S3_CSV_WRITE;
        if (subtype && strcmp(subtype, "GCSServer") == 0) return STRATEGY_GCS_CSV_WRITE;
        return STRATEGY_LOCAL_CSV_WRITE;
    }
    if (type == ST_API) {
        if (subtype && strcmp(subtype, "SalesforceServer") == 0) return STRATEGY_SF_WRITE;
        return STRATEGY_BQ_WRITE;
    }
    return "";
}

/* Determine the "subtype" string from connection details */
static const char* get_subtype(const DittoeConnection* c) {
    for (int i = 0; i < c->server.conn_detail_count; i++) {
        if (strcmp(c->server.connection_details[i].key, "databaseType") == 0 ||
            strcmp(c->server.connection_details[i].key, "fileType") == 0 ||
            strcmp(c->server.connection_details[i].key, "apiType") == 0)
            return c->server.connection_details[i].value;
    }
    return NULL;
}

void dittoe_connection_set_as_source(DittoeConnection* c) {
    if (!c->strategy[0]) {
        const char* strat = dittoe_default_read_strategy(c->server.type, get_subtype(c));
        strncpy(c->strategy, strat, DITTOE_MAX_STR - 1);
    }
}

void dittoe_connection_set_as_target(DittoeConnection* c) {
    if (!c->strategy[0]) {
        const char* strat = dittoe_default_write_strategy(c->server.type, get_subtype(c));
        strncpy(c->strategy, strat, DITTOE_MAX_STR - 1);
    }
}

int dittoe_connection_validate(const DittoeConnection* c, char* errors, int buf_size) {
    int pos = 0;
    if (!c->name[0])
        pos = json_append(errors, pos, buf_size, "connection name is required\n");
    if (!c->server.name[0])
        pos = json_append(errors, pos, buf_size, "connection '%s': server is required\n", c->name);
    if (c->field_count == 0)
        pos = json_append(errors, pos, buf_size, "connection '%s': at least one field is required\n", c->name);
    if (!c->strategy[0])
        pos = json_append(errors, pos, buf_size, "connection '%s': strategy is required\n", c->name);
    return pos;
}

DittoeExecResult dittoe_migrate(const DittoeConfig* cfg,
    DittoeConnection* source, DittoeConnection* target,
    const char* pipeline_name) {

    /* Auto-set strategies if not already set */
    dittoe_connection_set_as_source(source);
    dittoe_connection_set_as_target(target);

    /* Build resources */
    static DittoeResource src_res, tgt_res;
    dittoe_resource_init(&src_res, source->name, &source->server);
    src_res.field_count = source->field_count;
    for (int i = 0; i < source->field_count; i++) src_res.fields[i] = source->fields[i];
    dittoe_resource_set_strategy(&src_res, source->strategy);
    if (source->schema_name[0]) dittoe_resource_set_schema(&src_res, source->schema_name);

    dittoe_resource_init(&tgt_res, target->name, &target->server);
    tgt_res.field_count = target->field_count;
    for (int i = 0; i < target->field_count; i++) tgt_res.fields[i] = target->fields[i];
    dittoe_resource_set_strategy(&tgt_res, target->strategy);
    if (target->schema_name[0]) dittoe_resource_set_schema(&tgt_res, target->schema_name);

    /* Build pipeline */
    static DittoePipeline p;
    char name[DITTOE_MAX_STR];
    if (pipeline_name && pipeline_name[0]) {
        strncpy(name, pipeline_name, DITTOE_MAX_STR - 1);
        name[DITTOE_MAX_STR - 1] = '\0';
    } else {
        name[0] = '\0';
        strncat(name, source->name, DITTOE_MAX_STR - 1);
        strncat(name, "-to-", DITTOE_MAX_STR - strlen(name) - 1);
        strncat(name, target->name, DITTOE_MAX_STR - strlen(name) - 1);
    }
    dittoe_pipeline_init(&p, name, TT_DB_TO_DB);
    p.description[0] = '\0';
    strncat(p.description, "Migration: ", DITTOE_MAX_STR - 1);
    strncat(p.description, source->name, DITTOE_MAX_STR - strlen(p.description) - 1);
    strncat(p.description, " -> ", DITTOE_MAX_STR - strlen(p.description) - 1);
    strncat(p.description, target->name, DITTOE_MAX_STR - strlen(p.description) - 1);
    dittoe_pipeline_set_source(&p, &src_res);
    dittoe_pipeline_set_target(&p, &tgt_res);
    dittoe_pipeline_map_same_name(&p);
    dittoe_pipeline_add_default_behaviors(&p);

    return dittoe_execute(cfg->jar_path, &p, cfg->base_dir, cfg->timeout);
}
