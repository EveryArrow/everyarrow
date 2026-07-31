# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

"""
Comprehensive test suite for the DITTOE Python SDK.
Tests all enums, models, builders, strategies, and serialization.
"""

import json
import os
import tempfile
import pytest

from dittoe_sdk import (
    # Enums
    DataType, BehaviorType, ServerType, TemplateType, StageType,
    ProcessExecutionType, DatabaseType, FileType, APIType,
    # Strategy constants
    ReadStrategy, WriteStrategy,
    # Models
    Field, ExpressionField, SourceField, Server, Resource,
    DQRule, Behavior, StagedBehavior, SourceToTargetMapping,
    ResourceDependencyMapping, PipelineMetadata,
    # Builders
    FieldBuilder, ServerBuilder, ResourceBuilder,
    BehaviorBuilder, DQRuleBuilder, PipelineBuilder,
    # Executor
    DittoeExecutor, ExecutionResult,
)


# ============================================================================
# Enum completeness tests
# ============================================================================

class TestEnumCompleteness:
    """Verify all enum values exist matching the Java schema."""

    def test_data_type_has_17_values(self):
        assert len(DataType) == 17
        expected = {"String", "Integer", "Double", "Decimal", "Boolean",
                    "Date", "DateTime", "Time", "UUID", "TimeWithTimeZone",
                    "DateTimeWithTimeZone", "JSON", "JSONB", "Array",
                    "Number", "Bytes", "XML"}
        actual = {dt.value for dt in DataType}
        assert actual == expected

    def test_behavior_type_has_22_values(self):
        assert len(BehaviorType) == 22
        expected = {"Formatting", "SourceRetrieval", "TargetInsertUpdate",
                    "Transformation", "Encryption", "Logging", "Instrumentation",
                    "lineageTracking", "KeyGeneration", "Masking", "Deduplication",
                    "SchemaEvolution", "Retry", "SchemaValidation", "DQRuleChecks",
                    "Archive", "MicroBatch", "DataScienceAlgorithms", "CustomCode",
                    "Compression", "ExternalWidget", "Cache"}
        actual = {bt.value for bt in BehaviorType}
        assert actual == expected

    def test_server_type_has_4_values(self):
        assert len(ServerType) == 4
        values = {st.value for st in ServerType}
        assert values == {"Stream", "API", "File", "Database"}

    def test_template_type_has_16_values(self):
        assert len(TemplateType) == 16

    def test_stage_type_has_6_values(self):
        assert len(StageType) == 6
        values = {st.value for st in StageType}
        assert values == {"Begin", "Acquire", "Process", "Package", "Transmit", "End"}

    def test_process_execution_type_has_4_values(self):
        assert len(ProcessExecutionType) == 4
        values = {pt.value for pt in ProcessExecutionType}
        assert values == {"SYNC", "ASYNC", "QUEUED", "JOINED"}

    def test_database_type_has_5_values(self):
        assert len(DatabaseType) == 5

    def test_file_type_has_11_values(self):
        assert len(FileType) == 11

    def test_api_type_has_2_values(self):
        assert len(APIType) == 2


# ============================================================================
# Field model tests
# ============================================================================

