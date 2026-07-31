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
 * DITTOE C SDK Test Suite
 * Tests enum coverage, model construction, JSON serialization,
 * strategy constants, and pipeline building.
 */

#define _POSIX_C_SOURCE 200809L
#include "../include/dittoe.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>

static int tests_passed = 0;
static int tests_total = 0;

#define TEST(name) do { \
    tests_total++; \
    printf("  [%d] %s... ", tests_total, #name); \
    test_##name(); \
    tests_passed++; \
    printf("PASS\n"); \
} while(0)

/* ========================================================================
 * Enum completeness
 * ======================================================================== */

void test_data_type_count(void) {
    assert(DT_COUNT == 17);
    assert(strcmp(dittoe_data_type_str(DT_STRING), "String") == 0);
    assert(strcmp(dittoe_data_type_str(DT_XML), "XML") == 0);
    assert(strcmp(dittoe_data_type_str(DT_JSONB), "JSONB") == 0);
}

void test_behavior_type_count(void) {
    assert(BT_COUNT == 22);
    assert(strcmp(dittoe_behavior_type_str(BT_FORMATTING), "Formatting") == 0);
    assert(strcmp(dittoe_behavior_type_str(BT_CACHE), "Cache") == 0);
    assert(strcmp(dittoe_behavior_type_str(BT_LINEAGE_TRACKING), "lineageTracking") == 0);
}

void test_server_type_count(void) {
    assert(ST_COUNT == 4);
    assert(strcmp(dittoe_server_type_str(ST_DATABASE), "Database") == 0);
    assert(strcmp(dittoe_server_type_str(ST_FILE), "File") == 0);
    assert(strcmp(dittoe_server_type_str(ST_API), "API") == 0);
    assert(strcmp(dittoe_server_type_str(ST_STREAM), "Stream") == 0);
}

void test_template_type_count(void) {
    assert(TT_COUNT == 16);
    assert(strcmp(dittoe_template_type_str(TT_DB_TO_DB), "DatabaseToDatabase") == 0);
    assert(strcmp(dittoe_template_type_str(TT_FILE_TO_FILE), "FileToFile") == 0);
    assert(strcmp(dittoe_template_type_str(TT_API_TO_STREAM), "APIToStream") == 0);
}

void test_stage_type_count(void) {
    assert(STAGE_COUNT == 6);
    assert(strcmp(dittoe_stage_type_str(STAGE_BEGIN), "Begin") == 0);
    assert(strcmp(dittoe_stage_type_str(STAGE_END), "End") == 0);
}

void test_database_type_count(void) {
    assert(DBT_COUNT == 5);
    assert(strcmp(dittoe_database_type_str(DBT_POSTGRESQL), "PostgreSQLServer") == 0);
    assert(strcmp(dittoe_database_type_str(DBT_SNOWFLAKE), "SnowflakeServer") == 0);
}

void test_file_type_count(void) {
    assert(FT_COUNT == 11);
    assert(strcmp(dittoe_file_type_str(FT_LOCAL), "LocalFileServer") == 0);
    assert(strcmp(dittoe_file_type_str(FT_GOOGLE_DRIVE), "GoogleDriveServer") == 0);
}

void test_api_type_count(void) {
    assert(AT_COUNT == 2);
    assert(strcmp(dittoe_api_type_str(AT_BIGQUERY), "BigQueryServer") == 0);
    assert(strcmp(dittoe_api_type_str(AT_SALESFORCE), "SalesforceServer") == 0);
}

/* ========================================================================
 * Field tests
 * ======================================================================== */

void test_field_init(void) {
    DittoeField f;
    dittoe_field_init(&f, "name", DT_STRING, true);
    assert(strcmp(f.name, "name") == 0);
    assert(f.data_type == DT_STRING);
    assert(f.is_nullable == true);
}

void test_field_expression(void) {
    DittoeField f;
    dittoe_field_init(&f, "calc", DT_INTEGER, false);
    dittoe_field_set_expression(&f, "a + b");
    assert(f.has_expression == true);
    assert(strcmp(f.expression, "a + b") == 0);
}

