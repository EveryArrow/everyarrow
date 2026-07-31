# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

"""
DITTOE SDK - Comprehensive data models mirroring the Java dittoe-schema.

Covers all 6 enums, all 12 schema classes with full field coverage.
These models serialize to JSON that the DITTOE Java JAR can consume.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, Dict, List
from datetime import date
import json
import os


# ============================================================================
# Enums (6)
# ============================================================================

class DataType(Enum):
    """Type of the data of a Field. Mirrors org.everyarrow.dittoe.schema.DataType."""
    STRING = "String"
    INTEGER = "Integer"
    DOUBLE = "Double"
    DECIMAL = "Decimal"
    BOOLEAN = "Boolean"
    DATE = "Date"
    DATETIME = "DateTime"
    TIME = "Time"
    UUID = "UUID"
    TIME_WITH_TIME_ZONE = "TimeWithTimeZone"
    DATETIME_WITH_TIME_ZONE = "DateTimeWithTimeZone"
    JSON = "JSON"
    JSONB = "JSONB"
    ARRAY = "Array"
    NUMBER = "Number"
    BYTES = "Bytes"
    XML = "XML"


class BehaviorType(Enum):
    """Behavior types representing pipeline activities. Mirrors org.everyarrow.dittoe.schema.BehaviorType."""
    FORMATTING = "Formatting"
    SOURCE_RETRIEVAL = "SourceRetrieval"
    TARGET_INSERT_UPDATE = "TargetInsertUpdate"
    TRANSFORMATION = "Transformation"
    ENCRYPTION = "Encryption"
    LOGGING = "Logging"
    INSTRUMENTATION = "Instrumentation"
    LINEAGE_TRACKING = "lineageTracking"
    KEY_GENERATION = "KeyGeneration"
    MASKING = "Masking"
    DEDUPLICATION = "Deduplication"
    SCHEMA_EVOLUTION = "SchemaEvolution"
    RETRY = "Retry"
    SCHEMA_VALIDATION = "SchemaValidation"
    DQ_RULE_CHECKS = "DQRuleChecks"
    ARCHIVE = "Archive"
    MICRO_BATCH = "MicroBatch"
    DATA_SCIENCE_ALGORITHMS = "DataScienceAlgorithms"
    CUSTOM_CODE = "CustomCode"
    COMPRESSION = "Compression"
    EXTERNAL_WIDGET = "ExternalWidget"
    CACHE = "Cache"


class ServerType(Enum):
    """Server type categories. Mirrors org.everyarrow.dittoe.schema.ServerType."""
    STREAM = "Stream"
    API = "API"
    FILE = "File"
    DATABASE = "Database"


class TemplateType(Enum):
    """Pipeline template types (FADS combinations). Mirrors org.everyarrow.dittoe.schema.TemplateType."""
    FILE_TO_FILE = "FileToFile"
    FILE_TO_DATABASE = "FileToDatabase"
    FILE_TO_STREAM = "FileToStream"
    FILE_TO_API = "FileToAPI"
    DATABASE_TO_DATABASE = "DatabaseToDatabase"
    DATABASE_TO_FILE = "DatabaseToFile"
    DATABASE_TO_STREAM = "DatabaseToStream"
    DATABASE_TO_API = "DatabaseToAPI"
    STREAM_TO_STREAM = "StreamToStream"
    STREAM_TO_FILE = "StreamToFile"
    STREAM_TO_DATABASE = "StreamToDatabase"
    STREAM_TO_API = "StreamToAPI"
    API_TO_API = "APIToAPI"
    API_TO_FILE = "APIToFile"
    API_TO_DATABASE = "APIToDatabase"
    API_TO_STREAM = "APIToStream"


class StageType(Enum):
    """BAPPTE pipeline stages. Mirrors org.everyarrow.dittoe.schema.StageType."""
    BEGIN = "Begin"
    ACQUIRE = "Acquire"
    PROCESS = "Process"
    PACKAGE = "Package"
    TRANSMIT = "Transmit"
    END = "End"


class ProcessExecutionType(Enum):
    """How behaviors should be executed. Mirrors org.everyarrow.dittoe.schema.ProcessExecutionType."""
    SYNC = "SYNC"
    ASYNC = "ASYNC"
    QUEUED = "QUEUED"
    JOINED = "JOINED"


class DatabaseType(Enum):
    """Database server subtypes used in connectionDetails."""
    POSTGRESQL = "PostgreSQLServer"
    MYSQL = "MySQLServer"
    MSSQL = "MSSQLServer"
    SQLITE = "SQLiteServer"
    SNOWFLAKE = "SnowflakeServer"


class FileType(Enum):
    """File server subtypes used in connectionDetails."""
    LOCAL = "LocalFileServer"
    S3 = "S3Server"
    GCS = "GCSServer"
    AZURE = "AzureServer"
    DROPBOX = "DropboxServer"
    SFTP = "SFTPServer"
    FTP = "FTPServer"
    IBM = "IBMServer"
    ORACLE = "OracleServer"
    DIGITAL_OCEAN = "DigitalOceanServer"
    GOOGLE_DRIVE = "GoogleDriveServer"


class APIType(Enum):
    """API server subtypes used in connectionDetails."""
    BIGQUERY = "BigQueryServer"
    SALESFORCE = "SalesforceServer"


# ============================================================================
# Strategy constants
# ============================================================================

class ReadStrategy:
    """Fully-qualified Java class names for read strategies."""
    # Database
    JDBC_SELECT = "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy"

    # Local file
    LOCAL_CSV = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.CSVReadStrategy"
    LOCAL_JSON = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.JSONReadStrategy"

    # Cloud file
    S3_CSV = "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.CSVReadStrategy"
    GCS_CSV = "com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy.CSVReadStrategy"
    AZURE_CSV = "com.everyarrow.dittoe.io.file.servers.azure.reader.strategy.CSVReadStrategy"
    DROPBOX_CSV = "com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy.CSVReadStrategy"
    SFTP_CSV = "com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy.CSVReadStrategy"
    FTP_CSV = "com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy.CSVReadStrategy"
    IBM_CSV = "com.everyarrow.dittoe.io.file.servers.ibm.reader.strategy.CSVReadStrategy"
    ORACLE_CSV = "com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy.CSVReadStrategy"
    DIGITAL_OCEAN_CSV = "com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy.CSVReadStrategy"
    GOOGLE_DRIVE_CSV = "com.everyarrow.dittoe.io.file.servers.googledrive.reader.strategy.CSVReadStrategy"

    # API
    BIGQUERY_QUERY = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.QueryStrategy"
    BIGQUERY_AVRO = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.AvroStrategy"
    SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI"


class WriteStrategy:
    """Fully-qualified Java class names for write strategies."""
    # Database - generic JDBC
    JDBC_INSERT = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy"
    JDBC_REPLACE = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.ReplaceStrategy"

    # Database - PostgreSQL specific
    PG_UPSERT = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy"
    PG_INSERT_DO_NOTHING = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertDoNothingOnConflictStrategy"
    PG_COPY = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy"
    PG_COPY_OR_AUTO_ID = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyOrAutoIdStrategy"
    PG_INSERT_OR_UPDATE = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertOrUpdateStrategy"

    # Database - MSSQL specific
    MSSQL_MERGE = "com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy"

    # Database - SQLite specific
    SQLITE_UPSERT = "com.everyarrow.dittoe.io.database.servers.sqlite.writer.strategy.UpsertOnConflictStrategy"

    # Local file
    LOCAL_CSV = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.CSVWriteStrategy"
    LOCAL_JSON = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.JSONWriteStrategy"

    # Cloud file
    S3_CSV = "com.everyarrow.dittoe.io.file.servers.s3.writer.strategy.CSVWriteStrategy"
    GCS_CSV = "com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy.CSVWriteStrategy"
    AZURE_CSV = "com.everyarrow.dittoe.io.file.servers.azure.writer.strategy.CSVWriteStrategy"
    DROPBOX_CSV = "com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy.CSVWriteStrategy"
    SFTP_CSV = "com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy.CSVWriteStrategy"
    FTP_CSV = "com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy.CSVWriteStrategy"
    IBM_CSV = "com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy.CSVWriteStrategy"
    ORACLE_CSV = "com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy.CSVWriteStrategy"
    DIGITAL_OCEAN_CSV = "com.everyarrow.dittoe.io.file.servers.digitalocean.writer.strategy.CSVWriteStrategy"
    GOOGLE_DRIVE_CSV = "com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy.CSVWriteStrategy"

    # API
    BIGQUERY = "com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.DataStoreStrategy"
    SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI"


# ============================================================================
# Data classes (12)
# ============================================================================

@dataclass
class Field:
    """Represents a key that points to a data location in a resource.
    Mirrors org.everyarrow.dittoe.schema.Field.
    """
    name: str
    data_type: DataType = DataType.STRING
    is_nullable: bool = False
    description: Optional[str] = None

    def to_dict(self) -> dict:
        d = {
            "name": self.name,
            "dataType": self.data_type.value,
            "isNullable": self.is_nullable,
        }
        if self.description is not None:
            d["description"] = self.description
        return d


@dataclass
class ExpressionField(Field):
    """A field with a DEAL expression for computed values.
    Mirrors org.everyarrow.dittoe.schema.ExpressionField.
    """
    expression: str = ""

    def to_dict(self) -> dict:
        d = super().to_dict()
        d["expression"] = self.expression
        return d


@dataclass
class SourceField(Field):
    """A source field with an expression.
    Mirrors org.everyarrow.dittoe.schema.SourceField.
    """
    expression: str = ""

    def to_dict(self) -> dict:
        d = super().to_dict()
        d["expression"] = self.expression
        return d


@dataclass
class Server:
    """Represents a server (Database, File, API, Stream).
    Mirrors org.everyarrow.dittoe.schema.Server.
    """
    id: int = 0
    name: str = ""
    type: ServerType = ServerType.DATABASE
    connection_details: Dict[str, str] = field(default_factory=dict)
    additional_properties: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "type": self.type.value,
            "connectionDetails": self.connection_details,
            "additionalProperties": self.additional_properties,
        }


@dataclass
class Resource:
    """Represents a source or target data resource.
    Mirrors org.everyarrow.dittoe.schema.Resource.
    """
    resource_id: int = 1
    resource_name: str = ""
    server: Optional[Server] = None
    strategy: str = ""
    resource_properties: Dict[str, str] = field(default_factory=dict)
    fields: List[Field] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "resourceID": self.resource_id,
            "resourceName": self.resource_name,
            "server": self.server.to_dict() if self.server else {},
            "strategy": self.strategy,
            "resourceProperties": self.resource_properties,
            "fields": [f.to_dict() for f in self.fields],
        }


@dataclass
class DQRule:
    """Data quality rule. Data is inserted only when it passes the rule.
    Mirrors org.everyarrow.dittoe.schema.DQRule.
    """
    rule_name: str = ""
    rule_expression: str = ""

    def to_dict(self) -> dict:
        return {
            "ruleName": self.rule_name,
            "ruleExpression": self.rule_expression,
        }


@dataclass
class Behavior:
    """Represents a pipeline behavior/activity.
    Mirrors org.everyarrow.dittoe.schema.Behavior.
    """
    name: str = ""
    behavior_type: BehaviorType = BehaviorType.SOURCE_RETRIEVAL
    properties: Dict[str, str] = field(default_factory=dict)
    description: str = ""

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "behaviorType": self.behavior_type.value,
            "properties": self.properties,
            "description": self.description,
        }


@dataclass
class StagedBehavior(Behavior):
    """A behavior executed at a specific BAPPTE stage.
    Mirrors org.everyarrow.dittoe.schema.StagedBehavior.
    """
    plugin_reference: str = ""
    order: int = 0
    stage_type: StageType = StageType.PROCESS

    def to_dict(self) -> dict:
        d = super().to_dict()
        d["pluginReference"] = self.plugin_reference
        d["order"] = self.order
        d["stageType"] = self.stage_type.value
        return d


@dataclass
class SourceToTargetMapping:
    """Field-level mapping from source to target.
    Mirrors org.everyarrow.dittoe.schema.SourceToTargetMapping.
    """
    source_field: Optional[Field] = None
    target_field: Optional[Field] = None

    def to_dict(self) -> dict:
        return {
            "sourceField": self.source_field.to_dict() if self.source_field else {},
            "targetField": self.target_field.to_dict() if self.target_field else {},
        }


@dataclass
class ResourceDependencyMapping:
    """Mapping between a resource and its dependencies.
    Mirrors org.everyarrow.dittoe.schema.ResourceDependencyMapping.
    """
    resource: Optional[Resource] = None
    dependencies: List[Resource] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "resource": self.resource.to_dict() if self.resource else {},
            "dependencies": [r.to_dict() for r in self.dependencies],
        }


@dataclass
class PipelineMetadata:
    """The main DITTOE schema structure (PMD).
    Mirrors org.everyarrow.dittoe.schema.PipelineMetadata (extends PipelineMetadataBase).
    """
    # PipelineMetadataBase fields
    id: str = ""
    short_name: str = ""
    description: str = ""
    global_properties: Dict[str, str] = field(default_factory=dict)
    behaviors: List[Behavior] = field(default_factory=list)
    create_date: Optional[str] = None
    created_by: Optional[str] = None
    last_updated_by: Optional[str] = None
    last_updated_date: Optional[str] = None

    # PipelineMetadata fields
    source_resource: Optional[Resource] = None
    target_resource: Optional[Resource] = None
    template_type: TemplateType = TemplateType.DATABASE_TO_DATABASE
    field_mappings: List[SourceToTargetMapping] = field(default_factory=list)

    def to_dict(self) -> dict:
        d: dict = {
            "id": self.id,
            "shortName": self.short_name,
            "description": self.description,
            "globalProperties": self.global_properties,
            "behaviors": [b.to_dict() for b in self.behaviors],
            "sourceResourceToProcess": self.source_resource.to_dict() if self.source_resource else {},
            "targetResourceToProcess": self.target_resource.to_dict() if self.target_resource else {},
            "templateType": self.template_type.value,
            "S2TMap": [m.to_dict() for m in self.field_mappings],
        }
        if self.create_date is not None:
            d["createDate"] = self.create_date
        if self.created_by is not None:
            d["createdBy"] = self.created_by
        if self.last_updated_by is not None:
            d["lastUpdatedBy"] = self.last_updated_by
        if self.last_updated_date is not None:
            d["lastUpdatedDate"] = self.last_updated_date
        return d

    def to_json(self, indent: int = 2) -> str:
        """Serialize to JSON string."""
        return json.dumps(self.to_dict(), indent=indent)

    def save(self, path: str) -> None:
        """Save PMD JSON to a file."""
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "w") as f:
            f.write(self.to_json())
