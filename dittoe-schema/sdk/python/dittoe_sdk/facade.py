"""
DITTOE SDK Facade - High-level API for pipeline execution.

Provides a simplified interface for running data migrations.
Users create source/target connections using ConnectionBuilder,
then call Dittoe.migrate(source, target) to execute.

Power users can still use the low-level builders directly.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Union

from .models import (
    DataType, BehaviorType, ServerType, TemplateType,
    DatabaseType, FileType, APIType,
    Field, Server, Resource, Behavior, StagedBehavior,
    SourceToTargetMapping, PipelineMetadata,
    ReadStrategy, WriteStrategy, StageType,
)
from .builders import (
    FieldBuilder, ServerBuilder, ResourceBuilder,
    BehaviorBuilder, PipelineBuilder,
)
from .executor import DittoeExecutor, ExecutionResult
from .config import DittoeConfig, DittoeConfigError


# ============================================================================
# Field parsing helpers
# ============================================================================

# Mapping of short type names to DataType enum values
_TYPE_MAP: Dict[str, DataType] = {
    "string": DataType.STRING, "str": DataType.STRING,
    "integer": DataType.INTEGER, "int": DataType.INTEGER,
    "double": DataType.DOUBLE, "float": DataType.DOUBLE,
    "decimal": DataType.DECIMAL,
    "boolean": DataType.BOOLEAN, "bool": DataType.BOOLEAN,
    "date": DataType.DATE,
    "datetime": DataType.DATETIME,
    "time": DataType.TIME,
    "uuid": DataType.UUID,
    "json": DataType.JSON,
    "jsonb": DataType.JSONB,
    "array": DataType.ARRAY,
    "number": DataType.NUMBER,
    "bytes": DataType.BYTES,
    "xml": DataType.XML,
}


def parse_field(spec: Union[str, Field, Dict]) -> Field:
    """Parse a field specification into a Field object.

    Accepts:
        - str: "name:type" or just "name" (defaults to STRING)
        - Field: returned as-is
        - dict: {"name": ..., "type": ..., "nullable": ...}

    Examples:
        parse_field("id:integer")
        parse_field("name:string")
        parse_field("price")  # defaults to STRING
        parse_field({"name": "id", "type": "integer", "nullable": False})
    """
    if isinstance(spec, Field):
        return spec
    if isinstance(spec, dict):
        name = spec.get("name", "")
        type_str = spec.get("type", "string").lower()
        nullable = spec.get("nullable", True)
        return Field(
            name=name,
            data_type=_TYPE_MAP.get(type_str, DataType.STRING),
            is_nullable=nullable,
        )
    if isinstance(spec, str):
        parts = spec.split(":", 1)
        name = parts[0].strip()
        type_str = parts[1].strip().lower() if len(parts) > 1 else "string"
        return Field(
            name=name,
            data_type=_TYPE_MAP.get(type_str, DataType.STRING),
        )
    raise ValueError(f"Cannot parse field spec: {spec}")


# ============================================================================
# Default strategy resolution
# ============================================================================

# Maps (ServerType, "read"|"write") to default strategy
_DEFAULT_READ_STRATEGIES: Dict[str, str] = {
    DatabaseType.POSTGRESQL: ReadStrategy.JDBC_SELECT,
    DatabaseType.MYSQL: ReadStrategy.JDBC_SELECT,
    DatabaseType.MSSQL: ReadStrategy.JDBC_SELECT,
    DatabaseType.SQLITE: ReadStrategy.JDBC_SELECT,
    DatabaseType.SNOWFLAKE: ReadStrategy.JDBC_SELECT,
    FileType.LOCAL: ReadStrategy.LOCAL_CSV,
    FileType.S3: ReadStrategy.S3_CSV,
    FileType.GCS: ReadStrategy.GCS_CSV,
    FileType.AZURE: ReadStrategy.AZURE_CSV,
    FileType.DROPBOX: ReadStrategy.DROPBOX_CSV,
    FileType.SFTP: ReadStrategy.SFTP_CSV,
    FileType.FTP: ReadStrategy.FTP_CSV,
    FileType.IBM: ReadStrategy.IBM_CSV,
    FileType.ORACLE: ReadStrategy.ORACLE_CSV,
    FileType.DIGITAL_OCEAN: ReadStrategy.DIGITAL_OCEAN_CSV,
    FileType.GOOGLE_DRIVE: ReadStrategy.GOOGLE_DRIVE_CSV,
    APIType.BIGQUERY: ReadStrategy.BIGQUERY_QUERY,
    APIType.SALESFORCE: ReadStrategy.SALESFORCE,
}

_DEFAULT_WRITE_STRATEGIES: Dict[str, str] = {
    DatabaseType.POSTGRESQL: WriteStrategy.JDBC_INSERT,
    DatabaseType.MYSQL: WriteStrategy.JDBC_INSERT,
    DatabaseType.MSSQL: WriteStrategy.JDBC_INSERT,
    DatabaseType.SQLITE: WriteStrategy.JDBC_INSERT,
    DatabaseType.SNOWFLAKE: WriteStrategy.JDBC_INSERT,
    FileType.LOCAL: WriteStrategy.LOCAL_CSV,
    FileType.S3: WriteStrategy.S3_CSV,
    FileType.GCS: WriteStrategy.GCS_CSV,
    FileType.AZURE: WriteStrategy.AZURE_CSV,
    FileType.DROPBOX: WriteStrategy.DROPBOX_CSV,
    FileType.SFTP: WriteStrategy.SFTP_CSV,
    FileType.FTP: WriteStrategy.FTP_CSV,
    FileType.IBM: WriteStrategy.IBM_CSV,
    FileType.ORACLE: WriteStrategy.ORACLE_CSV,
    FileType.DIGITAL_OCEAN: WriteStrategy.DIGITAL_OCEAN_CSV,
    FileType.GOOGLE_DRIVE: WriteStrategy.GOOGLE_DRIVE_CSV,
    APIType.BIGQUERY: WriteStrategy.BIGQUERY,
    APIType.SALESFORCE: WriteStrategy.SALESFORCE,
}


def _resolve_template_type(source_server_type: ServerType,
                           target_server_type: ServerType) -> TemplateType:
    """Automatically determine the TemplateType from source/target ServerType."""
    key = f"{source_server_type.value}To{target_server_type.value}"
    for tt in TemplateType:
        if tt.value == key:
            return tt
    return TemplateType.DATABASE_TO_DATABASE


# ============================================================================
# Connection
# ============================================================================

@dataclass
class Connection:
    """Encapsulates everything needed for one end of a pipeline.

    Built by ConnectionBuilder. Contains: server, resource name,
    fields, strategy, schema, and resource properties.
    """
    name: str = ""
    server: Optional[Server] = None
    fields: List[Field] = field(default_factory=list)
    strategy: str = ""
    schema_name: str = ""
    resource_properties: Dict[str, str] = field(default_factory=dict)
    # Internal: the database/file/api type for default strategy resolution
    _db_type: str = ""

    def validate(self) -> List[str]:
        """Validate this connection.

        Returns:
            List of error messages (empty if valid).
        """
        errors: List[str] = []
        if not self.name:
            errors.append("Connection name is required")
        if self.server is None:
            errors.append(f"Connection '{self.name}': server is required")
        else:
            if not self.server.name:
                errors.append(f"Connection '{self.name}': server name is required")
            if not self.server.connection_details:
                errors.append(f"Connection '{self.name}': server connection details are empty")
        if not self.fields:
            errors.append(f"Connection '{self.name}': at least one field is required")
        if not self.strategy:
            errors.append(f"Connection '{self.name}': strategy is required")
        return errors


class ConnectionBuilder:
    """Fluent builder for creating a Connection (source or target).

    Usage:
        source = ConnectionBuilder("products") \\
            .postgresql("host", 5433, "mydb", "user", "pass") \\
            .schema("production") \\
            .fields(["id:integer", "name:string", "price:decimal"]) \\
            .read_strategy("jdbc_select") \\
            .build()

        # Or with auto-defaulted strategy:
        source = ConnectionBuilder("products") \\
            .postgresql("host", 5433, "mydb", "user", "pass") \\
            .schema("production") \\
            .fields(["id:integer", "name:string"]) \\
            .as_source() \\
            .build()
    """

    def __init__(self, resource_name: str):
        self._name = resource_name
        self._server_builder = ServerBuilder(resource_name)
        self._fields: List[Field] = []
        self._strategy = ""
        self._schema = ""
        self._properties: Dict[str, str] = {}
        self._is_source: Optional[bool] = None
        self._db_type = ""
        self._server_built = False

    # ---- Server type methods (delegate to ServerBuilder) ----

    def postgresql(self, host: str, port: int, database: str,
                   user: str, password: str) -> ConnectionBuilder:
        """Configure as PostgreSQL connection."""
        self._server_builder.postgresql(host, port, database, user, password)
        self._db_type = DatabaseType.POSTGRESQL
        self._server_built = True
        return self

    def mysql(self, host: str, port: int, database: str,
              user: str, password: str) -> ConnectionBuilder:
        """Configure as MySQL connection."""
        self._server_builder.mysql(host, port, database, user, password)
        self._db_type = DatabaseType.MYSQL
        self._server_built = True
        return self

    def mssql(self, host: str, port: int, database: str,
              user: str, password: str) -> ConnectionBuilder:
        """Configure as MSSQL connection."""
        self._server_builder.mssql(host, port, database, user, password)
        self._db_type = DatabaseType.MSSQL
        self._server_built = True
        return self

    def sqlite(self, database_path: str) -> ConnectionBuilder:
        """Configure as SQLite connection."""
        self._server_builder.sqlite(database_path)
        self._db_type = DatabaseType.SQLITE
        self._server_built = True
        return self

    def snowflake(self, account: str, warehouse: str, database: str,
                  schema: str, user: str, password: str) -> ConnectionBuilder:
        """Configure as Snowflake connection."""
        self._server_builder.snowflake(account, warehouse, database, schema, user, password)
        self._db_type = DatabaseType.SNOWFLAKE
        self._server_built = True
        return self

    def local_file(self, file_path: str) -> ConnectionBuilder:
        """Configure as local file connection."""
        self._server_builder.local_file(file_path)
        self._db_type = FileType.LOCAL
        self._server_built = True
        return self

    def s3(self, bucket: str, key: str, region: str = "",
           access_key: str = "", secret_key: str = "") -> ConnectionBuilder:
        """Configure as S3 connection."""
        self._server_builder.s3(bucket, key, region, access_key, secret_key)
        self._db_type = FileType.S3
        self._server_built = True
        return self

    def gcs(self, bucket: str, key: str,
            credentials_file: str = "") -> ConnectionBuilder:
        """Configure as GCS connection."""
        self._server_builder.gcs(bucket, key, credentials_file)
        self._db_type = FileType.GCS
        self._server_built = True
        return self

    def azure(self, container: str, blob: str,
              connection_string: str = "") -> ConnectionBuilder:
        """Configure as Azure Blob connection."""
        self._server_builder.azure(container, blob, connection_string)
        self._db_type = FileType.AZURE
        self._server_built = True
        return self

    def sftp(self, host: str, port: int, path: str,
             user: str, password: str = "", key_file: str = "") -> ConnectionBuilder:
        """Configure as SFTP connection."""
        self._server_builder.sftp(host, port, path, user, password, key_file)
        self._db_type = FileType.SFTP
        self._server_built = True
        return self

    def ftp(self, host: str, port: int, path: str,
            user: str, password: str) -> ConnectionBuilder:
        """Configure as FTP connection."""
        self._server_builder.ftp(host, port, path, user, password)
        self._db_type = FileType.FTP
        self._server_built = True
        return self

    def bigquery(self, project_id: str, dataset: str,
                 credentials_file: str) -> ConnectionBuilder:
        """Configure as BigQuery connection."""
        self._server_builder.bigquery(project_id, dataset, credentials_file)
        self._db_type = APIType.BIGQUERY
        self._server_built = True
        return self

    def salesforce(self, login_url: str, username: str,
                   password: str, security_token: str) -> ConnectionBuilder:
        """Configure as Salesforce connection."""
        self._server_builder.salesforce(login_url, username, password, security_token)
        self._db_type = APIType.SALESFORCE
        self._server_built = True
        return self

    # ---- Connection configuration ----

    def schema(self, schema_name: str) -> ConnectionBuilder:
        """Set the schema (e.g., 'production', 'public')."""
        self._schema = schema_name
        return self

    def fields(self, field_specs: List[Union[str, Field, Dict]]) -> ConnectionBuilder:
        """Set the fields for this connection.

        Args:
            field_specs: List of field specifications. Each can be:
                - str: "name:type" (e.g. "id:integer", "name:string")
                - Field object
                - dict: {"name": "...", "type": "...", "nullable": True/False}
        """
        self._fields = [parse_field(f) for f in field_specs]
        return self

    def add_field(self, name: str, data_type: DataType = DataType.STRING,
                  nullable: bool = True) -> ConnectionBuilder:
        """Add a single field."""
        self._fields.append(Field(name=name, data_type=data_type, is_nullable=nullable))
        return self

    def read_strategy(self, strategy: str) -> ConnectionBuilder:
        """Set the read strategy explicitly.

        Accepts either short names or full FQCN:
            - "jdbc_select", "local_csv", "s3_csv", etc.
            - Full FQCN like "com.everyarrow.dittoe.io...."
        """
        self._strategy = self._resolve_strategy_name(strategy, is_read=True)
        self._is_source = True
        return self

    def write_strategy(self, strategy: str) -> ConnectionBuilder:
        """Set the write strategy explicitly.

        Accepts either short names or full FQCN:
            - "jdbc_insert", "jdbc_replace", "pg_upsert", "pg_copy", etc.
            - Full FQCN like "com.everyarrow.dittoe.io...."
        """
        self._strategy = self._resolve_strategy_name(strategy, is_read=False)
        self._is_source = False
        return self

    def as_source(self) -> ConnectionBuilder:
        """Mark as source connection (auto-selects read strategy if not set)."""
        self._is_source = True
        if not self._strategy and self._db_type:
            self._strategy = _DEFAULT_READ_STRATEGIES.get(self._db_type, "")
        return self

    def as_target(self) -> ConnectionBuilder:
        """Mark as target connection (auto-selects write strategy if not set)."""
        self._is_source = False
        if not self._strategy and self._db_type:
            self._strategy = _DEFAULT_WRITE_STRATEGIES.get(self._db_type, "")
        return self

    def property(self, key: str, value: str) -> ConnectionBuilder:
        """Add a resource property."""
        self._properties[key] = value
        return self

    def build(self) -> Connection:
        """Build and return the Connection."""
        if not self._server_built:
            raise ValueError("No server type configured. Call .postgresql(), .mysql(), etc. first.")

        server = self._server_builder.build()

        # Auto-resolve strategy if not explicitly set
        strategy = self._strategy
        if not strategy and self._db_type:
            if self._is_source is True or self._is_source is None:
                strategy = _DEFAULT_READ_STRATEGIES.get(self._db_type, "")
            else:
                strategy = _DEFAULT_WRITE_STRATEGIES.get(self._db_type, "")

        # Auto-set schema as resource property
        props = dict(self._properties)
        if self._schema:
            props["schema"] = self._schema

        return Connection(
            name=self._name,
            server=server,
            fields=list(self._fields),
            strategy=strategy,
            schema_name=self._schema,
            resource_properties=props,
            _db_type=self._db_type,
        )

    @staticmethod
    def _resolve_strategy_name(name: str, is_read: bool) -> str:
        """Resolve a short strategy name to a full FQCN.

        If name already starts with 'com.', returns as-is (already FQCN).
        Otherwise resolves short names like 'jdbc_select', 'pg_upsert', etc.
        """
        if name.startswith("com."):
            return name

        key = name.upper()
        if is_read:
            # Check ReadStrategy class attributes
            for attr in dir(ReadStrategy):
                if attr == key and not attr.startswith("_"):
                    return getattr(ReadStrategy, attr)
        else:
            for attr in dir(WriteStrategy):
                if attr == key and not attr.startswith("_"):
                    return getattr(WriteStrategy, attr)

        # Return as-is; validation will catch invalid strategies
        return name


# ============================================================================
# Default behaviors
# ============================================================================

def default_behaviors() -> List[Behavior]:
    """Create the standard set of pipeline behaviors.

    Returns behaviors for: SourceRetrieval, TargetInsertUpdate,
    Instrumentation, Transformation — matching the engine's expected defaults.
    """
    return [
        BehaviorBuilder("source-retrieval").source_retrieval().error_threshold(1).build(),
        BehaviorBuilder("target-insert-update").target_insert_update().error_threshold(1).build(),
        BehaviorBuilder("instrumentation").instrumentation().build(),
        BehaviorBuilder("transformation").transformation().build(),
    ]


# ============================================================================
# Dittoe Facade
# ============================================================================

class Dittoe:
    """High-level facade for DITTOE pipeline execution.

    Provides a simplified API for data migration. Uses ConnectionBuilder
    to define source/target, then calls migrate() to execute.

    Usage:
        config = DittoeConfig.from_env()
        dittoe = Dittoe(config)

        source = ConnectionBuilder("products") \\
            .postgresql("src-host", 5433, "mydb", "user", "pass") \\
            .schema("production") \\
            .fields(["id:integer", "name:string", "price:decimal"]) \\
            .as_source() \\
            .build()

        target = ConnectionBuilder("products") \\
            .postgresql("tgt-host", 5434, "mydb", "user", "pass") \\
            .schema("production") \\
            .fields(["id:integer", "name:string", "price:decimal"]) \\
            .as_target() \\
            .build()

        result = dittoe.migrate(source, target)

    Power users can bypass this facade and use builders directly:
        from dittoe_sdk.builders import ServerBuilder, ResourceBuilder, PipelineBuilder
        from dittoe_sdk.executor import DittoeExecutor
    """

    def __init__(self, config: DittoeConfig):
        """Initialize with validated configuration.

        Args:
            config: DittoeConfig instance (call config.validate() first).
        """
        self._config = config
        self._executor = DittoeExecutor(
            jar_path=config.jar_path,
            java_path=config.java_path,
            java_opts=config.java_opts,
        )

    def migrate(
        self,
        source: Connection,
        target: Connection,
        pipeline_name: Optional[str] = None,
        behaviors: Optional[List[Union[str, Behavior]]] = None,
        field_mapping: str = "same_name",
        explicit_mappings: Optional[Dict[str, str]] = None,
        timeout: Optional[int] = None,
        cleanup: bool = True,
    ) -> ExecutionResult:
        """Execute a data migration pipeline.

        This is the main entry point. It takes any source/target pair,
        builds the pipeline internally, and executes it.

        Args:
            source: Source connection (built via ConnectionBuilder).
            target: Target connection (built via ConnectionBuilder).
            pipeline_name: Optional name for the pipeline (auto-generated if not provided).
            behaviors: List of behaviors. Accepts:
                - "default" for standard behaviors
                - Behavior objects for custom behaviors
                - Behavior type names as strings ("encryption", "retry", etc.)
                Default: ["default"] (SourceRetrieval + TargetInsertUpdate +
                Instrumentation + Transformation)
            field_mapping: "same_name" (auto-map matching field names) or "explicit".
            explicit_mappings: Dict of {"source_field": "target_field"} when field_mapping="explicit".
            timeout: Execution timeout in seconds (uses config default if not set).
            cleanup: Whether to clean up temp files after execution.

        Returns:
            ExecutionResult with success status, stdout, stderr, etc.

        Raises:
            DittoeConfigError: If validation fails on source, target, or config.
        """
        # Validate connections
        errors = source.validate() + target.validate()
        if errors:
            raise DittoeConfigError(
                "Connection validation failed:\n" + "\n".join(f"  - {e}" for e in errors)
            )

        # Build pipeline name
        if not pipeline_name:
            pipeline_name = f"{source.name}-to-{target.name}"

        # Determine template type from server types
        src_server_type = source.server.type if source.server else ServerType.DATABASE
        tgt_server_type = target.server.type if target.server else ServerType.DATABASE
        template_type = _resolve_template_type(src_server_type, tgt_server_type)

        # Build resources
        src_resource = Resource(
            resource_id=1,
            resource_name=source.name,
            server=source.server,
            strategy=source.strategy,
            resource_properties=source.resource_properties,
            fields=list(source.fields),
        )

        tgt_resource = Resource(
            resource_id=2,
            resource_name=target.name,
            server=target.server,
            strategy=target.strategy,
            resource_properties=target.resource_properties,
            fields=list(target.fields),
        )

        # Build field mappings
        mappings = self._build_field_mappings(
            source.fields, target.fields, field_mapping, explicit_mappings
        )

        # Build behaviors
        behavior_list = self._resolve_behaviors(behaviors)

        # Assemble pipeline
        pipeline = PipelineMetadata(
            id=pipeline_name,
            short_name=pipeline_name,
            description=f"Migration: {source.name} -> {target.name}",
            source_resource=src_resource,
            target_resource=tgt_resource,
            template_type=template_type,
            field_mappings=mappings,
            behaviors=behavior_list,
        )

        # Execute
        base_dir = self._config.ensure_base_dir()
        return self._executor.run(
            pipeline=pipeline,
            base_dir=base_dir,
            timeout=timeout or self._config.timeout,
            cleanup=cleanup,
        )

    @staticmethod
    def _build_field_mappings(
        source_fields: List[Field],
        target_fields: List[Field],
        mode: str,
        explicit: Optional[Dict[str, str]],
    ) -> List[SourceToTargetMapping]:
        """Build field mappings between source and target.

        Args:
            source_fields: Source field list.
            target_fields: Target field list.
            mode: "same_name" or "explicit".
            explicit: Dict of source -> target field name mappings.
        """
        if mode == "explicit" and explicit:
            mappings = []
            src_by_name = {f.name: f for f in source_fields}
            tgt_by_name = {f.name: f for f in target_fields}
            for src_name, tgt_name in explicit.items():
                src_f = src_by_name.get(src_name)
                tgt_f = tgt_by_name.get(tgt_name)
                if src_f and tgt_f:
                    mappings.append(SourceToTargetMapping(
                        source_field=src_f, target_field=tgt_f
                    ))
            return mappings

        # Default: same_name mapping
        tgt_by_name = {f.name: f for f in target_fields}
        return [
            SourceToTargetMapping(source_field=sf, target_field=tgt_by_name[sf.name])
            for sf in source_fields
            if sf.name in tgt_by_name
        ]

    @staticmethod
    def _resolve_behaviors(
        behaviors: Optional[List[Union[str, Behavior]]]
    ) -> List[Behavior]:
        """Resolve behavior specifications into Behavior objects.

        Accepts:
            - None or ["default"] -> standard behaviors
            - List of Behavior objects -> used directly
            - List of behavior type names -> resolved via BehaviorBuilder
        """
        if behaviors is None or behaviors == ["default"]:
            return default_behaviors()

        result: List[Behavior] = []
        for b in behaviors:
            if isinstance(b, Behavior):
                result.append(b)
            elif isinstance(b, str):
                # Resolve string name to behavior
                builder = BehaviorBuilder(b)
                # Try to call the method matching the behavior name
                method = getattr(builder, b.lower().replace(" ", "_"), None)
                if method and callable(method):
                    method()
                result.append(builder.build())
            else:
                raise ValueError(f"Invalid behavior specification: {b}")
        return result
