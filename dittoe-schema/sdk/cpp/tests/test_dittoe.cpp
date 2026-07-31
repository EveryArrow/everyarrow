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
 * DITTOE C++ SDK Test Suite
 * Tests enum coverage, fluent builders, JSON serialization,
 * strategy constants, and pipeline building.
 */

#include "../include/dittoe.hpp"
#include <cassert>
#include <iostream>
#include <fstream>
#include <filesystem>

static int tests_passed = 0;
static int tests_total = 0;

#define TEST(name) do { \
    tests_total++; \
    std::cout << "  [" << tests_total << "] " << #name << "... "; \
    test_##name(); \
    tests_passed++; \
    std::cout << "PASS" << std::endl; \
} while(0)

using namespace dittoe;

// ============================================================================
// Enum tests
// ============================================================================

void test_data_type_strings() {
    assert(std::string(to_string(DataType::String)) == "String");
    assert(std::string(to_string(DataType::Integer)) == "Integer");
    assert(std::string(to_string(DataType::XML)) == "XML");
    assert(std::string(to_string(DataType::JSONB)) == "JSONB");
    assert(std::string(to_string(DataType::UUID)) == "UUID");
    assert(std::string(to_string(DataType::Bytes)) == "Bytes");
    // Verify all 17 types
    for (int i = 0; i < 17; i++) {
        assert(std::string(to_string(static_cast<DataType>(i))).length() > 0);
    }
}

void test_behavior_type_strings() {
    assert(std::string(to_string(BehaviorType::Formatting)) == "Formatting");
    assert(std::string(to_string(BehaviorType::Cache)) == "Cache");
    assert(std::string(to_string(BehaviorType::lineageTracking)) == "lineageTracking");
    for (int i = 0; i < 22; i++) {
        assert(std::string(to_string(static_cast<BehaviorType>(i))).length() > 0);
    }
}

void test_server_type_strings() {
    assert(std::string(to_string(ServerType::Database)) == "Database");
    assert(std::string(to_string(ServerType::File)) == "File");
    assert(std::string(to_string(ServerType::API)) == "API");
    assert(std::string(to_string(ServerType::Stream)) == "Stream");
}

void test_template_type_strings() {
    assert(std::string(to_string(TemplateType::FileToFile)) == "FileToFile");
    assert(std::string(to_string(TemplateType::DatabaseToDatabase)) == "DatabaseToDatabase");
    assert(std::string(to_string(TemplateType::APIToStream)) == "APIToStream");
    for (int i = 0; i < 16; i++) {
        assert(std::string(to_string(static_cast<TemplateType>(i))).length() > 0);
    }
}

void test_stage_type_strings() {
    assert(std::string(to_string(StageType::Begin)) == "Begin");
    assert(std::string(to_string(StageType::End)) == "End");
    for (int i = 0; i < 6; i++) {
        assert(std::string(to_string(static_cast<StageType>(i))).length() > 0);
    }
}

void test_subtype_strings() {
    assert(std::string(to_string(DatabaseSubType::PostgreSQL)) == "PostgreSQLServer");
    assert(std::string(to_string(DatabaseSubType::Snowflake)) == "SnowflakeServer");
    assert(std::string(to_string(FileSubType::Local)) == "LocalFileServer");
    assert(std::string(to_string(FileSubType::GoogleDrive)) == "GoogleDriveServer");
    assert(std::string(to_string(APISubType::BigQuery)) == "BigQueryServer");
    assert(std::string(to_string(APISubType::Salesforce)) == "SalesforceServer");
}

// ============================================================================
// Field builder tests
// ============================================================================

void test_field_builder_basic() {
    auto f = FieldBuilder("name").string_().required().build();
    assert(f.name == "name");
    assert(f.dataType == DataType::String);
    assert(!f.isNullable);
}

void test_field_builder_all_types() {
    assert(FieldBuilder("a").integer().build().dataType == DataType::Integer);
    assert(FieldBuilder("a").double_().build().dataType == DataType::Double);
    assert(FieldBuilder("a").decimal().build().dataType == DataType::Decimal);
    assert(FieldBuilder("a").boolean().build().dataType == DataType::Boolean);
    assert(FieldBuilder("a").date().build().dataType == DataType::Date);
    assert(FieldBuilder("a").dateTime().build().dataType == DataType::DateTime);
    assert(FieldBuilder("a").time().build().dataType == DataType::Time);
    assert(FieldBuilder("a").uuid().build().dataType == DataType::UUID);
    assert(FieldBuilder("a").timeWithTz().build().dataType == DataType::TimeWithTimeZone);
    assert(FieldBuilder("a").dateTimeWithTz().build().dataType == DataType::DateTimeWithTimeZone);
    assert(FieldBuilder("a").json().build().dataType == DataType::JSON);
    assert(FieldBuilder("a").jsonb().build().dataType == DataType::JSONB);
    assert(FieldBuilder("a").array().build().dataType == DataType::Array);
    assert(FieldBuilder("a").number().build().dataType == DataType::Number);
    assert(FieldBuilder("a").bytes().build().dataType == DataType::Bytes);
    assert(FieldBuilder("a").xml().build().dataType == DataType::XML);
}