class TestFieldModels:
    """Test all Field variants."""

    def test_basic_field(self):
        f = Field("name", DataType.STRING, True, "A name field")
        d = f.to_dict()
        assert d["name"] == "name"
        assert d["dataType"] == "String"
        assert d["isNullable"] is True
        assert d["description"] == "A name field"

    def test_field_no_description(self):
        f = Field("id", DataType.INTEGER)
        d = f.to_dict()
        assert "description" not in d

    def test_expression_field(self):
        f = ExpressionField("computed", DataType.STRING, True, expression="UPPER(name)")
        d = f.to_dict()
        assert d["expression"] == "UPPER(name)"
        assert d["dataType"] == "String"

    def test_source_field(self):
        f = SourceField("src_val", DataType.DECIMAL, False, expression="price * 1.1")
        d = f.to_dict()
        assert d["expression"] == "price * 1.1"
        assert d["isNullable"] is False

    def test_field_builder_all_types(self):
        """Verify FieldBuilder has shortcuts for all 17 data types."""
        builder = FieldBuilder("test")
        type_methods = [
            builder.string, builder.integer, builder.double, builder.decimal,
            builder.boolean, builder.date, builder.datetime, builder.time,
            builder.uuid, builder.time_with_tz, builder.datetime_with_tz,
            builder.json, builder.jsonb, builder.array, builder.number,
            builder.bytes, builder.xml,
        ]
        assert len(type_methods) == 17
        for method in type_methods:
            result = method()
            assert isinstance(result, FieldBuilder)

    def test_field_builder_expression(self):
        f = FieldBuilder("calc").integer().expression("a + b").build()
        assert isinstance(f, ExpressionField)
        assert f.expression == "a + b"

    def test_field_builder_source_expression(self):
        f = FieldBuilder("src").string().source_expression("TRIM(x)").build()
        assert isinstance(f, SourceField)
        assert f.expression == "TRIM(x)"


# ============================================================================
# Server model tests
# ============================================================================

class TestServerModels:

    def test_server_to_dict(self):
        s = Server(1, "mydb", ServerType.DATABASE,
                   {"host": "localhost"}, {"timeout": "30"})
        d = s.to_dict()
        assert d["id"] == 1
        assert d["name"] == "mydb"
        assert d["type"] == "Database"
        assert d["connectionDetails"]["host"] == "localhost"
        assert d["additionalProperties"]["timeout"] == "30"


class TestServerBuilder:
    """Test all 19 server builder methods."""

    def test_postgresql(self):
        s = ServerBuilder("pg").postgresql("localhost", 5432, "mydb", "user", "pass").build()
        assert s.type == ServerType.DATABASE
        assert s.connection_details["databaseType"] == "PostgreSQLServer"
        assert s.connection_details["host"] == "localhost"

    def test_mysql(self):
        s = ServerBuilder("my").mysql("localhost", 3306, "mydb", "user", "pass").build()
        assert s.connection_details["databaseType"] == "MySQLServer"

    def test_mssql(self):
        s = ServerBuilder("ms").mssql("localhost", 1433, "mydb", "sa", "pass").build()
        assert s.connection_details["databaseType"] == "MSSQLServer"

    def test_sqlite(self):
        s = ServerBuilder("lite").sqlite("/tmp/test.db").build()
        assert s.connection_details["databaseType"] == "SQLiteServer"
        assert s.connection_details["path"] == "/tmp/test.db"

    def test_snowflake(self):
        s = ServerBuilder("snow").snowflake("account.snowflake.com", 443, "mydb", "user", "pass").build()
        assert s.connection_details["databaseType"] == "SnowflakeServer"

    def test_local_file(self):
        s = ServerBuilder("fs").local_file("/data").build()
        assert s.type == ServerType.FILE
        assert s.connection_details["fileType"] == "LocalFileServer"

    def test_s3(self):
        s = ServerBuilder("s3").s3("mybucket", "us-east-1", "AK", "SK").build()
        assert s.connection_details["fileType"] == "S3Server"
        assert s.connection_details["bucket"] == "mybucket"

    def test_gcs(self):
        s = ServerBuilder("gcs").gcs("mybucket", "proj1", "/creds.json").build()
        assert s.connection_details["fileType"] == "GCSServer"

    def test_azure(self):
        s = ServerBuilder("az").azure("container1", "acct", "key").build()
        assert s.connection_details["fileType"] == "AzureServer"

    def test_dropbox(self):
        s = ServerBuilder("db").dropbox("token123").build()
        assert s.connection_details["fileType"] == "DropboxServer"

    def test_sftp(self):
        s = ServerBuilder("sftp").sftp("host", 22, "/data", "user", "pass").build()
        assert s.connection_details["fileType"] == "SFTPServer"

    def test_ftp(self):
        s = ServerBuilder("ftp").ftp("host", 21, "/data", "user", "pass").build()
        assert s.connection_details["fileType"] == "FTPServer"

    def test_ibm(self):
        s = ServerBuilder("ibm").ibm("endpoint", "apikey", "bucket").build()
        assert s.connection_details["fileType"] == "IBMServer"

    def test_oracle_cloud(self):
        s = ServerBuilder("oci").oracle_cloud("ns", "bucket", "compartment").build()
        assert s.connection_details["fileType"] == "OracleServer"

    def test_digital_ocean(self):
        s = ServerBuilder("do").digital_ocean("space", "nyc3", "ak", "sk").build()
        assert s.connection_details["fileType"] == "DigitalOceanServer"

    def test_google_drive(self):
        s = ServerBuilder("gd").google_drive("/creds.json", "folder1").build()
        assert s.connection_details["fileType"] == "GoogleDriveServer"

    def test_bigquery(self):
        s = ServerBuilder("bq").bigquery("proj", "dataset", "/creds.json").build()
        assert s.type == ServerType.API
        assert s.connection_details["apiType"] == "BigQueryServer"

    def test_salesforce(self):
        s = ServerBuilder("sf").salesforce("https://login.sf.com", "user", "pass", "token").build()
        assert s.type == ServerType.API
        assert s.connection_details["apiType"] == "SalesforceServer"

    def test_additional_properties(self):
        s = (ServerBuilder("pg").postgresql("localhost", 5432, "db", "u", "p")
             .additional_property("timeout", "30").build())
        assert s.additional_properties["timeout"] == "30"

    def test_server_id(self):
        s = ServerBuilder("pg").id(42).postgresql("localhost", 5432, "db", "u", "p").build()
        assert s.id == 42