void test_field_json(void) {
    DittoeField f;
    dittoe_field_init(&f, "id", DT_INTEGER, false);
    char buf[2048];
    dittoe_field_to_json(&f, buf, sizeof(buf));
    assert(strstr(buf, "\"name\":\"id\"") != NULL);
    assert(strstr(buf, "\"dataType\":\"Integer\"") != NULL);
    assert(strstr(buf, "\"isNullable\":false") != NULL);
}

void test_field_expression_json(void) {
    DittoeField f;
    dittoe_field_init(&f, "calc", DT_STRING, true);
    dittoe_field_set_expression(&f, "UPPER(x)");
    char buf[2048];
    dittoe_field_to_json(&f, buf, sizeof(buf));
    assert(strstr(buf, "\"expression\":\"UPPER(x)\"") != NULL);
}

/* ========================================================================
 * Server tests
 * ======================================================================== */

void test_postgresql_server(void) {
    DittoeServer s;
    dittoe_server_postgresql(&s, "pg", "localhost", 5432, "mydb", "user", "pass");
    assert(s.type == ST_DATABASE);
    assert(s.conn_detail_count == 6);
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"type\":\"Database\"") != NULL);
    assert(strstr(buf, "\"databaseType\":\"PostgreSQLServer\"") != NULL);
    assert(strstr(buf, "\"host\":\"localhost\"") != NULL);
}

void test_mysql_server(void) {
    DittoeServer s;
    dittoe_server_mysql(&s, "my", "localhost", 3306, "db", "u", "p");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"databaseType\":\"MySQLServer\"") != NULL);
}

void test_mssql_server(void) {
    DittoeServer s;
    dittoe_server_mssql(&s, "ms", "localhost", 1433, "db", "sa", "p");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"databaseType\":\"MSSQLServer\"") != NULL);
}

void test_sqlite_server(void) {
    DittoeServer s;
    dittoe_server_sqlite(&s, "lite", "/tmp/test.db");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"databaseType\":\"SQLiteServer\"") != NULL);
    assert(strstr(buf, "\"path\":\"/tmp/test.db\"") != NULL);
}

void test_snowflake_server(void) {
    DittoeServer s;
    dittoe_server_snowflake(&s, "snow", "acct.sf.com", 443, "db", "u", "p");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"databaseType\":\"SnowflakeServer\"") != NULL);
}

void test_local_file_server(void) {
    DittoeServer s;
    dittoe_server_local_file(&s, "fs", "/data");
    assert(s.type == ST_FILE);
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"type\":\"File\"") != NULL);
    assert(strstr(buf, "\"fileType\":\"LocalFileServer\"") != NULL);
}

void test_s3_server(void) {
    DittoeServer s;
    dittoe_server_s3(&s, "s3", "bucket", "us-east-1", "AK", "SK");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"fileType\":\"S3Server\"") != NULL);
    assert(strstr(buf, "\"bucket\":\"bucket\"") != NULL);
}

void test_sftp_server(void) {
    DittoeServer s;
    dittoe_server_sftp(&s, "sftp", "host", 22, "/data", "u", "p");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"fileType\":\"SFTPServer\"") != NULL);
}

void test_bigquery_server(void) {
    DittoeServer s;
    dittoe_server_bigquery(&s, "bq", "proj", "dataset", "/creds");
    assert(s.type == ST_API);
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"type\":\"API\"") != NULL);
    assert(strstr(buf, "\"apiType\":\"BigQueryServer\"") != NULL);
}

void test_salesforce_server(void) {
    DittoeServer s;
    dittoe_server_salesforce(&s, "sf", "https://login.sf.com", "u", "p", "token");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"apiType\":\"SalesforceServer\"") != NULL);
}

void test_server_additional_props(void) {
    DittoeServer s;
    dittoe_server_postgresql(&s, "pg", "h", 5432, "db", "u", "p");
    dittoe_server_add_property(&s, "timeout", "30");
    char buf[8192];
    dittoe_server_to_json(&s, buf, sizeof(buf));
    assert(strstr(buf, "\"timeout\":\"30\"") != NULL);
}

/* ========================================================================
 * Strategy constant tests
 * ======================================================================== */