void test_field_expression() {
    auto f = FieldBuilder("calc").decimal().expression("price * 1.1").build();
    assert(f.hasExpression);
    assert(f.expression == "price * 1.1");
}

void test_field_json() {
    auto f = FieldBuilder("id").integer().required().build();
    auto j = f.to_json();
    assert(j.find("\"name\":\"id\"") != std::string::npos);
    assert(j.find("\"dataType\":\"Integer\"") != std::string::npos);
    assert(j.find("\"isNullable\":false") != std::string::npos);
}

// ============================================================================
// Server builder tests (all 19)
// ============================================================================

void test_postgresql_server() {
    auto s = ServerBuilder("pg").postgresql("localhost", 5432, "db", "u", "p").build();
    assert(s.type == ServerType::Database);
    assert(s.connectionDetails.at("databaseType") == "PostgreSQLServer");
    auto j = s.to_json();
    assert(j.find("\"type\":\"Database\"") != std::string::npos);
}

void test_mysql_server() {
    auto s = ServerBuilder("my").mysql("h", 3306, "db", "u", "p").build();
    assert(s.connectionDetails.at("databaseType") == "MySQLServer");
}

void test_mssql_server() {
    auto s = ServerBuilder("ms").mssql("h", 1433, "db", "sa", "p").build();
    assert(s.connectionDetails.at("databaseType") == "MSSQLServer");
}

void test_sqlite_server() {
    auto s = ServerBuilder("lite").sqlite("/tmp/test.db").build();
    assert(s.connectionDetails.at("databaseType") == "SQLiteServer");
    assert(s.connectionDetails.at("path") == "/tmp/test.db");
}

void test_snowflake_server() {
    auto s = ServerBuilder("snow").snowflake("acct.sf.com", 443, "db", "u", "p").build();
    assert(s.connectionDetails.at("databaseType") == "SnowflakeServer");
}

void test_local_file_server() {
    auto s = ServerBuilder("fs").localFile("/data").build();
    assert(s.type == ServerType::File);
    assert(s.connectionDetails.at("fileType") == "LocalFileServer");
}

void test_s3_server() {
    auto s = ServerBuilder("s3").s3("bucket", "us-east-1", "ak", "sk").build();
    assert(s.connectionDetails.at("fileType") == "S3Server");
}

void test_gcs_server() {
    auto s = ServerBuilder("gcs").gcs("bucket", "proj", "/creds").build();
    assert(s.connectionDetails.at("fileType") == "GCSServer");
}

void test_azure_server() {
    auto s = ServerBuilder("az").azure("container", "acct", "key").build();
    assert(s.connectionDetails.at("fileType") == "AzureServer");
}

void test_dropbox_server() {
    auto s = ServerBuilder("db").dropbox("token").build();
    assert(s.connectionDetails.at("fileType") == "DropboxServer");
}

void test_sftp_server() {
    auto s = ServerBuilder("sftp").sftp("h", 22, "/data", "u", "p").build();
    assert(s.connectionDetails.at("fileType") == "SFTPServer");
}

void test_ftp_server() {
    auto s = ServerBuilder("ftp").ftp("h", 21, "/data", "u", "p").build();
    assert(s.connectionDetails.at("fileType") == "FTPServer");
}

void test_ibm_server() {
    auto s = ServerBuilder("ibm").ibm("endpoint", "key", "bucket").build();
    assert(s.connectionDetails.at("fileType") == "IBMServer");
}

void test_oracle_server() {
    auto s = ServerBuilder("oci").oracleCloud("ns", "bucket", "comp").build();
    assert(s.connectionDetails.at("fileType") == "OracleServer");
}

void test_digitalocean_server() {
    auto s = ServerBuilder("do").digitalOcean("space", "region", "ak", "sk").build();
    assert(s.connectionDetails.at("fileType") == "DigitalOceanServer");
}