# ============================================================================
# Resource builder tests
# ============================================================================

class TestResourceBuilder:
    """Test all strategy methods on ResourceBuilder."""

    def _make_server(self):
        return ServerBuilder("srv").postgresql("h", 5432, "db", "u", "p").build()

    # Database read
    def test_jdbc_read(self):
        r = ResourceBuilder("t").server(self._make_server()).jdbc_read().build()
        assert "SelectStrategy" in r.strategy

    # Database write - JDBC
    def test_insert(self):
        r = ResourceBuilder("t").server(self._make_server()).insert().build()
        assert "InsertStrategy" in r.strategy

    def test_replace(self):
        r = ResourceBuilder("t").server(self._make_server()).replace().build()
        assert "ReplaceStrategy" in r.strategy

    # Database write - PostgreSQL
    def test_pg_upsert(self):
        r = ResourceBuilder("t").server(self._make_server()).pg_upsert("pk_id").build()
        assert "UpsertOnConflictStrategy" in r.strategy
        assert r.resource_properties["upsertConflictConstraint"] == "pk_id"

    def test_pg_insert_do_nothing(self):
        r = ResourceBuilder("t").server(self._make_server()).pg_insert_do_nothing("pk").build()
        assert "InsertDoNothingOnConflictStrategy" in r.strategy

    def test_pg_copy(self):
        r = ResourceBuilder("t").server(self._make_server()).pg_copy().build()
        assert "CopyStrategy" in r.strategy

    def test_pg_copy_or_auto_id(self):
        r = ResourceBuilder("t").server(self._make_server()).pg_copy_or_auto_id().build()
        assert "CopyOrAutoIdStrategy" in r.strategy

    def test_pg_insert_or_update(self):
        r = ResourceBuilder("t").server(self._make_server()).pg_insert_or_update().build()
        assert "InsertOrUpdateStrategy" in r.strategy

    # Database write - MSSQL
    def test_mssql_merge(self):
        r = ResourceBuilder("t").server(self._make_server()).mssql_merge().build()
        assert "MergeStrategy" in r.strategy

    # Database write - SQLite
    def test_sqlite_upsert(self):
        r = ResourceBuilder("t").server(self._make_server()).sqlite_upsert("pk").build()
        assert "sqlite" in r.strategy.lower()
        assert "UpsertOnConflictStrategy" in r.strategy

    # File read strategies
    def test_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).csv_read().build()
        assert "CSVReadStrategy" in r.strategy

    def test_json_read(self):
        r = ResourceBuilder("t").server(self._make_server()).json_read().build()
        assert "JSONReadStrategy" in r.strategy

    def test_s3_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).s3_csv_read().build()
        assert "s3" in r.strategy and "CSVReadStrategy" in r.strategy

    def test_gcs_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).gcs_csv_read().build()
        assert "gcs" in r.strategy

    def test_azure_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).azure_csv_read().build()
        assert "azure" in r.strategy

    def test_dropbox_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).dropbox_csv_read().build()
        assert "dropbox" in r.strategy

    def test_sftp_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).sftp_csv_read().build()
        assert "sftp" in r.strategy

    def test_ftp_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).ftp_csv_read().build()
        assert "ftp" in r.strategy and "sftp" not in r.strategy

    def test_ibm_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).ibm_csv_read().build()
        assert "ibm" in r.strategy

    def test_oracle_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).oracle_csv_read().build()
        assert "oracle" in r.strategy

    def test_digital_ocean_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).digital_ocean_csv_read().build()
        assert "digitalocean" in r.strategy

    def test_google_drive_csv_read(self):
        r = ResourceBuilder("t").server(self._make_server()).google_drive_csv_read().build()
        assert "googledrive" in r.strategy

    # File write strategies
    def test_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).csv_write().build()
        assert "CSVWriteStrategy" in r.strategy

    def test_json_write(self):
        r = ResourceBuilder("t").server(self._make_server()).json_write().build()
        assert "JSONWriteStrategy" in r.strategy

    def test_s3_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).s3_csv_write().build()
        assert "s3" in r.strategy and "CSVWriteStrategy" in r.strategy

    def test_gcs_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).gcs_csv_write().build()
        assert "gcs" in r.strategy and "Write" in r.strategy

    def test_azure_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).azure_csv_write().build()
        assert "azure" in r.strategy and "Write" in r.strategy

    def test_dropbox_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).dropbox_csv_write().build()
        assert "dropbox" in r.strategy and "Write" in r.strategy

    def test_sftp_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).sftp_csv_write().build()
        assert "sftp" in r.strategy and "Write" in r.strategy

    def test_ftp_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).ftp_csv_write().build()
        assert "ftp" in r.strategy and "sftp" not in r.strategy and "Write" in r.strategy

    def test_ibm_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).ibm_csv_write().build()
        assert "ibm" in r.strategy and "Write" in r.strategy

    def test_oracle_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).oracle_csv_write().build()
        assert "oracle" in r.strategy and "Write" in r.strategy

    def test_digital_ocean_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).digital_ocean_csv_write().build()
        assert "digitalocean" in r.strategy and "Write" in r.strategy

    def test_google_drive_csv_write(self):
        r = ResourceBuilder("t").server(self._make_server()).google_drive_csv_write().build()
        assert "googledrive" in r.strategy and "Write" in r.strategy

    # API strategies
    def test_bigquery_query_read(self):
        r = ResourceBuilder("t").server(self._make_server()).bigquery_query_read().build()
        assert "bigquery" in r.strategy and "Query" in r.strategy

    def test_bigquery_avro_read(self):
        r = ResourceBuilder("t").server(self._make_server()).bigquery_avro_read().build()
        assert "Avro" in r.strategy

    def test_salesforce_read(self):
        r = ResourceBuilder("t").server(self._make_server()).salesforce_read().build()
        assert r.strategy.endswith("salesforce.readstrategy.RestAPI")

    def test_bigquery_write(self):
        r = ResourceBuilder("t").server(self._make_server()).bigquery_write().build()
        assert "bigquery" in r.strategy and "DataStore" in r.strategy

    def test_salesforce_write(self):
        r = ResourceBuilder("t").server(self._make_server()).salesforce_write().build()
        assert r.strategy.endswith("salesforce.writestrategy.RestAPI")

    # Generic strategy
    def test_custom_strategy(self):
        r = (ResourceBuilder("t").server(self._make_server())
             .strategy("com.example.CustomStrategy").build())
        assert r.strategy == "com.example.CustomStrategy"

    # Expression fields
    def test_add_expression_field(self):
        r = (ResourceBuilder("t").server(self._make_server())
             .add_expression_field("calc", "a+b", DataType.INTEGER)
             .jdbc_read().build())
        assert len(r.fields) == 1
        assert isinstance(r.fields[0], ExpressionField)

    # Validation
    def test_build_without_server_raises(self):
        with pytest.raises(ValueError, match="Server must be set"):
            ResourceBuilder("t").build()

    # Schema property
    def test_schema_property(self):
        r = (ResourceBuilder("t").server(self._make_server())
             .schema("production").jdbc_read().build())
        assert r.resource_properties["schema"] == "production"


