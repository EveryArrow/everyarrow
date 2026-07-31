# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

"""
Executor for running the DITTOE Java JAR.
Handles PMD file placement, JAR invocation, and result parsing.
"""

from __future__ import annotations
import json
import os
import shutil
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional, Dict, List

from .models import PipelineMetadata


@dataclass
class ExecutionResult:
    """Result from a DITTOE pipeline execution."""
    success: bool = False
    return_code: int = -1
    stdout: str = ""
    stderr: str = ""
    summary: Dict = field(default_factory=dict)
    pmd_path: str = ""
    error_count: int = 0
    rows_processed: int = 0

    @property
    def has_errors(self) -> bool:
        return self.error_count > 0 or not self.success


class DittoeExecutor:
    """Executes DITTOE pipelines via the Java JAR.

    Usage:
        executor = DittoeExecutor("/path/to/execution-1.0-shaded.jar")
        result = executor.run(pipeline, timeout=300)
    """

    def __init__(self, jar_path: str, java_path: str = "java",
                 java_opts: Optional[List[str]] = None):
        """
        Args:
            jar_path: Path to the DITTOE shaded JAR file.
            java_path: Path to java executable (default: 'java').
            java_opts: Extra JVM options (e.g. ['-Xmx2g']).
        """
        self.jar_path = os.path.abspath(jar_path)
        self.java_path = java_path
        self.java_opts = java_opts or []

        if not os.path.isfile(self.jar_path):
            raise FileNotFoundError(f"JAR not found: {self.jar_path}")

    def run(self, pipeline: PipelineMetadata, base_dir: Optional[str] = None,
            timeout: int = 300, cleanup: bool = True,
            extra_args: Optional[List[str]] = None) -> ExecutionResult:
        """Execute a DITTOE pipeline.

        Args:
            pipeline: The pipeline metadata to execute.
            base_dir: Base directory for pipeline files. If None, uses a temp dir.
            timeout: Maximum execution time in seconds.
            cleanup: Whether to clean up temp directory after execution.
            extra_args: Extra command-line arguments for the JAR.

        Returns:
            ExecutionResult with stdout, stderr, summary, etc.
        """
        # Determine pipeline name (sanitize for directory)
        pipeline_name = pipeline.short_name.replace(" ", "")
        use_temp = base_dir is None
        if use_temp:
            base_dir = tempfile.mkdtemp(prefix="dittoe_")

        # DITTOE expects: basedir/pipelines/<name>/pmd.json
        pmd_dir = os.path.join(base_dir, "pipelines", pipeline_name)
        os.makedirs(pmd_dir, exist_ok=True)
        pmd_path = os.path.join(pmd_dir, "pmd.json")
        pipeline.save(pmd_path)

        # Build command: JAR uses -p for pipeline name, BASE_DIR as env var
        pipelines_dir = os.path.join(base_dir, "pipelines")
        cmd = [self.java_path] + self.java_opts + [
            "-jar", self.jar_path,
            "-p", pipeline_name,
        ]
        if extra_args:
            cmd.extend(extra_args)

        # JAR reads BASE_DIR from environment
        env = os.environ.copy()
        env["BASE_DIR"] = pipelines_dir

        result = ExecutionResult(pmd_path=pmd_path)

        try:
            proc = subprocess.run(
                cmd, capture_output=True, text=True,
                timeout=timeout, cwd=base_dir, env=env,
            )
            result.return_code = proc.returncode
            result.stdout = proc.stdout
            result.stderr = proc.stderr
            result.success = proc.returncode == 0

            # Try to parse summary JSON from output directory
            summary_path = os.path.join(pmd_dir, "summary.json")
            if os.path.isfile(summary_path):
                with open(summary_path) as f:
                    result.summary = json.load(f)
                result.error_count = result.summary.get("errorCount", 0)
                result.rows_processed = result.summary.get("rowsProcessed", 0)

        except subprocess.TimeoutExpired:
            result.success = False
            result.stderr = f"Pipeline execution timed out after {timeout}s"
        except Exception as e:
            result.success = False
            result.stderr = str(e)
        finally:
            if use_temp and cleanup:
                shutil.rmtree(base_dir, ignore_errors=True)

        return result

    def validate_pipeline(self, pipeline: PipelineMetadata) -> List[str]:
        """Validate a pipeline configuration before execution.

        Returns:
            List of validation error messages (empty if valid).
        """
        errors: List[str] = []

        if not pipeline.short_name:
            errors.append("Pipeline short_name is required")
        if not pipeline.source_resource:
            errors.append("Source resource is required")
        if not pipeline.target_resource:
            errors.append("Target resource is required")

        if pipeline.source_resource:
            if not pipeline.source_resource.server:
                errors.append("Source resource must have a server")
            if not pipeline.source_resource.strategy:
                errors.append("Source resource must have a strategy")
            if not pipeline.source_resource.fields:
                errors.append("Source resource must have at least one field")

        if pipeline.target_resource:
            if not pipeline.target_resource.server:
                errors.append("Target resource must have a server")
            if not pipeline.target_resource.strategy:
                errors.append("Target resource must have a strategy")
            if not pipeline.target_resource.fields:
                errors.append("Target resource must have at least one field")

        if not pipeline.behaviors:
            errors.append("Pipeline must have at least one behavior")

        if not pipeline.field_mappings:
            errors.append("Pipeline must have at least one field mapping")

        return errors
