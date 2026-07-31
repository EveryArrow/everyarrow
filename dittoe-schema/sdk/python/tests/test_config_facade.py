"""
Tests for DittoeConfig and Dittoe facade.
Tests configuration loading, validation, ConnectionBuilder, and migrate().
"""

import json
import os
import tempfile
import pytest

from dittoe_sdk import (
    # Config
    DittoeConfig, DittoeConfigError,
    # Facade
    Dittoe, Connection, ConnectionBuilder, parse_field, default_behaviors,
    # Models (for assertions)
    DataType, Field, Behavior, ReadStrategy, WriteStrategy,
    SourceToTargetMapping, TemplateType,
)


# ============================================================================
# DittoeConfig tests
# ============================================================================

class TestDittoeConfig:

    def test_default_values(self):
        config = DittoeConfig()
        assert config.jar_path == ""
        assert config.base_dir == "./pipelines"
        assert config.java_path == "java"
        assert config.java_opts == []
        assert config.timeout == 300

    def test_from_env(self, monkeypatch):
        monkeypatch.setenv("DITTOE_JAR", "/path/to/jar.jar")
        monkeypatch.setenv("DITTOE_BASE_DIR", "/tmp/pipelines")
        monkeypatch.setenv("DITTOE_JAVA", "/usr/bin/java")
        monkeypatch.setenv("DITTOE_JAVA_OPTS", "-Xmx2g,-Xms512m")
        monkeypatch.setenv("DITTOE_TIMEOUT", "600")

        config = DittoeConfig.from_env()
        assert config.jar_path == "/path/to/jar.jar"
        assert config.base_dir == "/tmp/pipelines"
        assert config.java_path == "/usr/bin/java"
        assert config.java_opts == ["-Xmx2g", "-Xms512m"]
        assert config.timeout == 600

    def test_from_env_defaults(self, monkeypatch):
        """When env vars are not set, defaults are used."""
        for var in ("DITTOE_JAR", "DITTOE_BASE_DIR", "DITTOE_JAVA",
                    "DITTOE_JAVA_OPTS", "DITTOE_TIMEOUT"):
            monkeypatch.delenv(var, raising=False)

        config = DittoeConfig.from_env()
        assert config.jar_path == ""
        assert config.base_dir == "./pipelines"
        assert config.java_path == "java"
        assert config.java_opts == []
        assert config.timeout == 300

    def test_validate_missing_jar(self):
        config = DittoeConfig(jar_path="", java_path="java")
        with pytest.raises(DittoeConfigError, match="jar_path is required"):
            config.validate()

    def test_validate_nonexistent_jar(self):
        config = DittoeConfig(jar_path="/nonexistent/file.jar")
        with pytest.raises(DittoeConfigError, match="JAR file not found"):
            config.validate()

    def test_validate_negative_timeout(self):
        config = DittoeConfig(jar_path="/some/path.jar", timeout=-1)
        with pytest.raises(DittoeConfigError, match="timeout must be positive"):
            config.validate()

    def test_validate_empty_base_dir(self):
        config = DittoeConfig(jar_path="/some/path.jar", base_dir="")
        with pytest.raises(DittoeConfigError, match="base_dir is required"):
            config.validate()

    def test_ensure_base_dir(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target = os.path.join(tmpdir, "new_dir", "sub")
            config = DittoeConfig(base_dir=target)
            result = config.ensure_base_dir()
            assert os.path.isdir(result)
            assert result == os.path.abspath(target)


# ============================================================================
# parse_field tests
# ============================================================================

class TestParseField:

    def test_parse_string_with_type(self):
        f = parse_field("id:integer")
        assert f.name == "id"
        assert f.data_type == DataType.INTEGER

    def test_parse_string_no_type(self):
        f = parse_field("name")
        assert f.name == "name"
        assert f.data_type == DataType.STRING

    def test_parse_string_aliases(self):
        assert parse_field("x:int").data_type == DataType.INTEGER
        assert parse_field("x:str").data_type == DataType.STRING
        assert parse_field("x:float").data_type == DataType.DOUBLE
        assert parse_field("x:bool").data_type == DataType.BOOLEAN

    def test_parse_dict(self):
        f = parse_field({"name": "price", "type": "decimal", "nullable": False})
        assert f.name == "price"
        assert f.data_type == DataType.DECIMAL
        assert f.is_nullable is False

    def test_parse_field_object(self):
        original = Field("test", DataType.UUID)
        result = parse_field(original)
        assert result is original

    def test_parse_invalid_type_raises(self):
        with pytest.raises(ValueError):
            parse_field(12345)


# ============================================================================
# ConnectionBuilder tests
# ============================================================================

class TestConnectionBuilder:

    def test_postgresql_connection(self):
        conn = (ConnectionBuilder("products")
                .postgresql("localhost", 5433, "mydb", "user", "pass")
                .schema("production")
                .fields(["id:integer", "name:string", "price:decimal"])
                .as_source()
                .build())

        assert conn.name == "products"
        assert conn.server is not None
        assert conn.server.connection_details["host"] == "localhost"
        assert conn.server.connection_details["port"] == "5433"
        assert conn.schema_name == "production"
        assert conn.resource_properties["schema"] == "production"
        assert len(conn.fields) == 3
        assert conn.fields[0].name == "id"
        assert conn.fields[0].data_type == DataType.INTEGER
        assert conn.strategy == ReadStrategy.JDBC_SELECT

    def test_mysql_source(self):
        conn = (ConnectionBuilder("users")
                .mysql("localhost", 3306, "mydb", "root", "pass")
                .fields(["id:int"])
                .as_source()
                .build())
        assert conn.strategy == ReadStrategy.JDBC_SELECT

    def test_postgresql_target(self):
        conn = (ConnectionBuilder("products")
                .postgresql("localhost", 5434, "mydb", "user", "pass")
                .fields(["id:integer", "name:string"])
                .as_target()
                .build())
        assert conn.strategy == WriteStrategy.JDBC_INSERT

    def test_explicit_read_strategy(self):
        conn = (ConnectionBuilder("data")
                .postgresql("h", 5432, "db", "u", "p")
                .fields(["id:int"])
                .read_strategy("JDBC_SELECT")
                .build())
        assert conn.strategy == ReadStrategy.JDBC_SELECT

    def test_explicit_write_strategy_fqcn(self):
        fqcn = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy"
        conn = (ConnectionBuilder("data")
                .postgresql("h", 5432, "db", "u", "p")
                .fields(["id:int"])
                .write_strategy(fqcn)
                .build())
        assert conn.strategy == fqcn

    def test_local_file_source(self):
        conn = (ConnectionBuilder("data.csv")
                .local_file("/data/imports")
                .fields(["id:int", "name:str"])
                .as_source()
                .build())
        assert conn.strategy == ReadStrategy.LOCAL_CSV

    def test_local_file_target(self):
        conn = (ConnectionBuilder("output.csv")
                .local_file("/data/exports")
                .fields(["id:int"])
                .as_target()
                .build())
        assert conn.strategy == WriteStrategy.LOCAL_CSV

    def test_s3_connection(self):
        conn = (ConnectionBuilder("data")
                .s3("mybucket", "data/file.csv", "us-east-1")
                .fields(["id:int"])
                .as_source()
                .build())
        assert conn.strategy == ReadStrategy.S3_CSV

    def test_add_field_method(self):
        conn = (ConnectionBuilder("t")
                .postgresql("h", 5432, "db", "u", "p")
                .add_field("id", DataType.INTEGER, False)
                .add_field("name", DataType.STRING)
                .as_source()
                .build())
        assert len(conn.fields) == 2

    def test_property_method(self):
        conn = (ConnectionBuilder("t")
                .postgresql("h", 5432, "db", "u", "p")
                .fields(["id:int"])
                .property("fetchSize", "1000")
                .as_source()
                .build())
        assert conn.resource_properties["fetchSize"] == "1000"

    def test_build_without_server_raises(self):
        with pytest.raises(ValueError, match="No server type configured"):
            ConnectionBuilder("test").fields(["id:int"]).build()

    def test_connection_validate_empty_name(self):
        conn = Connection()
        errors = conn.validate()
        assert any("name is required" in e for e in errors)

    def test_connection_validate_no_fields(self):
        from dittoe_sdk.models import Server, ServerType
        conn = Connection(
            name="test",
            server=Server(name="s", type=ServerType.DATABASE,
                          connection_details={"host": "h"}),
            strategy="some_strategy",
            fields=[],
        )
        errors = conn.validate()
        assert any("at least one field" in e for e in errors)

    def test_connection_validate_success(self):
        conn = (ConnectionBuilder("products")
                .postgresql("localhost", 5432, "db", "u", "p")
                .fields(["id:int"])
                .as_source()
                .build())
        errors = conn.validate()
        assert errors == []


# ============================================================================
# Default behaviors tests
# ============================================================================

class TestDefaultBehaviors:

    def test_default_behaviors_count(self):
        behaviors = default_behaviors()
        assert len(behaviors) == 4

    def test_default_behaviors_types(self):
        behaviors = default_behaviors()
        types = [b.behavior_type.value for b in behaviors]
        assert "SourceRetrieval" in types
        assert "TargetInsertUpdate" in types
        assert "Instrumentation" in types
        assert "Transformation" in types


# ============================================================================
# Dittoe facade tests (unit-level, no JAR execution)
# ============================================================================

class TestDittoeFacade:

    def test_field_mapping_same_name(self):
        """Test that same-name field mapping works correctly."""
        source_fields = [Field("id", DataType.INTEGER), Field("name", DataType.STRING)]
        target_fields = [Field("id", DataType.INTEGER), Field("name", DataType.STRING)]

        mappings = Dittoe._build_field_mappings(
            source_fields, target_fields, "same_name", None
        )
        assert len(mappings) == 2
        assert mappings[0].source_field.name == "id"
        assert mappings[0].target_field.name == "id"

    def test_field_mapping_explicit(self):
        """Test explicit field mapping with renaming."""
        source_fields = [Field("old_name", DataType.STRING)]
        target_fields = [Field("new_name", DataType.STRING)]

        mappings = Dittoe._build_field_mappings(
            source_fields, target_fields, "explicit",
            {"old_name": "new_name"}
        )
        assert len(mappings) == 1
        assert mappings[0].source_field.name == "old_name"
        assert mappings[0].target_field.name == "new_name"

    def test_field_mapping_partial_match(self):
        """Only fields present in both source and target get mapped."""
        source_fields = [Field("id", DataType.INTEGER), Field("extra", DataType.STRING)]
        target_fields = [Field("id", DataType.INTEGER)]

        mappings = Dittoe._build_field_mappings(
            source_fields, target_fields, "same_name", None
        )
        assert len(mappings) == 1
        assert mappings[0].source_field.name == "id"

    def test_resolve_behaviors_default(self):
        behaviors = Dittoe._resolve_behaviors(None)
        assert len(behaviors) == 4

    def test_resolve_behaviors_explicit_default(self):
        behaviors = Dittoe._resolve_behaviors(["default"])
        assert len(behaviors) == 4

    def test_resolve_behaviors_custom(self):
        custom = Behavior(name="custom", behavior_type=__import__(
            'dittoe_sdk.models', fromlist=['BehaviorType']).BehaviorType.ENCRYPTION)
        behaviors = Dittoe._resolve_behaviors([custom])
        assert len(behaviors) == 1
        assert behaviors[0].name == "custom"

    def test_migrate_validates_connections(self, tmp_path):
        """migrate() should raise if connections are invalid."""
        # Create a fake JAR so DittoeExecutor init doesn't fail
        fake_jar = tmp_path / "fake.jar"
        fake_jar.touch()
        config = DittoeConfig(jar_path=str(fake_jar))
        dittoe = Dittoe(config)

        bad_source = Connection()  # empty = invalid
        bad_target = Connection()

        with pytest.raises(DittoeConfigError, match="Connection validation failed"):
            dittoe.migrate(bad_source, bad_target)


# ============================================================================
# End-to-end pipeline construction test (no JAR needed)
# ============================================================================

class TestFacadeEndToEnd:

    def test_full_pipeline_construction_via_facade(self):
        """Verify facade builds the same structures as manual builders."""
        source = (ConnectionBuilder("products")
                  .postgresql("src-host", 5433, "srcdb", "user", "pass")
                  .schema("production")
                  .fields(["id:integer", "name:string", "price:decimal"])
                  .as_source()
                  .build())

        target = (ConnectionBuilder("products")
                  .postgresql("tgt-host", 5434, "tgtdb", "user", "pass")
                  .schema("production")
                  .fields(["id:integer", "name:string", "price:decimal"])
                  .as_target()
                  .build())

        # Validate connections
        assert source.validate() == []
        assert target.validate() == []

        # Verify strategy auto-assignment
        assert source.strategy == ReadStrategy.JDBC_SELECT
        assert target.strategy == WriteStrategy.JDBC_INSERT

        # Verify field parsing
        assert source.fields[0].name == "id"
        assert source.fields[0].data_type == DataType.INTEGER
        assert source.fields[2].name == "price"
        assert source.fields[2].data_type == DataType.DECIMAL

        # Verify server construction
        assert source.server.connection_details["host"] == "src-host"
        assert target.server.connection_details["host"] == "tgt-host"

    def test_csv_to_pg_construction(self):
        """Verify CSV-to-PostgreSQL pipeline can be built via facade."""
        source = (ConnectionBuilder("data.csv")
                  .local_file("/data/imports")
                  .fields(["id:integer", "name:string"])
                  .as_source()
                  .build())

        target = (ConnectionBuilder("users")
                  .postgresql("db-host", 5432, "mydb", "user", "pass")
                  .schema("public")
                  .fields(["id:integer", "name:string"])
                  .as_target()
                  .build())

        assert source.validate() == []
        assert target.validate() == []
        assert source.strategy == ReadStrategy.LOCAL_CSV
        assert target.strategy == WriteStrategy.JDBC_INSERT