void test_strategy_constants(void) {
    /* Verify strategies are FQCNs */
    assert(strstr(STRATEGY_JDBC_SELECT, "SelectStrategy") != NULL);
    assert(strstr(STRATEGY_JDBC_INSERT, "InsertStrategy") != NULL);
    assert(strstr(STRATEGY_PG_UPSERT, "UpsertOnConflictStrategy") != NULL);
    assert(strstr(STRATEGY_PG_COPY, "CopyStrategy") != NULL);
    assert(strstr(STRATEGY_MSSQL_MERGE, "MergeStrategy") != NULL);
    assert(strstr(STRATEGY_SQLITE_UPSERT, "sqlite") != NULL);
    assert(strstr(STRATEGY_LOCAL_CSV_READ, "CSVReadStrategy") != NULL);
    assert(strstr(STRATEGY_LOCAL_JSON_READ, "JSONReadStrategy") != NULL);
    assert(strstr(STRATEGY_S3_CSV_READ, "s3") != NULL);
    assert(strstr(STRATEGY_LOCAL_CSV_WRITE, "CSVWriteStrategy") != NULL);
    assert(strstr(STRATEGY_LOCAL_JSON_WRITE, "JSONWriteStrategy") != NULL);
    assert(strstr(STRATEGY_BQ_QUERY, "QueryStrategy") != NULL);
    assert(strstr(STRATEGY_BQ_AVRO, "AvroStrategy") != NULL);
    assert(strstr(STRATEGY_BQ_WRITE, "DataStoreStrategy") != NULL);
    assert(strstr(STRATEGY_SF_READ, "salesforce.readstrategy.RestAPI") != NULL);
    assert(strstr(STRATEGY_SF_WRITE, "salesforce.writestrategy.RestAPI") != NULL);
}

void test_all_strategies_start_with_prefix(void) {
    const char* all[] = {
        STRATEGY_JDBC_SELECT, STRATEGY_JDBC_INSERT, STRATEGY_JDBC_REPLACE,
        STRATEGY_PG_UPSERT, STRATEGY_PG_INSERT_DO_NOTHING, STRATEGY_PG_COPY,
        STRATEGY_PG_COPY_OR_AUTO_ID, STRATEGY_PG_INSERT_OR_UPDATE,
        STRATEGY_MSSQL_MERGE, STRATEGY_SQLITE_UPSERT,
        STRATEGY_LOCAL_CSV_READ, STRATEGY_LOCAL_JSON_READ,
        STRATEGY_S3_CSV_READ, STRATEGY_GCS_CSV_READ, STRATEGY_AZURE_CSV_READ,
        STRATEGY_DROPBOX_CSV_READ, STRATEGY_SFTP_CSV_READ, STRATEGY_FTP_CSV_READ,
        STRATEGY_IBM_CSV_READ, STRATEGY_ORACLE_CSV_READ,
        STRATEGY_DO_CSV_READ, STRATEGY_GDRIVE_CSV_READ,
        STRATEGY_LOCAL_CSV_WRITE, STRATEGY_LOCAL_JSON_WRITE,
        STRATEGY_S3_CSV_WRITE, STRATEGY_GCS_CSV_WRITE, STRATEGY_AZURE_CSV_WRITE,
        STRATEGY_DROPBOX_CSV_WRITE, STRATEGY_SFTP_CSV_WRITE, STRATEGY_FTP_CSV_WRITE,
        STRATEGY_IBM_CSV_WRITE, STRATEGY_ORACLE_CSV_WRITE,
        STRATEGY_DO_CSV_WRITE, STRATEGY_GDRIVE_CSV_WRITE,
        STRATEGY_BQ_QUERY, STRATEGY_BQ_AVRO, STRATEGY_BQ_WRITE,
        STRATEGY_SF_READ, STRATEGY_SF_WRITE,
    };
    int count = sizeof(all) / sizeof(all[0]);
    assert(count >= 39);  /* At least 39 strategy constants */
    for (int i = 0; i < count; i++) {
        assert(strncmp(all[i], "com.everyarrow.dittoe.io.", 24) == 0);
    }
}

/* ========================================================================
 * Resource tests
 * ======================================================================== */