void test_googledrive_server() {
    auto s = ServerBuilder("gd").googleDrive("/creds", "folder").build();
    assert(s.connectionDetails.at("fileType") == "GoogleDriveServer");
}

void test_bigquery_server() {
    auto s = ServerBuilder("bq").bigquery("proj", "dataset", "/creds").build();
    assert(s.type == ServerType::API);
    assert(s.connectionDetails.at("apiType") == "BigQueryServer");
}

void test_salesforce_server() {
    auto s = ServerBuilder("sf").salesforce("https://login.sf.com", "u", "p", "tk").build();
    assert(s.connectionDetails.at("apiType") == "SalesforceServer");
}

void test_server_additional_props() {
    auto s = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p")
        .additionalProperty("timeout", "30").build();
    assert(s.additionalProperties.at("timeout") == "30");
}

// ============================================================================
// Strategy constant tests
// ============================================================================

void test_strategy_read_constants() {
    assert(strategy::read::JDBC_SELECT.find("SelectStrategy") != std::string::npos);
    assert(strategy::read::LOCAL_CSV.find("CSVReadStrategy") != std::string::npos);
    assert(strategy::read::LOCAL_JSON.find("JSONReadStrategy") != std::string::npos);
    assert(strategy::read::S3_CSV.find("s3") != std::string::npos);
    assert(strategy::read::BQ_QUERY.find("QueryStrategy") != std::string::npos);
    assert(strategy::read::BQ_AVRO.find("AvroStrategy") != std::string::npos);
    assert(strategy::read::SALESFORCE.find("salesforce.readstrategy.RestAPI") != std::string::npos);
}

void test_strategy_write_constants() {
    assert(strategy::write::JDBC_INSERT.find("InsertStrategy") != std::string::npos);
    assert(strategy::write::JDBC_REPLACE.find("ReplaceStrategy") != std::string::npos);
    assert(strategy::write::PG_UPSERT.find("UpsertOnConflict") != std::string::npos);
    assert(strategy::write::PG_COPY.find("CopyStrategy") != std::string::npos);
    assert(strategy::write::MSSQL_MERGE.find("MergeStrategy") != std::string::npos);
    assert(strategy::write::SQLITE_UPSERT.find("sqlite") != std::string::npos);
    assert(strategy::write::BQ_DATASTORE.find("DataStoreStrategy") != std::string::npos);
    assert(strategy::write::SALESFORCE.find("salesforce.writestrategy.RestAPI") != std::string::npos);
}

void test_all_strategies_prefix() {
    const std::string* all_read[] = {
        &strategy::read::JDBC_SELECT,
        &strategy::read::LOCAL_CSV, &strategy::read::LOCAL_JSON,
        &strategy::read::S3_CSV, &strategy::read::GCS_CSV, &strategy::read::AZURE_CSV,
        &strategy::read::DROPBOX_CSV, &strategy::read::SFTP_CSV, &strategy::read::FTP_CSV,
        &strategy::read::IBM_CSV, &strategy::read::ORACLE_CSV,
        &strategy::read::DO_CSV, &strategy::read::GDRIVE_CSV,
        &strategy::read::BQ_QUERY, &strategy::read::BQ_AVRO, &strategy::read::SALESFORCE,
    };
    const std::string* all_write[] = {
        &strategy::write::JDBC_INSERT, &strategy::write::JDBC_REPLACE,
        &strategy::write::PG_UPSERT, &strategy::write::PG_INSERT_DO_NOTHING,
        &strategy::write::PG_COPY, &strategy::write::PG_COPY_OR_AUTO_ID,
        &strategy::write::PG_INSERT_OR_UPDATE,
        &strategy::write::MSSQL_MERGE, &strategy::write::SQLITE_UPSERT,
        &strategy::write::LOCAL_CSV, &strategy::write::LOCAL_JSON,
        &strategy::write::S3_CSV, &strategy::write::GCS_CSV, &strategy::write::AZURE_CSV,
        &strategy::write::DROPBOX_CSV, &strategy::write::SFTP_CSV, &strategy::write::FTP_CSV,
        &strategy::write::IBM_CSV, &strategy::write::ORACLE_CSV,
        &strategy::write::DO_CSV, &strategy::write::GDRIVE_CSV,
        &strategy::write::BQ_DATASTORE, &strategy::write::SALESFORCE,
    };
    for (auto* s : all_read) assert(s->find("com.everyarrow.dittoe.io.") == 0);
    for (auto* s : all_write) assert(s->find("com.everyarrow.dittoe.io.") == 0);
    assert(sizeof(all_read)/sizeof(all_read[0]) == 16);
    assert(sizeof(all_write)/sizeof(all_write[0]) == 23);
}