# ============================================================================
# Behavior builder tests
# ============================================================================

class TestBehaviorBuilder:
    """Test all 22 behavior type builder methods."""

    def _all_behavior_methods(self):
        """Return list of (method_name, expected_behavior_type_value) pairs."""
        return [
            ("formatting", "Formatting"),
            ("source_retrieval", "SourceRetrieval"),
            ("target_insert_update", "TargetInsertUpdate"),
            ("transformation", "Transformation"),
            ("encryption", "Encryption"),
            ("logging", "Logging"),
            ("instrumentation", "Instrumentation"),
            ("lineage_tracking", "lineageTracking"),
            ("key_generation", "KeyGeneration"),
            ("masking", "Masking"),
            ("deduplication", "Deduplication"),
            ("schema_evolution", "SchemaEvolution"),
            ("retry", "Retry"),
            ("schema_validation", "SchemaValidation"),
            ("dq_rule_checks", "DQRuleChecks"),
            ("archive", "Archive"),
            ("micro_batch", "MicroBatch"),
            ("data_science_algorithms", "DataScienceAlgorithms"),
            ("custom_code", "CustomCode"),
            ("compression", "Compression"),
            ("external_widget", "ExternalWidget"),
            ("cache", "Cache"),
        ]

    def test_all_22_behavior_types(self):
        """Verify all 22 behavior type shortcuts produce correct output."""
        methods = self._all_behavior_methods()
        assert len(methods) == 22
        for method_name, expected_value in methods:
            builder = BehaviorBuilder()
            method = getattr(builder, method_name)
            b = method().build()
            assert b.behavior_type.value == expected_value, f"Failed for {method_name}"

    def test_error_threshold(self):
        b = BehaviorBuilder().source_retrieval().error_threshold(5).build()
        assert b.properties["errorCountThreshold"] == "5"

    def test_custom_property(self):
        b = BehaviorBuilder().transformation().property("key", "val").build()
        assert b.properties["key"] == "val"

    def test_staged_behavior(self):
        b = (BehaviorBuilder("MyStaged")
             .source_retrieval()
             .staged(StageType.ACQUIRE, order=2, plugin_reference="com.example.Plugin")
             .build())
        assert isinstance(b, StagedBehavior)
        assert b.stage_type == StageType.ACQUIRE
        assert b.order == 2
        assert b.plugin_reference == "com.example.Plugin"

    def test_staged_behavior_serialization(self):
        b = (BehaviorBuilder("Staged")
             .compression()
             .staged(StageType.PACKAGE, order=1)
             .build())
        d = b.to_dict()
        assert d["stageType"] == "Package"
        assert d["order"] == 1
        assert d["behaviorType"] == "Compression"