void test_resource_build(void) {
    DittoeServer s;
    dittoe_server_postgresql(&s, "pg", "h", 5432, "db", "u", "p");
    DittoeResource r;
    dittoe_resource_init(&r, "products", &s);
    dittoe_resource_add_field(&r, "id", DT_INTEGER, false);
    dittoe_resource_add_field(&r, "name", DT_STRING, true);
    dittoe_resource_set_strategy(&r, STRATEGY_JDBC_SELECT);
    dittoe_resource_set_schema(&r, "production");
    assert(r.field_count == 2);
    assert(strcmp(r.resource_name, "products") == 0);
}

void test_resource_json(void) {
    DittoeServer s;
    dittoe_server_postgresql(&s, "pg", "h", 5432, "db", "u", "p");
    DittoeResource r;
    dittoe_resource_init(&r, "orders", &s);
    dittoe_resource_add_field(&r, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&r, STRATEGY_JDBC_SELECT);
    char buf[DITTOE_MAX_JSON];
    dittoe_resource_to_json(&r, buf, sizeof(buf));
    assert(strstr(buf, "\"resourceName\":\"orders\"") != NULL);
    assert(strstr(buf, "\"fields\":[") != NULL);
    assert(strstr(buf, "SelectStrategy") != NULL);
}

/* ========================================================================
 * Behavior tests
 * ======================================================================== */

void test_behavior_all_types(void) {
    /* Verify all 22 behavior types can be created */
    for (int i = 0; i < BT_COUNT; i++) {
        DittoeBehavior b;
        dittoe_behavior_init(&b, "test", (DittoeBehaviorType)i);
        char buf[4096];
        dittoe_behavior_to_json(&b, buf, sizeof(buf));
        const char* expected = dittoe_behavior_type_str((DittoeBehaviorType)i);
        assert(strstr(buf, expected) != NULL);
    }
}

void test_behavior_staged(void) {
    DittoeBehavior b;
    dittoe_behavior_init(&b, "Compress", BT_COMPRESSION);
    dittoe_behavior_set_staged(&b, STAGE_PACKAGE, 1);
    char buf[4096];
    dittoe_behavior_to_json(&b, buf, sizeof(buf));
    assert(strstr(buf, "\"stageType\":\"Package\"") != NULL);
    assert(strstr(buf, "\"order\":1") != NULL);
}

void test_behavior_error_threshold(void) {
    DittoeBehavior b;
    dittoe_behavior_init(&b, "SR", BT_SOURCE_RETRIEVAL);
    dittoe_behavior_set_error_threshold(&b, 5);
    char buf[4096];
    dittoe_behavior_to_json(&b, buf, sizeof(buf));
    assert(strstr(buf, "\"errorCountThreshold\":\"5\"") != NULL);
}

/* ========================================================================
 * Pipeline tests
 * ======================================================================== */

void test_full_pipeline(void) {
    static DittoeServer src_srv, tgt_srv;
    dittoe_server_postgresql(&src_srv, "src", "localhost", 5433, "srcdb", "u", "p");
    dittoe_server_postgresql(&tgt_srv, "tgt", "localhost", 5434, "tgtdb", "u", "p");

    static DittoeResource src, tgt;
    dittoe_resource_init(&src, "product", &src_srv);
    dittoe_resource_add_field(&src, "id", DT_INTEGER, false);
    dittoe_resource_add_field(&src, "name", DT_STRING, true);
    dittoe_resource_add_field(&src, "price", DT_DECIMAL, true);
    dittoe_resource_set_strategy(&src, STRATEGY_JDBC_SELECT);
    dittoe_resource_set_schema(&src, "production");

    dittoe_resource_init(&tgt, "product", &tgt_srv);
    dittoe_resource_add_field(&tgt, "id", DT_INTEGER, false);
    dittoe_resource_add_field(&tgt, "name", DT_STRING, true);
    dittoe_resource_add_field(&tgt, "price", DT_DECIMAL, true);
    dittoe_resource_set_strategy(&tgt, STRATEGY_JDBC_INSERT);

    static DittoePipeline p;
    dittoe_pipeline_init(&p, "ProductMigration", TT_DB_TO_DB);
    strncpy(p.description, "Migrate products", DITTOE_MAX_STR - 1);
    dittoe_pipeline_set_source(&p, &src);
    dittoe_pipeline_set_target(&p, &tgt);
    dittoe_pipeline_map_same_name(&p);
    dittoe_pipeline_add_default_behaviors(&p);

    assert(p.mapping_count == 3);
    assert(p.behavior_count == 4);

    char buf[DITTOE_MAX_JSON];
    dittoe_pipeline_to_json(&p, buf, sizeof(buf));
    assert(strstr(buf, "\"shortName\":\"ProductMigration\"") != NULL);
    assert(strstr(buf, "\"templateType\":\"DatabaseToDatabase\"") != NULL);
    assert(strstr(buf, "\"S2TMap\":[") != NULL);
    assert(strstr(buf, "\"sourceResourceToProcess\":{") != NULL);
    assert(strstr(buf, "\"targetResourceToProcess\":{") != NULL);
}

