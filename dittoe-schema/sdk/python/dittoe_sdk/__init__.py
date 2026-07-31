# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

"""
DITTOE SDK - Python SDK for building and executing DITTOE pipelines.

Provides fluent builder APIs for constructing Pipeline Metadata Documents (PMDs)
and an executor for running the DITTOE Java JAR.
"""

from .models import (
    # Enums
    DataType,
    BehaviorType,
    ServerType,
    TemplateType,
    StageType,
    ProcessExecutionType,
    DatabaseType,
    FileType,
    APIType,
    # Strategy constants
    ReadStrategy,
    WriteStrategy,
    # Data classes
    Field,
    ExpressionField,
    SourceField,
    Server,
    Resource,
    DQRule,
    Behavior,
    StagedBehavior,
    SourceToTargetMapping,
    ResourceDependencyMapping,
    PipelineMetadata,
)

from .builders import (
    FieldBuilder,
    ServerBuilder,
    ResourceBuilder,
    BehaviorBuilder,
    DQRuleBuilder,
    PipelineBuilder,
)

from .executor import (
    DittoeExecutor,
    ExecutionResult,
)

from .config import (
    DittoeConfig,
    DittoeConfigError,
)

from .facade import (
    Dittoe,
    Connection,
    ConnectionBuilder,
    parse_field,
    default_behaviors,
)

__version__ = "1.1.0"

__all__ = [
    # Enums
    "DataType", "BehaviorType", "ServerType", "TemplateType",
    "StageType", "ProcessExecutionType", "DatabaseType", "FileType", "APIType",
    # Strategy constants
    "ReadStrategy", "WriteStrategy",
    # Data classes
    "Field", "ExpressionField", "SourceField", "Server", "Resource",
    "DQRule", "Behavior", "StagedBehavior", "SourceToTargetMapping",
    "ResourceDependencyMapping", "PipelineMetadata",
    # Builders (power user API)
    "FieldBuilder", "ServerBuilder", "ResourceBuilder",
    "BehaviorBuilder", "DQRuleBuilder", "PipelineBuilder",
    # Executor
    "DittoeExecutor", "ExecutionResult",
    # Config
    "DittoeConfig", "DittoeConfigError",
    # Facade (simplified API)
    "Dittoe", "Connection", "ConnectionBuilder",
    "parse_field", "default_behaviors",
]
