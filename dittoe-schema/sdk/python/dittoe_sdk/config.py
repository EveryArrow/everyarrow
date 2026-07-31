"""
DITTOE SDK Configuration.

Centralizes runtime settings with environment variable support and validation.
"""

from __future__ import annotations
import os
import shutil
from dataclasses import dataclass, field
from typing import Optional, List


class DittoeConfigError(Exception):
    """Raised when configuration validation fails."""
    pass


@dataclass
class DittoeConfig:
    """Central configuration for DITTOE SDK.

    All settings can be loaded from environment variables with sensible defaults.

    Environment variables:
        DITTOE_JAR      - Path to the DITTOE execution JAR (required)
        DITTOE_BASE_DIR - Base directory for pipeline files (default: ./pipelines)
        DITTOE_JAVA     - Path to java executable (default: java)
        DITTOE_JAVA_OPTS - Comma-separated JVM options (default: empty)
        DITTOE_TIMEOUT  - Default execution timeout in seconds (default: 300)

    Usage:
        config = DittoeConfig.from_env()
        config.validate()  # raises DittoeConfigError on failure
    """
    jar_path: str = ""
    base_dir: str = "./pipelines"
    java_path: str = "java"
    java_opts: List[str] = field(default_factory=list)
    timeout: int = 300

    @classmethod
    def from_env(cls) -> DittoeConfig:
        """Load configuration from environment variables.

        Returns:
            DittoeConfig populated from env vars with defaults.
        """
        java_opts_str = os.environ.get("DITTOE_JAVA_OPTS", "")
        java_opts = [o.strip() for o in java_opts_str.split(",") if o.strip()] if java_opts_str else []

        return cls(
            jar_path=os.environ.get("DITTOE_JAR", ""),
            base_dir=os.environ.get("DITTOE_BASE_DIR", "./pipelines"),
            java_path=os.environ.get("DITTOE_JAVA", "java"),
            java_opts=java_opts,
            timeout=int(os.environ.get("DITTOE_TIMEOUT", "300")),
        )

    def validate(self) -> List[str]:
        """Validate this configuration.

        Returns:
            List of validation error messages (empty if valid).

        Raises:
            DittoeConfigError: If any critical validation fails.
        """
        errors: List[str] = []

        # JAR path validation
        if not self.jar_path:
            errors.append("jar_path is required (set DITTOE_JAR env var or pass directly)")
        elif not os.path.isfile(self.jar_path):
            errors.append(f"JAR file not found: {self.jar_path}")
        elif not os.access(self.jar_path, os.R_OK):
            errors.append(f"JAR file is not readable: {self.jar_path}")

        # Java validation
        if not self.java_path:
            errors.append("java_path is required")
        elif not shutil.which(self.java_path):
            errors.append(f"Java executable not found: {self.java_path}")

        # Base directory validation
        if not self.base_dir:
            errors.append("base_dir is required")

        # Timeout validation
        if self.timeout <= 0:
            errors.append(f"timeout must be positive, got: {self.timeout}")

        if errors:
            raise DittoeConfigError(
                "Configuration validation failed:\n" + "\n".join(f"  - {e}" for e in errors)
            )

        return errors

    def ensure_base_dir(self) -> str:
        """Create base_dir if it doesn't exist, return absolute path."""
        abs_path = os.path.abspath(self.base_dir)
        os.makedirs(abs_path, exist_ok=True)
        return abs_path