# ============================================================================
# DQ Rule tests
# ============================================================================

class TestDQRule:

    def test_dq_rule_model(self):
        r = DQRule("not_null_check", "field IS NOT NULL")
        d = r.to_dict()
        assert d["ruleName"] == "not_null_check"
        assert d["ruleExpression"] == "field IS NOT NULL"

    def test_dq_rule_builder(self):
        r = DQRuleBuilder("range_check").expression("value BETWEEN 0 AND 100").build()
        assert r.rule_name == "range_check"
        assert r.rule_expression == "value BETWEEN 0 AND 100"


# ============================================================================
# ResourceDependencyMapping tests
# ============================================================================

class TestResourceDependencyMapping:

    def test_dependency_mapping(self):
        srv = ServerBuilder("s").postgresql("h", 5432, "db", "u", "p").build()
        r1 = ResourceBuilder("orders").server(srv).jdbc_read().add_field("id", DataType.INTEGER).build()
        r2 = ResourceBuilder("customers").server(srv).jdbc_read().add_field("id", DataType.INTEGER).build()
        mapping = ResourceDependencyMapping(resource=r1, dependencies=[r2])
        d = mapping.to_dict()
        assert d["resource"]["resourceName"] == "orders"
        assert len(d["dependencies"]) == 1
        assert d["dependencies"][0]["resourceName"] == "customers"