// ============================================================================
// Resource builder tests
// ============================================================================

void test_resource_builder() {
    auto srv = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p").build();
    auto r = ResourceBuilder("products").server(srv)
        .addField("id", DataType::Integer, false)
        .addField("name", DataType::String)
        .schema("production")
        .jdbcRead().build();
    assert(r.resourceName == "products");
    assert(r.fields.size() == 2);
    assert(r.strategy_ == strategy::read::JDBC_SELECT);
    assert(r.resourceProperties.at("schema") == "production");
}

void test_resource_all_strategies() {
    auto srv = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p").build();
    assert(ResourceBuilder("t").server(srv).jdbcRead().build().strategy_ == strategy::read::JDBC_SELECT);
    assert(ResourceBuilder("t").server(srv).insert().build().strategy_ == strategy::write::JDBC_INSERT);
    assert(ResourceBuilder("t").server(srv).replace().build().strategy_ == strategy::write::JDBC_REPLACE);
    assert(ResourceBuilder("t").server(srv).pgUpsert("c").build().strategy_ == strategy::write::PG_UPSERT);
    assert(ResourceBuilder("t").server(srv).pgInsertDoNothing("c").build().strategy_ == strategy::write::PG_INSERT_DO_NOTHING);
    assert(ResourceBuilder("t").server(srv).pgCopy().build().strategy_ == strategy::write::PG_COPY);
    assert(ResourceBuilder("t").server(srv).pgCopyOrAutoId().build().strategy_ == strategy::write::PG_COPY_OR_AUTO_ID);
    assert(ResourceBuilder("t").server(srv).pgInsertOrUpdate().build().strategy_ == strategy::write::PG_INSERT_OR_UPDATE);
    assert(ResourceBuilder("t").server(srv).mssqlMerge().build().strategy_ == strategy::write::MSSQL_MERGE);
    assert(ResourceBuilder("t").server(srv).sqliteUpsert("c").build().strategy_ == strategy::write::SQLITE_UPSERT);
    assert(ResourceBuilder("t").server(srv).csvRead().build().strategy_ == strategy::read::LOCAL_CSV);
    assert(ResourceBuilder("t").server(srv).csvWrite().build().strategy_ == strategy::write::LOCAL_CSV);
    assert(ResourceBuilder("t").server(srv).jsonRead().build().strategy_ == strategy::read::LOCAL_JSON);
    assert(ResourceBuilder("t").server(srv).jsonWrite().build().strategy_ == strategy::write::LOCAL_JSON);
    assert(ResourceBuilder("t").server(srv).s3CsvRead().build().strategy_ == strategy::read::S3_CSV);
    assert(ResourceBuilder("t").server(srv).s3CsvWrite().build().strategy_ == strategy::write::S3_CSV);
    assert(ResourceBuilder("t").server(srv).gcsCsvRead().build().strategy_ == strategy::read::GCS_CSV);
    assert(ResourceBuilder("t").server(srv).gcsCsvWrite().build().strategy_ == strategy::write::GCS_CSV);
    assert(ResourceBuilder("t").server(srv).azureCsvRead().build().strategy_ == strategy::read::AZURE_CSV);
    assert(ResourceBuilder("t").server(srv).azureCsvWrite().build().strategy_ == strategy::write::AZURE_CSV);
    assert(ResourceBuilder("t").server(srv).dropboxCsvRead().build().strategy_ == strategy::read::DROPBOX_CSV);
    assert(ResourceBuilder("t").server(srv).dropboxCsvWrite().build().strategy_ == strategy::write::DROPBOX_CSV);
    assert(ResourceBuilder("t").server(srv).sftpCsvRead().build().strategy_ == strategy::read::SFTP_CSV);
    assert(ResourceBuilder("t").server(srv).sftpCsvWrite().build().strategy_ == strategy::write::SFTP_CSV);
    assert(ResourceBuilder("t").server(srv).ftpCsvRead().build().strategy_ == strategy::read::FTP_CSV);
    assert(ResourceBuilder("t").server(srv).ftpCsvWrite().build().strategy_ == strategy::write::FTP_CSV);
    assert(ResourceBuilder("t").server(srv).ibmCsvRead().build().strategy_ == strategy::read::IBM_CSV);
    assert(ResourceBuilder("t").server(srv).ibmCsvWrite().build().strategy_ == strategy::write::IBM_CSV);
    assert(ResourceBuilder("t").server(srv).oracleCsvRead().build().strategy_ == strategy::read::ORACLE_CSV);
    assert(ResourceBuilder("t").server(srv).oracleCsvWrite().build().strategy_ == strategy::write::ORACLE_CSV);
    assert(ResourceBuilder("t").server(srv).digitalOceanCsvRead().build().strategy_ == strategy::read::DO_CSV);
    assert(ResourceBuilder("t").server(srv).digitalOceanCsvWrite().build().strategy_ == strategy::write::DO_CSV);
    assert(ResourceBuilder("t").server(srv).googleDriveCsvRead().build().strategy_ == strategy::read::GDRIVE_CSV);
    assert(ResourceBuilder("t").server(srv).googleDriveCsvWrite().build().strategy_ == strategy::write::GDRIVE_CSV);
    assert(ResourceBuilder("t").server(srv).bigqueryQueryRead().build().strategy_ == strategy::read::BQ_QUERY);
    assert(ResourceBuilder("t").server(srv).bigqueryAvroRead().build().strategy_ == strategy::read::BQ_AVRO);
    assert(ResourceBuilder("t").server(srv).bigqueryWrite().build().strategy_ == strategy::write::BQ_DATASTORE);
    assert(ResourceBuilder("t").server(srv).salesforceRead().build().strategy_ == strategy::read::SALESFORCE);
    assert(ResourceBuilder("t").server(srv).salesforceWrite().build().strategy_ == strategy::write::SALESFORCE);
}

