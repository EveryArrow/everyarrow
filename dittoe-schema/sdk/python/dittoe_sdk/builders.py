# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

"""
Builder classes for fluent construction of DITTOE pipeline components.
Wraps all servers (19), strategies (45), behaviors (22), and template types (16).
"""

from __future__ import annotations
from typing import Optional, Dict, List
import uuid as uuid_lib

from .models import (
    DataType, BehaviorType, ServerType, TemplateType, StageType,
    ProcessExecutionType, DatabaseType, FileType, APIType,
    Field, ExpressionField, SourceField, Server, Resource,
    Behavior, StagedBehavior, SourceToTargetMapping, DQRule,
    ResourceDependencyMapping, PipelineMetadata,
    ReadStrategy, WriteStrategy,
)


# ============================================================================
# FieldBuilder
# ============================================================================

class FieldBuilder:
    """Fluent builder for Field objects."""

    def __init__(self, name: str):
        self._name = name
        self._data_type = DataType.STRING
        self._is_nullable = True
        self._description: Optional[str] = None
        self._expression: Optional[str] = None
        self._is_expression_field = False
        self._is_source_field = False

    # Data type shortcuts
    def data_type(self, dt: DataType) -> FieldBuilder:
        self._data_type = dt
        return self

    def string(self) -> FieldBuilder: return self.data_type(DataType.STRING)
    def integer(self) -> FieldBuilder: return self.data_type(DataType.INTEGER)
    def double(self) -> FieldBuilder: return self.data_type(DataType.DOUBLE)
    def decimal(self) -> FieldBuilder: return self.data_type(DataType.DECIMAL)
    def boolean(self) -> FieldBuilder: return self.data_type(DataType.BOOLEAN)
    def date(self) -> FieldBuilder: return self.data_type(DataType.DATE)
    def datetime(self) -> FieldBuilder: return self.data_type(DataType.DATETIME)
    def time(self) -> FieldBuilder: return self.data_type(DataType.TIME)
    def uuid(self) -> FieldBuilder: return self.data_type(DataType.UUID)
    def time_with_tz(self) -> FieldBuilder: return self.data_type(DataType.TIME_WITH_TIME_ZONE)
    def datetime_with_tz(self) -> FieldBuilder: return self.data_type(DataType.DATETIME_WITH_TIME_ZONE)
    def json(self) -> FieldBuilder: return self.data_type(DataType.JSON)
    def jsonb(self) -> FieldBuilder: return self.data_type(DataType.JSONB)
    def array(self) -> FieldBuilder: return self.data_type(DataType.ARRAY)
    def number(self) -> FieldBuilder: return self.data_type(DataType.NUMBER)
    def bytes(self) -> FieldBuilder: return self.data_type(DataType.BYTES)
    def xml(self) -> FieldBuilder: return self.data_type(DataType.XML)

    def nullable(self, value: bool = True) -> FieldBuilder:
        self._is_nullable = value
        return self

    def required(self) -> FieldBuilder:
        return self.nullable(False)

    def description(self, desc: str) -> FieldBuilder:
        self._description = desc
        return self

    def expression(self, expr: str) -> FieldBuilder:
        """Mark this as an ExpressionField with a DEAL expression."""
        self._expression = expr
        self._is_expression_field = True
        return self

    def source_expression(self, expr: str) -> FieldBuilder:
        """Mark this as a SourceField with an expression."""
        self._expression = expr
        self._is_source_field = True
        return self

    def build(self) -> Field:
        if self._is_expression_field:
            return ExpressionField(
                name=self._name, data_type=self._data_type,
                is_nullable=self._is_nullable, description=self._description,
                expression=self._expression or "",
            )
        elif self._is_source_field:
            return SourceField(
                name=self._name, data_type=self._data_type,
                is_nullable=self._is_nullable, description=self._description,
                expression=self._expression or "",
            )
        return Field(
            name=self._name, data_type=self._data_type,
            is_nullable=self._is_nullable, description=self._description,
        )


# ============================================================================
# ServerBuilder
# ============================================================================