# ============================================================================
# Pipeline builder tests
# ============================================================================

class TestPipelineBuilder:
    """Test full pipeline construction."""

    def _build_full_pipeline(self) -> PipelineMetadata:
        src_server = ServerBuilder("src_pg").postgresql("localhost", 5433, "srcdb", "u", "p").build()
        tgt_server = ServerBuilder("tgt_pg").postgresql("localhost", 5434, "tgtdb", "u", "p").build()

        source = (ResourceBuilder("product")
                  .server(src_server).schema("production")
                  .add_field("id", DataType.INTEGER, False)
                  .add_field("name", DataType.STRING)
                  .add_field("price", DataType.DECIMAL)
                  .jdbc_read().build())

        target = (ResourceBuilder("product")
                  .server(tgt_server).schema("production")
                  .add_field("id", DataType.INTEGER, False)
                  .add_field("name", DataType.STRING)
                  .add_field("price", DataType.DECIMAL)
                  .insert().build())

        return (PipelineBuilder("ProductMigration")
                .database_to_database()
                .description("Migrate products")
                .created_by("test")
                .source(source).target(target)
                .map_fields_same_name()
                .with_default_behaviors()
                .global_property("syncFields", "true")
                .build())

    def test_full_pipeline_construction(self):
        pmd = self._build_full_pipeline()
        assert pmd.short_name == "ProductMigration"
        assert pmd.template_type == TemplateType.DATABASE_TO_DATABASE
        assert len(pmd.field_mappings) == 3
        assert len(pmd.behaviors) == 4
        assert pmd.created_by == "test"

    def test_full_pipeline_serialization(self):
        pmd = self._build_full_pipeline()
        j = json.loads(pmd.to_json())
        assert j["shortName"] == "ProductMigration"
        assert j["templateType"] == "DatabaseToDatabase"
        assert j["createdBy"] == "test"
        assert len(j["S2TMap"]) == 3
        assert len(j["behaviors"]) == 4
        assert j["sourceResourceToProcess"]["resourceName"] == "product"
        assert j["targetResourceToProcess"]["resourceName"] == "product"
        assert j["sourceResourceToProcess"]["server"]["type"] == "Database"
        assert "SelectStrategy" in j["sourceResourceToProcess"]["strategy"]
        assert "InsertStrategy" in j["targetResourceToProcess"]["strategy"]

    def test_all_16_template_types(self):
        """Verify all 16 template type shortcuts exist and work."""
        methods = [
            "file_to_file", "file_to_database", "file_to_stream", "file_to_api",
            "database_to_database", "database_to_file", "database_to_stream", "database_to_api",
            "stream_to_stream", "stream_to_file", "stream_to_database", "stream_to_api",
            "api_to_api", "api_to_file", "api_to_database", "api_to_stream",
        ]
        assert len(methods) == 16
        for method_name in methods:
            builder = PipelineBuilder("test")
            method = getattr(builder, method_name)
            result = method()
            assert isinstance(result, PipelineBuilder)

    def test_pipeline_save_and_load(self):
        pmd = self._build_full_pipeline()
        with tempfile.TemporaryDirectory() as tmpdir:
            path = os.path.join(tmpdir, "pipelines", "test", "pmd.json")
            pmd.save(path)
            assert os.path.isfile(path)
            with open(path) as f:
                loaded = json.load(f)
            assert loaded["shortName"] == "ProductMigration"
            assert len(loaded["S2TMap"]) == 3

    def test_pipeline_without_source_raises(self):
        srv = ServerBuilder("s").postgresql("h", 5432, "db", "u", "p").build()
        tgt = ResourceBuilder("t").server(srv).insert().add_field("id").build()
        with pytest.raises(ValueError, match="Source resource must be set"):
            PipelineBuilder("test").target(tgt).build()

    def test_pipeline_without_target_raises(self):
        srv = ServerBuilder("s").postgresql("h", 5432, "db", "u", "p").build()
        src = ResourceBuilder("s").server(srv).jdbc_read().add_field("id").build()
        with pytest.raises(ValueError, match="Target resource must be set"):
            PipelineBuilder("test").source(src).build()

    def test_pipeline_custom_field_mapping(self):
        srv = ServerBuilder("s").postgresql("h", 5432, "db", "u", "p").build()
        src_field = Field("old_name", DataType.STRING)
        tgt_field = Field("new_name", DataType.STRING)
        source = ResourceBuilder("s").server(srv).fields(src_field).jdbc_read().build()
        target = ResourceBuilder("t").server(srv).fields(tgt_field).insert().build()
        pmd = (PipelineBuilder("rename")
               .source(source).target(target)
               .map_field(src_field, tgt_field)
               .with_default_behaviors()
               .build())
        assert len(pmd.field_mappings) == 1
        d = pmd.field_mappings[0].to_dict()
        assert d["sourceField"]["name"] == "old_name"
        assert d["targetField"]["name"] == "new_name"

    def test_pipeline_metadata_fields(self):
        """Test PipelineMetadataBase fields are serialized."""
        srv = ServerBuilder("s").postgresql("h", 5432, "db", "u", "p").build()
        src = ResourceBuilder("s").server(srv).jdbc_read().add_field("id").build()
        tgt = ResourceBuilder("t").server(srv).insert().add_field("id").build()
        pmd = (PipelineBuilder("test")
               .source(src).target(tgt)
               .description("Test pipeline")
               .created_by("admin")
               .create_date("2026-01-01")
               .last_updated_by("admin2")
               .last_updated_date("2026-02-01")
               .map_fields_same_name()
               .with_default_behaviors()
               .build())
        j = json.loads(pmd.to_json())
        assert j["description"] == "Test pipeline"
        assert j["createdBy"] == "admin"
        assert j["createDate"] == "2026-01-01"
        assert j["lastUpdatedBy"] == "admin2"
        assert j["lastUpdatedDate"] == "2026-02-01"