void test_pipeline_save_load(void) {
    static DittoeServer s;
    dittoe_server_postgresql(&s, "pg", "h", 5432, "db", "u", "p");
    static DittoeResource src, tgt;
    dittoe_resource_init(&src, "t", &s);
    dittoe_resource_add_field(&src, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&src, STRATEGY_JDBC_SELECT);
    dittoe_resource_init(&tgt, "t", &s);
    dittoe_resource_add_field(&tgt, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&tgt, STRATEGY_JDBC_INSERT);

    static DittoePipeline p;
    dittoe_pipeline_init(&p, "SaveTest", TT_DB_TO_DB);
    dittoe_pipeline_set_source(&p, &src);
    dittoe_pipeline_set_target(&p, &tgt);
    dittoe_pipeline_map_same_name(&p);
    dittoe_pipeline_add_default_behaviors(&p);

    const char* test_path = "build/dittoe_test_save/pmd.json";
    int result = dittoe_pipeline_save(&p, test_path);
    assert(result == 0);

    /* Read back */
    FILE* f = fopen(test_path, "r");
    assert(f != NULL);
    char buf[DITTOE_MAX_JSON];
    int len = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[len] = '\0';
    assert(strstr(buf, "\"shortName\":\"SaveTest\"") != NULL);

    /* Cleanup */
    remove(test_path);
    rmdir("build/dittoe_test_save");
}

void test_pipeline_validation(void) {
    static DittoePipeline p;
    memset(&p, 0, sizeof(p));
    char errors[4096] = {0};
    int len = dittoe_pipeline_validate(&p, errors, sizeof(errors));
    assert(len > 0);
    assert(strstr(errors, "short_name is required") != NULL);
    assert(strstr(errors, "source resource is required") != NULL);
    assert(strstr(errors, "target resource is required") != NULL);
}

void test_cross_database_mysql_to_pg(void) {
    static DittoeServer my_srv, pg_srv;
    dittoe_server_mysql(&my_srv, "mysql", "h", 3306, "src", "u", "p");
    dittoe_server_postgresql(&pg_srv, "pg", "h", 5432, "tgt", "u", "p");

    static DittoeResource src, tgt;
    dittoe_resource_init(&src, "users", &my_srv);
    dittoe_resource_add_field(&src, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&src, STRATEGY_JDBC_SELECT);
    dittoe_resource_init(&tgt, "users", &pg_srv);
    dittoe_resource_add_field(&tgt, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&tgt, STRATEGY_JDBC_INSERT);

    static DittoePipeline p;
    dittoe_pipeline_init(&p, "MySQLToPG", TT_DB_TO_DB);
    dittoe_pipeline_set_source(&p, &src);
    dittoe_pipeline_set_target(&p, &tgt);
    dittoe_pipeline_map_same_name(&p);
    dittoe_pipeline_add_default_behaviors(&p);

    char buf[DITTOE_MAX_JSON];
    dittoe_pipeline_to_json(&p, buf, sizeof(buf));
    assert(strstr(buf, "\"databaseType\":\"MySQLServer\"") != NULL);
    assert(strstr(buf, "\"databaseType\":\"PostgreSQLServer\"") != NULL);
}