void test_resource_json() {
    auto srv = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p").build();
    auto r = ResourceBuilder("orders").server(srv)
        .addField("id", DataType::Integer, false).jdbcRead().build();
    auto j = r.to_json();
    assert(j.find("\"resourceName\":\"orders\"") != std::string::npos);
    assert(j.find("\"fields\":[") != std::string::npos);
    assert(j.find("SelectStrategy") != std::string::npos);
}

// ============================================================================
// Behavior builder tests
// ============================================================================

void test_behavior_all_22_types() {
    assert(BehaviorBuilder().formatting().build().behaviorType == BehaviorType::Formatting);
    assert(BehaviorBuilder().sourceRetrieval().build().behaviorType == BehaviorType::SourceRetrieval);
    assert(BehaviorBuilder().targetInsertUpdate().build().behaviorType == BehaviorType::TargetInsertUpdate);
    assert(BehaviorBuilder().transformation().build().behaviorType == BehaviorType::Transformation);
    assert(BehaviorBuilder().encryption().build().behaviorType == BehaviorType::Encryption);
    assert(BehaviorBuilder().logging().build().behaviorType == BehaviorType::Logging);
    assert(BehaviorBuilder().instrumentation().build().behaviorType == BehaviorType::Instrumentation);
    assert(BehaviorBuilder().lineageTracking().build().behaviorType == BehaviorType::lineageTracking);
    assert(BehaviorBuilder().keyGeneration().build().behaviorType == BehaviorType::KeyGeneration);
    assert(BehaviorBuilder().masking().build().behaviorType == BehaviorType::Masking);
    assert(BehaviorBuilder().deduplication().build().behaviorType == BehaviorType::Deduplication);
    assert(BehaviorBuilder().schemaEvolution().build().behaviorType == BehaviorType::SchemaEvolution);
    assert(BehaviorBuilder().retry().build().behaviorType == BehaviorType::Retry);
    assert(BehaviorBuilder().schemaValidation().build().behaviorType == BehaviorType::SchemaValidation);
    assert(BehaviorBuilder().dqRuleChecks().build().behaviorType == BehaviorType::DQRuleChecks);
    assert(BehaviorBuilder().archive().build().behaviorType == BehaviorType::Archive);
    assert(BehaviorBuilder().microBatch().build().behaviorType == BehaviorType::MicroBatch);
    assert(BehaviorBuilder().dataScienceAlgorithms().build().behaviorType == BehaviorType::DataScienceAlgorithms);
    assert(BehaviorBuilder().customCode().build().behaviorType == BehaviorType::CustomCode);
    assert(BehaviorBuilder().compression().build().behaviorType == BehaviorType::Compression);
    assert(BehaviorBuilder().externalWidget().build().behaviorType == BehaviorType::ExternalWidget);
    assert(BehaviorBuilder().cache().build().behaviorType == BehaviorType::Cache);
}