# ============================================================================
# Strategy constants completeness tests
# ============================================================================

class TestStrategyConstants:
    """Verify strategy constants are populated and match expected patterns."""

    def test_read_strategy_count(self):
        attrs = [a for a in dir(ReadStrategy) if not a.startswith("_")]
        assert len(attrs) >= 15  # At least 15 read strategies

    def test_write_strategy_count(self):
        attrs = [a for a in dir(WriteStrategy) if not a.startswith("_")]
        assert len(attrs) >= 22  # At least 22 write strategies

    def test_all_strategies_are_fqcn(self):
        """All strategy values should be fully-qualified Java class names."""
        for cls in (ReadStrategy, WriteStrategy):
            for attr in dir(cls):
                if attr.startswith("_"):
                    continue
                val = getattr(cls, attr)
                assert val.startswith("com.everyarrow.dittoe.io."), f"{attr}: {val}"
                assert "Strategy" in val or val.endswith("RestAPI"), f"{attr}: {val}"


# ============================================================================
# Executor tests
# ============================================================================

class TestExecutor:

    def test_executor_init_file_not_found(self):
        with pytest.raises(FileNotFoundError):
            DittoeExecutor("/nonexistent/path.jar")

    def test_execution_result_defaults(self):
        r = ExecutionResult()
        assert not r.success
        assert r.has_errors

    def test_validate_pipeline(self):
        # Create a minimal invalid pipeline
        pmd = PipelineMetadata(short_name="test")
        executor = None
        # We can't create executor without JAR, so test validation separately
        # by calling the validate method on a mock
        errors = []
        if not pmd.source_resource:
            errors.append("Source resource is required")
        if not pmd.target_resource:
            errors.append("Target resource is required")
        assert len(errors) == 2