void test_csv_to_database(void) {
    static DittoeServer fs, pg;
    dittoe_server_local_file(&fs, "fs", "/data");
    dittoe_server_postgresql(&pg, "pg", "h", 5432, "db", "u", "p");

    static DittoeResource src, tgt;
    dittoe_resource_init(&src, "data.csv", &fs);
    dittoe_resource_add_field(&src, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&src, STRATEGY_LOCAL_CSV_READ);
    dittoe_resource_init(&tgt, "data", &pg);
    dittoe_resource_add_field(&tgt, "id", DT_INTEGER, false);
    dittoe_resource_set_strategy(&tgt, STRATEGY_JDBC_INSERT);

    static DittoePipeline p;
    dittoe_pipeline_init(&p, "CSVImport", TT_FILE_TO_DATABASE);
    dittoe_pipeline_set_source(&p, &src);
    dittoe_pipeline_set_target(&p, &tgt);
    dittoe_pipeline_map_same_name(&p);
    dittoe_pipeline_add_default_behaviors(&p);

    char buf[DITTOE_MAX_JSON];
    dittoe_pipeline_to_json(&p, buf, sizeof(buf));
    assert(strstr(buf, "\"templateType\":\"FileToDatabase\"") != NULL);
}

/* ========================================================================
 * Config tests
 * ======================================================================== */

void test_config_defaults(void) {
    DittoeConfig cfg;
    dittoe_config_init(&cfg);
    assert(strcmp(cfg.base_dir, "./pipelines") == 0);
    assert(strcmp(cfg.java_path, "java") == 0);
    assert(cfg.timeout == 300);
    assert(cfg.jar_path[0] == '\0');
    assert(cfg.java_opts_count == 0);
}

void test_config_validate_empty_jar(void) {
    DittoeConfig cfg;
    dittoe_config_init(&cfg);
    char errors[4096] = {0};
    int len = dittoe_config_validate(&cfg, errors, sizeof(errors));
    assert(len > 0);
    assert(strstr(errors, "jar_path") != NULL);
}

void test_config_validate_bad_timeout(void) {
    DittoeConfig cfg;
    dittoe_config_init(&cfg);
    strncpy(cfg.jar_path, "/nonexistent.jar", DITTOE_MAX_STR - 1);
    cfg.timeout = -1;
    char errors[4096] = {0};
    int len = dittoe_config_validate(&cfg, errors, sizeof(errors));
    assert(len > 0);
    assert(strstr(errors, "timeout") != NULL);
}

void test_executor_input_validation(void) {
    assert(dittoe_is_valid_pipeline_name("ProductMigration"));
    assert(dittoe_is_valid_pipeline_name("pipeline-1.2"));
    assert(!dittoe_is_valid_pipeline_name(""));
    assert(!dittoe_is_valid_pipeline_name(".hidden"));
    assert(!dittoe_is_valid_pipeline_name("../escape"));
    assert(!dittoe_is_valid_pipeline_name("bad name"));
    assert(!dittoe_is_valid_pipeline_name("bad;name"));

    assert(dittoe_is_safe_executor_path("/opt/dittoe/engine.jar"));
    assert(dittoe_is_safe_executor_path("C:\\Program Files\\dittoe\\engine.jar"));
    assert(!dittoe_is_safe_executor_path(""));
    assert(!dittoe_is_safe_executor_path("bad\npath"));
    assert(!dittoe_is_safe_executor_path("bad'path"));
}

/* ========================================================================
 * Facade tests
 * ======================================================================== */

void test_connection_pg(void) {
    DittoeConnection c;
    dittoe_connection_init_pg(&c, "products", "localhost", 5433, "mydb", "user", "pass");
    dittoe_connection_add_field(&c, "id", DT_INTEGER, false);
    dittoe_connection_add_field(&c, "name", DT_STRING, true);
    dittoe_connection_set_schema(&c, "production");
    dittoe_connection_set_as_source(&c);
    assert(strcmp(c.name, "products") == 0);
    assert(c.field_count == 2);
    assert(strstr(c.strategy, "SelectStrategy") != NULL);
}

void test_connection_local_file(void) {
    DittoeConnection c;
    dittoe_connection_init_local_file(&c, "data.csv", "/data/imports");
    dittoe_connection_add_field(&c, "id", DT_INTEGER, false);
    dittoe_connection_set_as_source(&c);
    assert(strstr(c.strategy, "CSVReadStrategy") != NULL);
}