void test_behavior_staged() {
    auto b = BehaviorBuilder("Compress").compression()
        .staged(StageType::Package, 1, "com.plugin").build();
    assert(b.isStaged);
    assert(b.stageType == StageType::Package);
    assert(b.order == 1);
    auto j = b.to_json();
    assert(j.find("\"stageType\":\"Package\"") != std::string::npos);
}

void test_behavior_error_threshold() {
    auto b = BehaviorBuilder().sourceRetrieval().errorThreshold(5).build();
    assert(b.properties.at("errorCountThreshold") == "5");
}

void test_behavior_json() {
    auto b = BehaviorBuilder("SR").sourceRetrieval().description("get data").build();
    auto j = b.to_json();
    assert(j.find("\"behaviorType\":\"SourceRetrieval\"") != std::string::npos);
    assert(j.find("\"description\":\"get data\"") != std::string::npos);
}

// ============================================================================
// Pipeline builder tests
// ============================================================================

void test_full_pipeline() {
    auto src_srv = ServerBuilder("src").postgresql("localhost", 5433, "srcdb", "u", "p").build();
    auto tgt_srv = ServerBuilder("tgt").postgresql("localhost", 5434, "tgtdb", "u", "p").build();
    auto src = ResourceBuilder("product").server(src_srv)
        .addField("id", DataType::Integer, false)
        .addField("name", DataType::String)
        .addField("price", DataType::Decimal)
        .schema("production").jdbcRead().build();
    auto tgt = ResourceBuilder("product").server(tgt_srv)
        .addField("id", DataType::Integer, false)
        .addField("name", DataType::String)
        .addField("price", DataType::Decimal)
        .insert().build();

    auto p = PipelineBuilder("ProductMigration").databaseToDatabase()
        .description("Migrate products")
        .source(src).target(tgt)
        .mapFieldsSameName()
        .withDefaultBehaviors().build();

    assert(p.fieldMappings.size() == 3);
    assert(p.behaviors.size() == 4);
    auto j = p.to_json();
    assert(j.find("\"shortName\":\"ProductMigration\"") != std::string::npos);
    assert(j.find("\"templateType\":\"DatabaseToDatabase\"") != std::string::npos);
    assert(j.find("\"S2TMap\":[") != std::string::npos);
}

void test_all_16_template_types() {
    assert(PipelineBuilder("t").fileToFile().build().templateType == TemplateType::FileToFile);
    assert(PipelineBuilder("t").fileToDatabase().build().templateType == TemplateType::FileToDatabase);
    assert(PipelineBuilder("t").fileToStream().build().templateType == TemplateType::FileToStream);
    assert(PipelineBuilder("t").fileToApi().build().templateType == TemplateType::FileToAPI);
    assert(PipelineBuilder("t").databaseToDatabase().build().templateType == TemplateType::DatabaseToDatabase);
    assert(PipelineBuilder("t").databaseToFile().build().templateType == TemplateType::DatabaseToFile);
    assert(PipelineBuilder("t").databaseToStream().build().templateType == TemplateType::DatabaseToStream);
    assert(PipelineBuilder("t").databaseToApi().build().templateType == TemplateType::DatabaseToAPI);
    assert(PipelineBuilder("t").streamToStream().build().templateType == TemplateType::StreamToStream);
    assert(PipelineBuilder("t").streamToFile().build().templateType == TemplateType::StreamToFile);
    assert(PipelineBuilder("t").streamToDatabase().build().templateType == TemplateType::StreamToDatabase);
    assert(PipelineBuilder("t").streamToApi().build().templateType == TemplateType::StreamToAPI);
    assert(PipelineBuilder("t").apiToApi().build().templateType == TemplateType::APIToAPI);
    assert(PipelineBuilder("t").apiToFile().build().templateType == TemplateType::APIToFile);
    assert(PipelineBuilder("t").apiToDatabase().build().templateType == TemplateType::APIToDatabase);
    assert(PipelineBuilder("t").apiToStream().build().templateType == TemplateType::APIToStream);
}