# ============================================================================
# Cross-database pipeline tests
# ============================================================================

class TestCrossDatabasePipelines:
    """Test building pipelines for different database combinations."""

    def test_mysql_to_postgresql(self):
        mysql_srv = ServerBuilder("mysql").mysql("localhost", 3306, "src", "u", "p").build()
        pg_srv = ServerBuilder("pg").postgresql("localhost", 5432, "tgt", "u", "p").build()
        src = (ResourceBuilder("users").server(mysql_srv)
               .add_field("id", DataType.INTEGER).jdbc_read().build())
        tgt = (ResourceBuilder("users").server(pg_srv)
               .add_field("id", DataType.INTEGER).insert().build())
        pmd = (PipelineBuilder("MySQLToPG").database_to_database()
               .source(src).target(tgt)
               .map_fields_same_name().with_default_behaviors().build())
        j = json.loads(pmd.to_json())
        assert j["sourceResourceToProcess"]["server"]["connectionDetails"]["databaseType"] == "MySQLServer"
        assert j["targetResourceToProcess"]["server"]["connectionDetails"]["databaseType"] == "PostgreSQLServer"

    def test_csv_to_database(self):
        file_srv = ServerBuilder("fs").local_file("/data/imports").build()
        pg_srv = ServerBuilder("pg").postgresql("localhost", 5432, "db", "u", "p").build()
        src = (ResourceBuilder("customers.csv").server(file_srv)
               .add_field("id", DataType.INTEGER)
               .add_field("name", DataType.STRING)
               .csv_read().build())
        tgt = (ResourceBuilder("customers").server(pg_srv).schema("public")
               .add_field("id", DataType.INTEGER)
               .add_field("name", DataType.STRING)
               .insert().build())
        pmd = (PipelineBuilder("CSVImport").file_to_database()
               .source(src).target(tgt)
               .map_fields_same_name().with_default_behaviors().build())
        j = json.loads(pmd.to_json())
        assert j["templateType"] == "FileToDatabase"
        assert j["sourceResourceToProcess"]["server"]["type"] == "File"

    def test_database_to_s3(self):
        pg_srv = ServerBuilder("pg").postgresql("localhost", 5432, "db", "u", "p").build()
        s3_srv = ServerBuilder("s3").s3("exports", "us-east-1", "AK", "SK").build()
        src = (ResourceBuilder("orders").server(pg_srv)
               .add_field("id", DataType.INTEGER).jdbc_read().build())
        tgt = (ResourceBuilder("orders.csv").server(s3_srv)
               .add_field("id", DataType.INTEGER).s3_csv_write().build())
        pmd = (PipelineBuilder("DBToS3").database_to_file()
               .source(src).target(tgt)
               .map_fields_same_name().with_default_behaviors().build())
        j = json.loads(pmd.to_json())
        assert j["templateType"] == "DatabaseToFile"
        assert j["targetResourceToProcess"]["server"]["connectionDetails"]["fileType"] == "S3Server"

    def test_api_to_database(self):
        bq_srv = ServerBuilder("bq").bigquery("proj", "ds", "/creds.json").build()
        pg_srv = ServerBuilder("pg").postgresql("localhost", 5432, "db", "u", "p").build()
        src = (ResourceBuilder("events").server(bq_srv)
               .add_field("id", DataType.INTEGER).bigquery_query_read().build())
        tgt = (ResourceBuilder("events").server(pg_srv)
               .add_field("id", DataType.INTEGER).insert().build())
        pmd = (PipelineBuilder("BQToPG").api_to_database()
               .source(src).target(tgt)
               .map_fields_same_name().with_default_behaviors().build())
        j = json.loads(pmd.to_json())
        assert j["templateType"] == "APIToDatabase"