void test_connection_s3_target(void) {
    DittoeConnection c;
    dittoe_connection_init_s3(&c, "output", "mybucket", "us-east-1", "ak", "sk");
    dittoe_connection_add_field(&c, "id", DT_INTEGER, false);
    dittoe_connection_set_as_target(&c);
    assert(strstr(c.strategy, "CSVWriteStrategy") != NULL);
    assert(strstr(c.strategy, "s3") != NULL);
}

void test_connection_validate(void) {
    DittoeConnection c;
    memset(&c, 0, sizeof(c));
    char errors[4096] = {0};
    int len = dittoe_connection_validate(&c, errors, sizeof(errors));
    assert(len > 0);
    assert(strstr(errors, "connection name is required") != NULL);
}

void test_default_read_strategy(void) {
    assert(strstr(dittoe_default_read_strategy(ST_DATABASE, "PostgreSQLServer"), "SelectStrategy") != NULL);
    assert(strstr(dittoe_default_read_strategy(ST_FILE, "LocalFileServer"), "CSVReadStrategy") != NULL);
    assert(strstr(dittoe_default_read_strategy(ST_API, "BigQueryServer"), "QueryStrategy") != NULL);
}

void test_default_write_strategy(void) {
    assert(strstr(dittoe_default_write_strategy(ST_DATABASE, "PostgreSQLServer"), "InsertStrategy") != NULL);
    assert(strstr(dittoe_default_write_strategy(ST_FILE, "LocalFileServer"), "CSVWriteStrategy") != NULL);
    assert(strstr(dittoe_default_write_strategy(ST_API, "BigQueryServer"), "DataStoreStrategy") != NULL);
}

void test_connection_mysql_target(void) {
    DittoeConnection c;
    dittoe_connection_init_mysql(&c, "users", "localhost", 3306, "db", "u", "p");
    dittoe_connection_add_field(&c, "id", DT_INTEGER, false);
    dittoe_connection_set_as_target(&c);
    assert(strstr(c.strategy, "InsertStrategy") != NULL);
}

/* ========================================================================
 * Main
 * ======================================================================== */

int main(void) {
    printf("\n=== DITTOE C SDK Tests ===\n\n");

    printf("Enum completeness:\n");
    TEST(data_type_count);
    TEST(behavior_type_count);
    TEST(server_type_count);
    TEST(template_type_count);
    TEST(stage_type_count);
    TEST(database_type_count);
    TEST(file_type_count);
    TEST(api_type_count);

    printf("\nField models:\n");
    TEST(field_init);
    TEST(field_expression);
    TEST(field_json);
    TEST(field_expression_json);

    printf("\nServer builders:\n");
    TEST(postgresql_server);
    TEST(mysql_server);
    TEST(mssql_server);
    TEST(sqlite_server);
    TEST(snowflake_server);
    TEST(local_file_server);
    TEST(s3_server);
    TEST(sftp_server);
    TEST(bigquery_server);
    TEST(salesforce_server);
    TEST(server_additional_props);

    printf("\nStrategy constants:\n");
    TEST(strategy_constants);
    TEST(all_strategies_start_with_prefix);

    printf("\nResource:\n");
    TEST(resource_build);
    TEST(resource_json);

    printf("\nBehavior:\n");
    TEST(behavior_all_types);
    TEST(behavior_staged);
    TEST(behavior_error_threshold);

    printf("\nPipeline:\n");
    TEST(full_pipeline);
    TEST(pipeline_save_load);
    TEST(pipeline_validation);
    TEST(cross_database_mysql_to_pg);
    TEST(csv_to_database);

    printf("\nConfig:\n");
    TEST(config_defaults);
    TEST(config_validate_empty_jar);
    TEST(config_validate_bad_timeout);
    TEST(executor_input_validation);

    printf("\nFacade:\n");
    TEST(connection_pg);
    TEST(connection_local_file);
    TEST(connection_s3_target);
    TEST(connection_validate);
    TEST(default_read_strategy);
    TEST(default_write_strategy);
    TEST(connection_mysql_target);

    printf("\n=== Results: %d/%d tests passed ===\n\n", tests_passed, tests_total);
    return (tests_passed == tests_total) ? 0 : 1;
}