void test_pipeline_save() {
    auto srv = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p").build();
    auto src = ResourceBuilder("t").server(srv).addField("id", DataType::Integer, false)
        .jdbcRead().build();
    auto tgt = ResourceBuilder("t").server(srv).addField("id", DataType::Integer, false)
        .insert().build();
    auto p = PipelineBuilder("SaveTest").databaseToDatabase()
        .source(src).target(tgt).mapFieldsSameName().withDefaultBehaviors().build();
    auto testPath = "build/dittoe_cpp_test/pmd.json";
    p.save(testPath);

    std::ifstream f(testPath);
    assert(f.good());
    std::string content((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    assert(content.find("\"shortName\":\"SaveTest\"") != std::string::npos);
    f.close();
    std::filesystem::remove_all("build/dittoe_cpp_test");
}

void test_cross_db_mysql_to_pg() {
    auto my = ServerBuilder("my").mysql("h", 3306, "src", "u", "p").build();
    auto pg = ServerBuilder("pg").postgresql("h", 5432, "tgt", "u", "p").build();
    auto src = ResourceBuilder("users").server(my).addField("id", DataType::Integer, false)
        .jdbcRead().build();
    auto tgt = ResourceBuilder("users").server(pg).addField("id", DataType::Integer, false)
        .insert().build();
    auto p = PipelineBuilder("MySQLToPG").databaseToDatabase()
        .source(src).target(tgt).mapFieldsSameName().withDefaultBehaviors().build();
    auto j = p.to_json();
    assert(j.find("MySQLServer") != std::string::npos);
    assert(j.find("PostgreSQLServer") != std::string::npos);
}

void test_csv_to_database() {
    auto fs = ServerBuilder("fs").localFile("/data").build();
    auto pg = ServerBuilder("pg").postgresql("h", 5432, "db", "u", "p").build();
    auto src = ResourceBuilder("data.csv").server(fs).addField("id", DataType::Integer, false)
        .csvRead().build();
    auto tgt = ResourceBuilder("data").server(pg).addField("id", DataType::Integer, false)
        .insert().build();
    auto p = PipelineBuilder("CSVImport").fileToDatabase()
        .source(src).target(tgt).mapFieldsSameName().withDefaultBehaviors().build();
    assert(p.templateType == TemplateType::FileToDatabase);
}

void test_dq_rule() {
    DQRule r{"not_null", "name IS NOT NULL"};
    auto j = r.to_json();
    assert(j.find("\"ruleName\":\"not_null\"") != std::string::npos);
    assert(j.find("name IS NOT NULL") != std::string::npos);
}

// ============================================================================
// Config tests
// ============================================================================

void test_config_defaults() {
    DittoeConfig c;
    assert(c.jarPath.empty());
    assert(c.baseDir == "./pipelines");
    assert(c.javaPath == "java");
    assert(c.javaOpts.empty());
    assert(c.timeout == 300);
}

void test_config_validate_empty_jar() {
    auto errors = DittoeConfig().validate();
    assert(!errors.empty());
    bool found = false;
    for (auto& e : errors) if (e.find("JarPath") != std::string::npos) found = true;
    assert(found);
}

void test_config_validate_bad_timeout() {
    DittoeConfig c;
    c.jarPath = "/nonexistent.jar";
    c.timeout = -1;
    auto errors = c.validate();
    bool found = false;
    for (auto& e : errors) if (e.find("Timeout") != std::string::npos) found = true;
    assert(found);
}

void test_config_ensure_base_dir() {
    DittoeConfig c;
    auto tmpDir = "build/dittoe_cpp_config_test_" + std::to_string(std::hash<int>{}(42));
    c.baseDir = tmpDir;
    auto result = c.ensureBaseDir();
    assert(std::filesystem::exists(result));
    std::filesystem::remove_all(tmpDir);
}

void test_executor_input_validation() {
    assert(isValidPipelineName("ProductMigration"));
    assert(isValidPipelineName("pipeline-1.2"));
    assert(!isValidPipelineName(""));
    assert(!isValidPipelineName(".hidden"));
    assert(!isValidPipelineName("../escape"));
    assert(!isValidPipelineName("bad name"));
    assert(!isValidPipelineName("bad;name"));

    assert(isSafeExecutorPath("/opt/dittoe/engine.jar"));
    assert(isSafeExecutorPath("C:\\Program Files\\dittoe\\engine.jar"));
    assert(!isSafeExecutorPath(""));
    assert(!isSafeExecutorPath("bad\npath"));
    assert(!isSafeExecutorPath("bad'path"));
}

// ============================================================================
// Facade tests
// ============================================================================

void test_connection_builder_pg() {
    auto conn = dittoe::ConnectionBuilder("products")
        .postgresql("localhost", 5433, "mydb", "user", "pass")
        .schema("production")
        .fields({"id:integer", "name:string", "price:decimal"})
        .asSource()
        .build();
    assert(conn.name == "products");
    assert(conn.fields.size() == 3);
    assert(conn.fields[0].dataType == DataType::Integer);
    assert(conn.strategy_ == strategy::read::JDBC_SELECT);
    assert(conn.schemaName == "production");
}

void test_connection_builder_local_file() {
    auto conn = dittoe::ConnectionBuilder("data.csv")
        .localFile("/data/imports")
        .fields({"id:int", "name:str"})
        .asSource()
        .build();
    assert(conn.strategy_ == strategy::read::LOCAL_CSV);
}

void test_connection_builder_s3_target() {
    auto conn = dittoe::ConnectionBuilder("output")
        .s3("mybucket", "us-east-1", "ak", "sk")
        .fields({"id:int"})
        .asTarget()
        .build();
    assert(conn.strategy_ == strategy::write::S3_CSV);
}

void test_connection_builder_throws_without_server() {
    bool threw = false;
    try { dittoe::ConnectionBuilder("test").build(); }
    catch (std::runtime_error&) { threw = true; }
    assert(threw);
}

void test_connection_validate() {
    Connection c;
    auto errors = c.validate();
    bool found = false;
    for (auto& e : errors) if (e.find("name is required") != std::string::npos) found = true;
    assert(found);
}

void test_parse_field() {
    auto f1 = parseField("id:integer");
    assert(f1.name == "id");
    assert(f1.dataType == DataType::Integer);
    auto f2 = parseField("name");
    assert(f2.dataType == DataType::String);
}

void test_default_behaviors() {
    auto b = defaultBehaviors();
    assert(b.size() == 4);
}

void test_build_field_mappings() {
    std::vector<Field> src = {{"id"}, {"name"}};
    std::vector<Field> tgt = {{"id"}, {"name"}};
    auto mappings = buildFieldMappings(src, tgt);
    assert(mappings.size() == 2);
}

// ============================================================================
// Main
// ============================================================================

int main() {
    std::cout << "\n=== DITTOE C++ SDK Tests ===\n\n";

    std::cout << "Enum completeness:\n";
    TEST(data_type_strings);
    TEST(behavior_type_strings);
    TEST(server_type_strings);
    TEST(template_type_strings);
    TEST(stage_type_strings);
    TEST(subtype_strings);

    std::cout << "\nField builders:\n";
    TEST(field_builder_basic);
    TEST(field_builder_all_types);
    TEST(field_expression);
    TEST(field_json);

    std::cout << "\nServer builders (19 types):\n";
    TEST(postgresql_server);
    TEST(mysql_server);
    TEST(mssql_server);
    TEST(sqlite_server);
    TEST(snowflake_server);
    TEST(local_file_server);
    TEST(s3_server);
    TEST(gcs_server);
    TEST(azure_server);
    TEST(dropbox_server);
    TEST(sftp_server);
    TEST(ftp_server);
    TEST(ibm_server);
    TEST(oracle_server);
    TEST(digitalocean_server);
    TEST(googledrive_server);
    TEST(bigquery_server);
    TEST(salesforce_server);
    TEST(server_additional_props);

    std::cout << "\nStrategy constants:\n";
    TEST(strategy_read_constants);
    TEST(strategy_write_constants);
    TEST(all_strategies_prefix);

    std::cout << "\nResource builders:\n";
    TEST(resource_builder);
    TEST(resource_all_strategies);
    TEST(resource_json);

    std::cout << "\nBehavior builders:\n";
    TEST(behavior_all_22_types);
    TEST(behavior_staged);
    TEST(behavior_error_threshold);
    TEST(behavior_json);

    std::cout << "\nPipeline:\n";
    TEST(full_pipeline);
    TEST(all_16_template_types);
    TEST(pipeline_save);
    TEST(cross_db_mysql_to_pg);
    TEST(csv_to_database);
    TEST(dq_rule);

    std::cout << "\nConfig:\n";
    TEST(config_defaults);
    TEST(config_validate_empty_jar);
    TEST(config_validate_bad_timeout);
    TEST(config_ensure_base_dir);
    TEST(executor_input_validation);

    std::cout << "\nFacade:\n";
    TEST(connection_builder_pg);
    TEST(connection_builder_local_file);
    TEST(connection_builder_s3_target);
    TEST(connection_builder_throws_without_server);
    TEST(connection_validate);
    TEST(parse_field);
    TEST(default_behaviors);
    TEST(build_field_mappings);

    std::cout << "\n=== Results: " << tests_passed << "/" << tests_total << " tests passed ===\n\n";
    return (tests_passed == tests_total) ? 0 : 1;
}