class ServerBuilder:
    """Fluent builder for Server objects. Supports all 19 server types."""

    def __init__(self, name: str):
        self._id = 0
        self._name = name
        self._server_type = ServerType.DATABASE
        self._connection_details: Dict[str, str] = {}
        self._additional_properties: Dict[str, str] = {}

    def id(self, server_id: int) -> ServerBuilder:
        self._id = server_id
        return self

    def additional_property(self, key: str, value: str) -> ServerBuilder:
        self._additional_properties[key] = value
        return self

    def connection_detail(self, key: str, value: str) -> ServerBuilder:
        self._connection_details[key] = value
        return self

    # --- Database servers ---
    def _database(self, db_type: DatabaseType, host: str, port: int,
                  database: str, user: str, password: str) -> ServerBuilder:
        self._server_type = ServerType.DATABASE
        self._connection_details = {
            "databaseType": db_type.value,
            "host": host, "port": str(port),
            "database": database, "user": user, "password": password,
        }
        return self

    def postgresql(self, host: str, port: int, database: str,
                   user: str, password: str) -> ServerBuilder:
        return self._database(DatabaseType.POSTGRESQL, host, port, database, user, password)

    def mysql(self, host: str, port: int, database: str,
              user: str, password: str) -> ServerBuilder:
        return self._database(DatabaseType.MYSQL, host, port, database, user, password)

    def mssql(self, host: str, port: int, database: str,
              user: str, password: str) -> ServerBuilder:
        return self._database(DatabaseType.MSSQL, host, port, database, user, password)

    def snowflake(self, host: str, port: int, database: str,
                  user: str, password: str) -> ServerBuilder:
        return self._database(DatabaseType.SNOWFLAKE, host, port, database, user, password)

    def sqlite(self, path: str) -> ServerBuilder:
        self._server_type = ServerType.DATABASE
        self._connection_details = {"databaseType": DatabaseType.SQLITE.value, "path": path}
        return self

    # --- File servers ---
    def local_file(self, path: str) -> ServerBuilder:
        self._server_type = ServerType.FILE
        self._connection_details = {"fileType": FileType.LOCAL.value, "path": path}
        return self

    def _cloud_file(self, file_type: FileType, **kwargs: str) -> ServerBuilder:
        self._server_type = ServerType.FILE
        self._connection_details = {"fileType": file_type.value, **kwargs}
        return self

    def s3(self, bucket: str, region: str, access_key: str,
           secret_key: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.S3, bucket=bucket, region=region,
                                accessKey=access_key, secretKey=secret_key, path=path)

    def gcs(self, bucket: str, project_id: str,
            credentials_file: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.GCS, bucket=bucket, projectId=project_id,
                                credentialsFile=credentials_file, path=path)

    def azure(self, container: str, account_name: str,
              account_key: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.AZURE, container=container, accountName=account_name,
                                accountKey=account_key, path=path)

    def dropbox(self, access_token: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.DROPBOX, accessToken=access_token, path=path)

    def sftp(self, host: str, port: int, path: str,
             user: str, password: str) -> ServerBuilder:
        return self._cloud_file(FileType.SFTP, host=host, port=str(port),
                                path=path, user=user, password=password)

    def ftp(self, host: str, port: int, path: str,
            user: str, password: str) -> ServerBuilder:
        return self._cloud_file(FileType.FTP, host=host, port=str(port),
                                path=path, user=user, password=password)

    def ibm(self, endpoint: str, api_key: str,
            bucket: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.IBM, endpoint=endpoint,
                                apiKey=api_key, bucket=bucket, path=path)

    def oracle_cloud(self, namespace: str, bucket: str,
                     compartment_id: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.ORACLE, namespace=namespace, bucket=bucket,
                                compartmentId=compartment_id, path=path)

    def digital_ocean(self, space: str, region: str, access_key: str,
                      secret_key: str, path: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.DIGITAL_OCEAN, space=space, region=region,
                                accessKey=access_key, secretKey=secret_key, path=path)

    def google_drive(self, credentials_file: str,
                     folder_id: str = "") -> ServerBuilder:
        return self._cloud_file(FileType.GOOGLE_DRIVE, credentialsFile=credentials_file,
                                folderId=folder_id)

    # --- API servers ---
    def bigquery(self, project_id: str, dataset: str,
                 credentials_file: str) -> ServerBuilder:
        self._server_type = ServerType.API
        self._connection_details = {
            "apiType": APIType.BIGQUERY.value,
            "projectId": project_id, "dataset": dataset,
            "credentialsFile": credentials_file,
        }
        return self

    def salesforce(self, login_url: str, username: str,
                   password: str, security_token: str) -> ServerBuilder:
        self._server_type = ServerType.API
        self._connection_details = {
            "apiType": APIType.SALESFORCE.value,
            "loginUrl": login_url, "username": username,
            "password": password, "securityToken": security_token,
        }
        return self

    def build(self) -> Server:
        return Server(
            id=self._id, name=self._name, type=self._server_type,
            connection_details=self._connection_details,
            additional_properties=self._additional_properties,
        )


# ============================================================================
# ResourceBuilder
# ============================================================================

class ResourceBuilder:
    """Fluent builder for Resource objects. Supports all 45 strategies."""

    def __init__(self, resource_name: str):
        self._resource_id = 1
        self._resource_name = resource_name
        self._server: Optional[Server] = None
        self._fields: List[Field] = []
        self._strategy = ""
        self._resource_properties: Dict[str, str] = {}

    def id(self, resource_id: int) -> ResourceBuilder:
        self._resource_id = resource_id
        return self

    def server(self, server: Server) -> ResourceBuilder:
        self._server = server
        return self

    def fields(self, *fields: Field) -> ResourceBuilder:
        self._fields.extend(fields)
        return self

    def add_field(self, name: str, data_type: DataType = DataType.STRING,
                  nullable: bool = True, description: Optional[str] = None) -> ResourceBuilder:
        self._fields.append(Field(name, data_type, nullable, description))
        return self

    def add_expression_field(self, name: str, expression: str,
                             data_type: DataType = DataType.STRING,
                             nullable: bool = True) -> ResourceBuilder:
        self._fields.append(ExpressionField(name, data_type, nullable, expression=expression))
        return self

    def schema(self, schema_name: str) -> ResourceBuilder:
        self._resource_properties["schema"] = schema_name
        return self

    def property(self, key: str, value: str) -> ResourceBuilder:
        self._resource_properties[key] = value
        return self

    def strategy(self, strategy_class: str) -> ResourceBuilder:
        """Set strategy by fully-qualified class name. See ReadStrategy / WriteStrategy constants."""
        self._strategy = strategy_class
        return self

    # --- Database read ---
    def jdbc_read(self) -> ResourceBuilder:
        """SelectStrategy for JDBC database reads."""
        self._strategy = ReadStrategy.JDBC_SELECT
        return self

    # --- Database write: generic JDBC ---
    def insert(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.JDBC_INSERT
        return self

    def replace(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.JDBC_REPLACE
        return self

    # --- Database write: PostgreSQL ---
    def pg_upsert(self, conflict_constraint: str) -> ResourceBuilder:
        self._strategy = WriteStrategy.PG_UPSERT
        self._resource_properties["upsertConflictConstraint"] = conflict_constraint
        return self

    def pg_insert_do_nothing(self, conflict_constraint: str) -> ResourceBuilder:
        self._strategy = WriteStrategy.PG_INSERT_DO_NOTHING
        self._resource_properties["upsertConflictConstraint"] = conflict_constraint
        return self

    def pg_copy(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.PG_COPY
        return self

    def pg_copy_or_auto_id(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.PG_COPY_OR_AUTO_ID
        return self

    def pg_insert_or_update(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.PG_INSERT_OR_UPDATE
        return self

    # --- Database write: MSSQL ---
    def mssql_merge(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.MSSQL_MERGE
        return self

    # --- Database write: SQLite ---
    def sqlite_upsert(self, conflict_constraint: str) -> ResourceBuilder:
        self._strategy = WriteStrategy.SQLITE_UPSERT
        self._resource_properties["upsertConflictConstraint"] = conflict_constraint
        return self

    # --- File read ---
    def csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.LOCAL_CSV
        return self

    def json_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.LOCAL_JSON
        return self

    def s3_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.S3_CSV
        return self

    def gcs_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.GCS_CSV
        return self

    def azure_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.AZURE_CSV
        return self

    def dropbox_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.DROPBOX_CSV
        return self

    def sftp_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.SFTP_CSV
        return self

    def ftp_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.FTP_CSV
        return self

    def ibm_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.IBM_CSV
        return self

    def oracle_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.ORACLE_CSV
        return self

    def digital_ocean_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.DIGITAL_OCEAN_CSV
        return self

    def google_drive_csv_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.GOOGLE_DRIVE_CSV
        return self

    # --- File write ---
    def csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.LOCAL_CSV
        return self

    def json_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.LOCAL_JSON
        return self

    def s3_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.S3_CSV
        return self

    def gcs_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.GCS_CSV
        return self

    def azure_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.AZURE_CSV
        return self

    def dropbox_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.DROPBOX_CSV
        return self

    def sftp_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.SFTP_CSV
        return self

    def ftp_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.FTP_CSV
        return self

    def ibm_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.IBM_CSV
        return self

    def oracle_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.ORACLE_CSV
        return self

    def digital_ocean_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.DIGITAL_OCEAN_CSV
        return self

    def google_drive_csv_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.GOOGLE_DRIVE_CSV
        return self

    # --- API read ---
    def bigquery_query_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.BIGQUERY_QUERY
        return self

    def bigquery_avro_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.BIGQUERY_AVRO
        return self

    def salesforce_read(self) -> ResourceBuilder:
        self._strategy = ReadStrategy.SALESFORCE
        return self

    # --- API write ---
    def bigquery_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.BIGQUERY
        return self

    def salesforce_write(self) -> ResourceBuilder:
        self._strategy = WriteStrategy.SALESFORCE
        return self

    def build(self) -> Resource:
        if self._server is None:
            raise ValueError("Server must be set")
        return Resource(
            resource_id=self._resource_id,
            resource_name=self._resource_name,
            server=self._server,
            strategy=self._strategy,
            resource_properties=self._resource_properties,
            fields=self._fields,
        )


# ============================================================================
# BehaviorBuilder
# ============================================================================

class BehaviorBuilder:
    """Fluent builder for Behavior objects. Supports all 22 behavior types."""

    def __init__(self, name: str = ""):
        self._name = name
        self._behavior_type = BehaviorType.SOURCE_RETRIEVAL
        self._description = ""
        self._properties: Dict[str, str] = {}
        # StagedBehavior fields
        self._is_staged = False
        self._plugin_reference = ""
        self._order = 0
        self._stage_type = StageType.PROCESS

    def _set_type(self, bt: BehaviorType, default_name: str, desc: str) -> BehaviorBuilder:
        self._behavior_type = bt
        if not self._name:
            self._name = default_name
        self._description = desc
        return self

    # All 22 behavior types
    def formatting(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.FORMATTING, "Formatting", "Data formatting behavior")

    def source_retrieval(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.SOURCE_RETRIEVAL, "SourceRetrieval",
                              "Retrieve data from the source resource")

    def target_insert_update(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.TARGET_INSERT_UPDATE, "TargetInsertUpdate",
                              "Write data to the target resource")

    def transformation(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.TRANSFORMATION, "Transformation",
                              "Apply transformation using DEAL")

    def encryption(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.ENCRYPTION, "Encryption", "Data encryption behavior")

    def logging(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.LOGGING, "Logging", "Logging behavior")

    def instrumentation(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.INSTRUMENTATION, "Instrumentation",
                              "Pipeline instrumentation")

    def lineage_tracking(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.LINEAGE_TRACKING, "LineageTracking",
                              "Data lineage tracking")

    def key_generation(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.KEY_GENERATION, "KeyGeneration", "Key generation")

    def masking(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.MASKING, "Masking", "Data masking")

    def deduplication(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.DEDUPLICATION, "Deduplication", "Data deduplication")

    def schema_evolution(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.SCHEMA_EVOLUTION, "SchemaEvolution",
                              "Schema evolution handling")

    def retry(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.RETRY, "Retry", "Retry on failure")

    def schema_validation(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.SCHEMA_VALIDATION, "SchemaValidation",
                              "Validate schema before processing")

    def dq_rule_checks(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.DQ_RULE_CHECKS, "DQRuleChecks",
                              "Data quality rule checks")

    def archive(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.ARCHIVE, "Archive", "Archive data")

    def micro_batch(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.MICRO_BATCH, "MicroBatch", "Micro-batch processing")

    def data_science_algorithms(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.DATA_SCIENCE_ALGORITHMS, "DataScienceAlgorithms",
                              "Apply data science algorithms")

    def custom_code(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.CUSTOM_CODE, "CustomCode", "Custom code execution")

    def compression(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.COMPRESSION, "Compression", "Data compression")

    def external_widget(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.EXTERNAL_WIDGET, "ExternalWidget",
                              "External widget behavior")

    def cache(self) -> BehaviorBuilder:
        return self._set_type(BehaviorType.CACHE, "Cache", "Caching behavior")

    # Common properties
    def error_threshold(self, threshold: int) -> BehaviorBuilder:
        self._properties["errorCountThreshold"] = str(threshold)
        return self

    def property(self, key: str, value: str) -> BehaviorBuilder:
        self._properties[key] = value
        return self

    # StagedBehavior support
    def staged(self, stage: StageType, order: int = 0,
               plugin_reference: str = "") -> BehaviorBuilder:
        self._is_staged = True
        self._stage_type = stage
        self._order = order
        self._plugin_reference = plugin_reference
        return self

    def build(self) -> Behavior:
        if self._is_staged:
            return StagedBehavior(
                name=self._name, behavior_type=self._behavior_type,
                properties=self._properties, description=self._description,
                plugin_reference=self._plugin_reference,
                order=self._order, stage_type=self._stage_type,
            )
        return Behavior(
            name=self._name, behavior_type=self._behavior_type,
            properties=self._properties, description=self._description,
        )


# ============================================================================
# DQRuleBuilder
# ============================================================================

class DQRuleBuilder:
    """Fluent builder for DQRule objects."""

    def __init__(self, name: str):
        self._name = name
        self._expression = ""

    def expression(self, expr: str) -> DQRuleBuilder:
        self._expression = expr
        return self

    def build(self) -> DQRule:
        return DQRule(rule_name=self._name, rule_expression=self._expression)


# ============================================================================
# PipelineBuilder
# ============================================================================

class PipelineBuilder:
    """Fluent builder for complete PipelineMetadata (PMD). Supports all 16 template types."""

    def __init__(self, short_name: str):
        self._id = str(uuid_lib.uuid4())
        self._short_name = short_name
        self._description = ""
        self._template_type = TemplateType.DATABASE_TO_DATABASE
        self._source_resource: Optional[Resource] = None
        self._target_resource: Optional[Resource] = None
        self._field_mappings: List[SourceToTargetMapping] = []
        self._behaviors: List[Behavior] = []
        self._global_properties: Dict[str, str] = {}
        self._create_date: Optional[str] = None
        self._created_by: Optional[str] = None
        self._last_updated_by: Optional[str] = None
        self._last_updated_date: Optional[str] = None

    def id(self, pipeline_id: str) -> PipelineBuilder:
        self._id = pipeline_id
        return self

    def description(self, desc: str) -> PipelineBuilder:
        self._description = desc
        return self

    def created_by(self, author: str) -> PipelineBuilder:
        self._created_by = author
        return self

    def create_date(self, dt: str) -> PipelineBuilder:
        self._create_date = dt
        return self

    def last_updated_by(self, author: str) -> PipelineBuilder:
        self._last_updated_by = author
        return self

    def last_updated_date(self, dt: str) -> PipelineBuilder:
        self._last_updated_date = dt
        return self

    # Template type - all 16
    def template_type(self, tt: TemplateType) -> PipelineBuilder:
        self._template_type = tt
        return self

    def file_to_file(self) -> PipelineBuilder: return self.template_type(TemplateType.FILE_TO_FILE)
    def file_to_database(self) -> PipelineBuilder: return self.template_type(TemplateType.FILE_TO_DATABASE)
    def file_to_stream(self) -> PipelineBuilder: return self.template_type(TemplateType.FILE_TO_STREAM)
    def file_to_api(self) -> PipelineBuilder: return self.template_type(TemplateType.FILE_TO_API)
    def database_to_database(self) -> PipelineBuilder: return self.template_type(TemplateType.DATABASE_TO_DATABASE)
    def database_to_file(self) -> PipelineBuilder: return self.template_type(TemplateType.DATABASE_TO_FILE)
    def database_to_stream(self) -> PipelineBuilder: return self.template_type(TemplateType.DATABASE_TO_STREAM)
    def database_to_api(self) -> PipelineBuilder: return self.template_type(TemplateType.DATABASE_TO_API)
    def stream_to_stream(self) -> PipelineBuilder: return self.template_type(TemplateType.STREAM_TO_STREAM)
    def stream_to_file(self) -> PipelineBuilder: return self.template_type(TemplateType.STREAM_TO_FILE)
    def stream_to_database(self) -> PipelineBuilder: return self.template_type(TemplateType.STREAM_TO_DATABASE)
    def stream_to_api(self) -> PipelineBuilder: return self.template_type(TemplateType.STREAM_TO_API)
    def api_to_api(self) -> PipelineBuilder: return self.template_type(TemplateType.API_TO_API)
    def api_to_file(self) -> PipelineBuilder: return self.template_type(TemplateType.API_TO_FILE)
    def api_to_database(self) -> PipelineBuilder: return self.template_type(TemplateType.API_TO_DATABASE)
    def api_to_stream(self) -> PipelineBuilder: return self.template_type(TemplateType.API_TO_STREAM)

    # Resources
    def source(self, resource: Resource) -> PipelineBuilder:
        self._source_resource = resource
        return self

    def target(self, resource: Resource) -> PipelineBuilder:
        self._target_resource = resource
        return self

    # Field mapping
    def map_field(self, source: Field, target: Field) -> PipelineBuilder:
        self._field_mappings.append(SourceToTargetMapping(source, target))
        return self

    def map_fields_same_name(self) -> PipelineBuilder:
        """Auto-map fields with matching names from source to target."""
        if not self._source_resource or not self._target_resource:
            raise ValueError("Source and target resources must be set first")
        target_fields_by_name = {f.name: f for f in self._target_resource.fields}
        for src_field in self._source_resource.fields:
            if src_field.name in target_fields_by_name:
                self._field_mappings.append(
                    SourceToTargetMapping(src_field, target_fields_by_name[src_field.name])
                )
        return self

    # Behaviors
    def behavior(self, behavior: Behavior) -> PipelineBuilder:
        self._behaviors.append(behavior)
        return self

    def behaviors(self, *behaviors: Behavior) -> PipelineBuilder:
        self._behaviors.extend(behaviors)
        return self

    def with_default_behaviors(self) -> PipelineBuilder:
        """Add standard behaviors: SourceRetrieval, TargetInsertUpdate, Instrumentation, Transformation."""
        self._behaviors = [
            BehaviorBuilder().source_retrieval().error_threshold(1).build(),
            BehaviorBuilder().target_insert_update().error_threshold(1).build(),
            BehaviorBuilder().instrumentation().build(),
            BehaviorBuilder().transformation().build(),
        ]
        return self

    # Global properties
    def global_property(self, key: str, value: str) -> PipelineBuilder:
        self._global_properties[key] = value
        return self

    def sync_fields(self, enabled: bool = True) -> PipelineBuilder:
        self._global_properties["syncFields"] = str(enabled).lower()
        return self

    def run_test(self, enabled: bool = True) -> PipelineBuilder:
        self._global_properties["runTest"] = str(enabled).lower()
        return self

    def build(self) -> PipelineMetadata:
        if not self._source_resource:
            raise ValueError("Source resource must be set")
        if not self._target_resource:
            raise ValueError("Target resource must be set")
        return PipelineMetadata(
            id=self._id, short_name=self._short_name,
            description=self._description,
            global_properties=self._global_properties,
            behaviors=self._behaviors,
            create_date=self._create_date, created_by=self._created_by,
            last_updated_by=self._last_updated_by,
            last_updated_date=self._last_updated_date,
            source_resource=self._source_resource,
            target_resource=self._target_resource,
            template_type=self._template_type,
            field_mappings=self._field_mappings,
        )
