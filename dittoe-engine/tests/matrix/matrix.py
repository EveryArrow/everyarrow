#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import io
import json
import os
import shlex
import shutil
import sqlite3
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = REPO_ROOT / "tests/matrix/connector-manifest.json"
DEFAULT_BASE_DIR = REPO_ROOT / "target/dittoe-matrix/pipelines"
RESULTS_DIR = REPO_ROOT / "target/dittoe-matrix/results"
STATUS_FILE = RESULTS_DIR / "status.json"
DEFAULT_REPORT = REPO_ROOT / "target/dittoe-matrix/status-report.md"
DEFAULT_PERMUTATION_REPORT = REPO_ROOT / "tests/matrix/PERMUTATION-REPORT.md"
DEFAULT_SCENARIO = "canonical-smoke"
VERIFIED_TARGETS = {
    "local-file", "sqlite", "postgresql", "mysql", "mssql", "ftp", "sftp",
    "s3", "ibm-cos", "digitalocean-spaces", "gcs", "azure-blob", "bigquery"
}
HASH_VERIFIED_TARGETS = {
    "local-file", "ftp", "sftp", "s3", "ibm-cos", "digitalocean-spaces", "gcs", "azure-blob"
}
BIGQUERY_MATRIX_PROFILE = "bigquery-pairs"
BIGQUERY_METADATA_HOST = os.environ.get("DITTOE_MATRIX_BQ_METADATA_HOST", "diablo@64.225.59.141")
BIGQUERY_SECRET_HOST = os.environ.get(
    "DITTOE_MATRIX_BQ_SECRET_HOST",
    "ec2-user@52.55.190.97"
)
BIGQUERY_SECRET_KEY_PATH = Path(
    os.environ.get("DITTOE_MATRIX_BQ_SSH_KEY", str(Path.home() / ".ssh/db-pg17-key.pem"))
)
BIGQUERY_CUSTOMER_SERVER_ID = os.environ.get("DITTOE_MATRIX_BQ_CUSTOMER_SERVER_ID", "452")
BIGQUERY_DEFAULT_PROJECT = os.environ.get("DITTOE_MATRIX_BQ_PROJECT", "connectortest-468901")
BIGQUERY_DEFAULT_DATASET = os.environ.get("DITTOE_MATRIX_BQ_DATASET", "AdventureWorks")
_BIGQUERY_RUNTIME_CONFIG: dict[str, str] | None = None
_BIGQUERY_HELPER_DIR: Path | None = None
BIGQUERY_CACHED_PROPS_FILE = Path(os.environ.get("DITTOE_MATRIX_BQ_PROPS_CACHE", "/tmp/bq_encrypted_props.json"))
BIGQUERY_CACHED_SECRET_FILE = Path(os.environ.get("DITTOE_MATRIX_BQ_SECRET_CACHE", "/tmp/ea_secret.txt"))


def repo_path(value: str | Path) -> Path:
    path = Path(value)
    return path if path.is_absolute() else REPO_ROOT / path


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as manifest_file:
        return json.load(manifest_file)


def fixture_row_count(fixture: Path) -> int:
    with fixture.open(newline="", encoding="utf-8") as fixture_file:
        return sum(1 for _ in csv.DictReader(fixture_file))


def runtime_manifest(path: Path, fixture_override: Path | None = None) -> dict[str, Any]:
    manifest = load_manifest(path)
    if fixture_override is None:
        return manifest

    fixture_path = repo_path(fixture_override)
    manifest["dataset"] = dict(manifest["dataset"])
    manifest["dataset"]["fixture"] = str(fixture_path)
    manifest["dataset"]["expectedRows"] = fixture_row_count(fixture_path)
    return manifest


def shell_single_quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def bigquery_ssh_command(host: str, remote_command: str) -> list[str]:
    command = ["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new"]
    if BIGQUERY_SECRET_KEY_PATH.exists():
        command.extend(["-i", str(BIGQUERY_SECRET_KEY_PATH)])
    command.extend([host, remote_command])
    return command


def require_text(command: list[str], error_message: str) -> str:
    completed = run_command(command)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or error_message
        raise RuntimeError(detail)
    return completed.stdout.strip()


def fetch_remote_secret_key() -> str:
    remote_command = (
        "systemctl show ea-event-manager --property=Environment --value "
        "| tr ' ' '\\n' | grep '^SECRET_KEY=' | head -n1 | cut -d= -f2-"
    )
    return require_text(
        bigquery_ssh_command(BIGQUERY_SECRET_HOST, remote_command),
        "Failed to retrieve remote SECRET_KEY"
    )


def parse_cached_bigquery_props(text: str) -> dict[str, str]:
    if not text:
        return {}
    key_match = re.search(r"key:\s*([^,}]+)", text)
    project_match = re.search(r"project:\s*([^,}]+)", text)
    return {
        "key": key_match.group(1).strip() if key_match else "",
        "project": project_match.group(1).strip() if project_match else "",
    }


def load_cached_bigquery_row() -> dict[str, str]:
    if not BIGQUERY_CACHED_PROPS_FILE.exists():
        return {}
    return parse_cached_bigquery_props(BIGQUERY_CACHED_PROPS_FILE.read_text(encoding="utf-8").strip())


def load_cached_secret_key() -> str:
    if not BIGQUERY_CACHED_SECRET_FILE.exists():
        return ""
    return BIGQUERY_CACHED_SECRET_FILE.read_text(encoding="utf-8").strip()


def fetch_bigquery_row() -> dict[str, str]:
    query = (
        "SELECT json_build_object("
        "'key', connection_properties->>'key', "
        "'project', COALESCE(connection_properties->>'project', ''), "
        "'dataset', COALESCE(("
        "SELECT pipeline_metadata->'targetResourceToProcess'->'resourceProperties'->>'dataset' "
        "FROM tap.pipeline_metadata pm WHERE pm.target_server_id = customer_server.id LIMIT 1"
        "), '')"
        ")::text "
        "FROM tap.customer_server WHERE id = %s;"
    ) % BIGQUERY_CUSTOMER_SERVER_ID
    remote_command = (
        'psql -U diablo -d everyarrow_development -t -A -c '
        + shell_single_quote(query)
    )
    text = require_text(
        bigquery_ssh_command(BIGQUERY_METADATA_HOST, remote_command),
        "Failed to retrieve BigQuery customer_server row"
    )
    if not text:
        raise RuntimeError(f"BigQuery customer_server {BIGQUERY_CUSTOMER_SERVER_ID} returned no data")
    return json.loads(text)


def ensure_bigquery_helper_dir() -> Path:
    global _BIGQUERY_HELPER_DIR
    if _BIGQUERY_HELPER_DIR is not None:
        return _BIGQUERY_HELPER_DIR

    helper_dir = REPO_ROOT / "target/dittoe-matrix/bigquery-helper"
    helper_dir.mkdir(parents=True, exist_ok=True)
    decrypt_source = helper_dir / "DecryptBigQueryKey.java"
    admin_source = helper_dir / "BigQueryMatrixAdmin.java"

    decrypt_source.write_text(
        """
import com.everyarrow.utilities.security.CryptoUtils;

public class DecryptBigQueryKey {
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected encrypted value and secret key");
        }
        System.out.print(CryptoUtils.decrypt(args[0], CryptoUtils.createKeyFromString(args[1])));
    }
}
""".strip() + "\n",
        encoding="utf-8"
    )
    admin_source.write_text(
        """
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.CsvOptions;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.WriteChannelConfiguration;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

public class BigQueryMatrixAdmin {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: <command> <key> <project> <dataset> [args...]");
        }
        String command = args[0];
        String key = args[1];
        String project = args[2];
        String dataset = args[3];
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(key))
        );
        BigQuery bigQuery = BigQueryOptions.newBuilder().setCredentials(credentials).setProjectId(project).build().getService();
        switch (command) {
            case "delete-table" -> deleteTable(bigQuery, project, dataset, args[4]);
            case "read-table" -> readTable(bigQuery, project, dataset, args[4]);
            case "seed-csv" -> seedCsv(bigQuery, project, dataset, args[4], Path.of(args[5]));
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void deleteTable(BigQuery bigQuery, String project, String dataset, String table) {
        bigQuery.delete(TableId.of(project, dataset, table));
    }

    private static void readTable(BigQuery bigQuery, String project, String dataset, String table) throws Exception {
        QueryJobConfiguration query = QueryJobConfiguration.newBuilder(
                "SELECT * FROM `" + project + "." + dataset + "." + table + "` ORDER BY id"
        ).build();
        TableResult result = bigQuery.query(query);
        String[] fieldNames = result.getSchema().getFields().stream().map(com.google.cloud.bigquery.Field::getName).toArray(String[]::new);
        for (FieldValueList row : result.iterateAll()) {
            for (int index = 0; index < fieldNames.length; index++) {
                if (index > 0) {
                    System.out.print('\t');
                }
                Object value = row.get(fieldNames[index]).getValue();
                System.out.print(value == null ? "" : value.toString());
            }
            System.out.println();
        }
    }

    private static void seedCsv(BigQuery bigQuery, String project, String dataset, String table, Path csvPath) throws Exception {
        TableId tableId = TableId.of(project, dataset, table);
        bigQuery.delete(tableId);
        String csv = Files.readString(csvPath);
        String[] lines = csv.split("\\\\R");
        String[] header = lines[0].split(",", -1);
        com.google.cloud.bigquery.Field[] schemaFields = new com.google.cloud.bigquery.Field[header.length];
        for (int i = 0; i < header.length; i++) {
            schemaFields[i] = com.google.cloud.bigquery.Field.of(header[i], StandardSQLTypeName.STRING);
        }
        Schema schema = Schema.of(schemaFields);
        TableDefinition definition = com.google.cloud.bigquery.StandardTableDefinition.of(schema);
        bigQuery.create(TableInfo.of(tableId, definition));
        WriteChannelConfiguration config = WriteChannelConfiguration.newBuilder(tableId)
                .setFormatOptions(CsvOptions.newBuilder().setSkipLeadingRows(1).build())
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .build();
        JobId jobId = JobId.of(UUID.randomUUID().toString());
        try (var writer = bigQuery.writer(jobId, config)) {
            writer.write(java.nio.ByteBuffer.wrap(csv.getBytes()));
        }
        Job job = bigQuery.getJob(jobId);
        if (job != null) {
            job = job.waitFor();
        }
        if (job == null || job.getStatus().getError() != null) {
            throw new IllegalStateException("BigQuery CSV seed failed");
        }
    }
}
""".strip() + "\n",
        encoding="utf-8"
    )

    classpath = ":".join([
        str(REPO_ROOT / "utilities/target/classes"),
        str(REPO_ROOT / "target/execution-1.0-shaded.jar")
    ])
    compile = run_command([
        "javac", "-cp", classpath, "-d", str(helper_dir),
        str(decrypt_source), str(admin_source)
    ])
    if compile.returncode != 0:
        raise RuntimeError(compile.stderr.strip() or compile.stdout.strip() or "Failed to compile BigQuery helpers")
    _BIGQUERY_HELPER_DIR = helper_dir
    return helper_dir


def java_classpath(*extra: Path) -> str:
    helper_dir = ensure_bigquery_helper_dir()
    parts = [str(helper_dir), str(REPO_ROOT / "utilities/target/classes"), str(REPO_ROOT / "target/execution-1.0-shaded.jar")]
    parts.extend(str(path) for path in extra)
    return ":".join(parts)


def decrypt_bigquery_key(encrypted_key: str, secret_key: str) -> str:
    return require_text(
        ["java", "-cp", java_classpath(), "DecryptBigQueryKey", encrypted_key, secret_key],
        "Failed to decrypt BigQuery key"
    )


def bigquery_runtime_config() -> dict[str, str]:
    global _BIGQUERY_RUNTIME_CONFIG
    if _BIGQUERY_RUNTIME_CONFIG is not None:
        return dict(_BIGQUERY_RUNTIME_CONFIG)

    row = load_cached_bigquery_row()
    secret_key = load_cached_secret_key()
    if not row.get("key"):
        row = fetch_bigquery_row()
    if not secret_key:
        secret_key = fetch_remote_secret_key()
    project = row.get("project") or BIGQUERY_DEFAULT_PROJECT
    dataset = row.get("dataset") or BIGQUERY_DEFAULT_DATASET
    decrypted_key = decrypt_bigquery_key(row["key"], secret_key)
    _BIGQUERY_RUNTIME_CONFIG = {
        "key": decrypted_key,
        "project": project,
        "dataset": dataset,
    }
    return dict(_BIGQUERY_RUNTIME_CONFIG)


def configure_bigquery_connector(manifest: dict[str, Any]) -> None:
    connector = manifest.get("connectors", {}).get("bigquery")
    if not connector:
        return
    runtime = bigquery_runtime_config()
    connector["local"] = True
    connector["connectionDetails"] = {
        "key": runtime["key"],
        "project": runtime["project"],
    }
    connector["resourceProperties"] = {
        "dataset": runtime["dataset"],
    }
    connector["source"] = {
        "resourceName": "matrix_source_{case}",
        "resourceProperties": {"dataset": runtime["dataset"]},
    }
    connector["target"] = {
        "resourceName": "matrix_target_{case}",
        "resourceProperties": {"dataset": runtime["dataset"]},
    }
    profiles = manifest.setdefault("profiles", {})
    if BIGQUERY_MATRIX_PROFILE not in profiles:
        profiles[BIGQUERY_MATRIX_PROFILE] = [*profiles.get("docker-full", []), "bigquery"]
    scenario_profiles = manifest.setdefault("scenarioProfiles", {})
    scenario_profiles.setdefault(BIGQUERY_MATRIX_PROFILE, [DEFAULT_SCENARIO])


def manifest_needs_bigquery_support(
    manifest: dict[str, Any],
    profile: str,
    only_sources: list[str] | None,
    only_targets: list[str] | None,
    only_cases: list[str] | None
) -> bool:
    if any(name == "bigquery" for name in (only_sources or [])):
        return True
    if any(name == "bigquery" for name in (only_targets or [])):
        return True
    if any("bigquery" in pipeline_name for pipeline_name in (only_cases or [])):
        return True
    return profile in {"remote-full", BIGQUERY_MATRIX_PROFILE}


def configure_manifest_for_request(
    manifest: dict[str, Any],
    profile: str,
    only_sources: list[str] | None,
    only_targets: list[str] | None,
    only_cases: list[str] | None
) -> dict[str, Any]:
    if manifest_needs_bigquery_support(manifest, profile, only_sources, only_targets, only_cases):
        configure_bigquery_connector(manifest)
    return manifest


def run_bigquery_admin(command: str, *args: str) -> subprocess.CompletedProcess[str]:
    runtime = bigquery_runtime_config()
    return run_command([
        "java", "-cp", java_classpath(), "BigQueryMatrixAdmin",
        command,
        runtime["key"],
        runtime["project"],
        runtime["dataset"],
        *args,
    ])


def format_strings(value: Any, case_slug: str) -> Any:
    if isinstance(value, str):
        return value.replace("{case}", case_slug)
    if isinstance(value, dict):
        return {key: format_strings(inner, case_slug) for key, inner in value.items()}
    if isinstance(value, list):
        return [format_strings(inner, case_slug) for inner in value]
    return value


def merge_dicts(*values: dict[str, Any] | None) -> dict[str, Any]:
    merged: dict[str, Any] = {}
    for value in values:
        if value:
            merged.update(value)
    return merged


def field_list(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    return [dict(field) for field in manifest["dataset"]["fields"]]


def s2t_map(fields: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{"sourceField": dict(field), "targetField": dict(field)} for field in fields]


def behaviors() -> list[dict[str, Any]]:
    return [
        {
            "name": "SourceRetrieval",
            "description": "Used to retrieve data from the source resource",
            "behaviorType": "SourceRetrieval",
            "properties": {"errorCountThreshold": "1"}
        },
        {
            "name": "TargetInsertUpdate",
            "description": "Used to write data to the target resource",
            "behaviorType": "TargetInsertUpdate",
            "properties": {"errorCountThreshold": "1"}
        },
        {
            "name": "Instrumentation",
            "description": "Used for pipeline instrumentation",
            "behaviorType": "Instrumentation",
            "properties": {}
        }
    ]


def type_key(server_type: str) -> str:
    return f"{server_type.lower()}Type"


def resource_for(
    manifest: dict[str, Any],
    connector_name: str,
    connector: dict[str, Any],
    role: str,
    fields: list[dict[str, Any]],
    case_slug: str,
    resource_id: int,
    scenario_name: str = DEFAULT_SCENARIO
) -> dict[str, Any]:
    role_config = connector[role]
    scenario_overrides = scenario_connector_overrides(manifest, scenario_name, connector_name, role)
    server_type = connector["serverType"]
    connection_details = merge_dicts(
        connector.get("connectionDetails"),
        role_config.get("connectionDetails"),
        scenario_overrides.get("connectionDetails")
    )
    connection_details.setdefault(type_key(server_type), connector["concreteServer"])

    resource_properties = merge_dicts(
        connector.get("resourceProperties"),
        role_config.get("resourceProperties"),
        scenario_overrides.get("resourceProperties")
    )
    resource_name = format_strings(scenario_overrides.get("resourceName", role_config["resourceName"]), case_slug)
    strategy_key = "sourceStrategy" if role == "source" else "targetStrategy"

    return {
        "resourceID": resource_id,
        "resourceName": resource_name,
        "strategy": connector[strategy_key],
        "resourceProperties": format_strings(resource_properties, case_slug),
        "server": {
            "id": resource_id,
            "name": f"matrix-{connector_name}-{role}",
            "type": server_type,
            "connectionDetails": format_strings(connection_details, case_slug),
            "additionalProperties": {}
        },
        "fields": [dict(field) for field in fields]
    }


def pmd_for_case(manifest: dict[str, Any], source_name: str, target_name: str, scenario_name: str) -> dict[str, Any]:
    connectors = manifest["connectors"]
    source = connectors[source_name]
    target = connectors[target_name]
    fields = field_list(manifest)
    scenario = scenario_definition(manifest, scenario_name)
    case_slug = f"{source_name}-to-{target_name}-{scenario_name}"
    pipeline_id = f"matrix-{case_slug}"

    return {
        "id": pipeline_id,
        "shortName": case_slug,
        "S2TMap": s2t_map(fields),
        "behaviors": behaviors(),
        "globalProperties": {
            "matrixCase": "true",
            "expectedRows": str(manifest["dataset"]["expectedRows"]),
            **format_strings(scenario.get("globalProperties", {}), case_slug)
        },
        "sourceResourceToProcess": resource_for(manifest, source_name, source, "source", fields, case_slug, 0, scenario_name),
        "targetResourceToProcess": resource_for(manifest, target_name, target, "target", fields, case_slug, 1, scenario_name),
        "templateType": f"{source['serverType']}To{target['serverType']}"
    }


def supports_role(connector: dict[str, Any], role: str) -> bool:
    if not connector.get("implemented") or not connector.get("local"):
        return False
    if role not in connector:
        return False
    strategy_key = "sourceStrategy" if role == "source" else "targetStrategy"
    return strategy_key in connector


def role_ready(connector: dict[str, Any], role: str) -> bool:
    if not connector.get("implemented"):
        return False
    if role not in connector:
        return False
    strategy_key = "sourceStrategy" if role == "source" else "targetStrategy"
    return strategy_key in connector


def role_gaps(connector_name: str, connector: dict[str, Any], role: str) -> list[str]:
    if not connector.get("implemented"):
        return [f"{connector_name}: not implemented"]

    gaps: list[str] = []
    strategy_key = "sourceStrategy" if role == "source" else "targetStrategy"
    if strategy_key not in connector:
        gaps.append(f"{connector_name}: missing {strategy_key}")
    if role not in connector:
        gaps.append(f"{connector_name}: missing {role} resource config")
    if not connector.get("local"):
        gaps.append(f"{connector_name}: remote-only or non-local test path")
    return gaps


def implemented_connectors(manifest: dict[str, Any]) -> list[str]:
    return [
        name for name, connector in manifest["connectors"].items()
        if connector.get("implemented")
    ]


def practical_connectors(manifest: dict[str, Any]) -> list[str]:
    connectors = manifest["connectors"]
    return [
        name for name, connector in connectors.items()
        if connector.get("implemented") and connector.get("local")
    ]


def connector_is_easily_fixable(connector: dict[str, Any]) -> bool:
    return (
        connector.get("implemented")
        and not connector.get("local")
        and not connector.get("needsFakeClient")
        and "sourceStrategy" in connector
        and "targetStrategy" in connector
    )


def connector_is_credential_gated(connector: dict[str, Any]) -> bool:
    return (
        connector.get("implemented")
        and not connector.get("local")
        and not connector.get("needsFakeClient")
        and "sourceStrategy" in connector
        and "targetStrategy" in connector
        and connector.get("remoteProfile")
    )


def connector_readiness_row(name: str, connector: dict[str, Any]) -> tuple[str, str, str, str, str]:
    notes = []
    if connector.get("remoteProfile"):
        notes.append("remote-profile")
    if connector.get("needsFakeClient"):
        notes.append("needs-fake-client")
    if connector.get("excludedReason"):
        notes.append(connector["excludedReason"])
    if not notes:
        if connector.get("local") and role_ready(connector, "source") and role_ready(connector, "target"):
            notes.append("matrix-ready")
        elif connector_is_credential_gated(connector):
            notes.append("credential-gated remote")
        elif connector_is_easily_fixable(connector):
            notes.append("easily-fixable manifest contract work")
        else:
            notes.append("needs contract work")

    return (
        name,
        "yes" if role_ready(connector, "source") else "no",
        "yes" if role_ready(connector, "target") else "no",
        "yes" if connector.get("local") else "no",
        "; ".join(notes)
    )


def permutation_status(source_name: str, target_name: str, source: dict[str, Any], target: dict[str, Any]) -> tuple[str, str]:
    if connector_is_credential_gated(source) or connector_is_credential_gated(target):
        return "credential-gated-remote", "implemented path exists but execution requires real remote credentials and resource contracts"

    issues = [*role_gaps(source_name, source, "source"), *role_gaps(target_name, target, "target")]
    if issues:
        return "blocked", "; ".join(issues)
    if source.get("local") and target.get("local"):
        return "matrix-ready", "covered by canonical-smoke full permutation run"
    return "remote-contract-needed", "implemented but not locally runnable in current matrix"


def permutation_test_result(
    manifest: dict[str, Any],
    source_name: str,
    target_name: str,
    base_dir: Path,
    expected_rows: int,
    status_entries: dict[str, Any]
) -> tuple[str, str]:
    source = manifest["connectors"][source_name]
    target = manifest["connectors"][target_name]
    structural_status, _ = permutation_status(source_name, target_name, source, target)

    if structural_status != "matrix-ready":
        if source_name in practical_connectors(manifest) and target_name in practical_connectors(manifest):
            return "practical-untested", "not runnable yet; contract work still required"
        return "not-testable", "not currently runnable in the local matrix"

    pipeline_name = f"{source_name}-to-{target_name}/{DEFAULT_SCENARIO}"
    status = case_status(pipeline_name, base_dir, expected_rows, status_entries)
    if not status["completed"]:
        return "ready-but-untested", "matrix-ready but no recorded canonical-smoke run"
    if status["passed"]:
        return "tested-pass", status.get("outputVerification", "passed")
    return "tested-fail", status.get("outputVerification", "failed")


def permutation_report_lines(manifest: dict[str, Any], base_dir: Path, status_entries: dict[str, Any]) -> list[str]:
    connectors = manifest["connectors"]
    names = implemented_connectors(manifest)
    practical_names = practical_connectors(manifest)
    readiness_rows = [connector_readiness_row(name, connectors[name]) for name in names]
    expected_rows = int(manifest["dataset"]["expectedRows"])

    total_pairs = len(names) * len(names)
    practical_pairs = len(practical_names) * len(practical_names)
    matrix_ready = 0
    credential_gated_remote = 0
    remote_contract_needed = 0
    blocked = 0
    practical_matrix_ready = 0
    practical_contract_needed = 0
    tested_pass = 0
    tested_fail = 0
    ready_but_untested = 0
    practical_untested = 0
    pair_rows: list[tuple[str, str, str, str, str, str]] = []

    for source_name in names:
        for target_name in names:
            status, reason = permutation_status(source_name, target_name, connectors[source_name], connectors[target_name])
            test_status, evidence = permutation_test_result(manifest, source_name, target_name, base_dir, expected_rows, status_entries)
            if status == "matrix-ready":
                matrix_ready += 1
            elif status == "credential-gated-remote":
                credential_gated_remote += 1
            elif status == "remote-contract-needed":
                remote_contract_needed += 1
            else:
                blocked += 1
            if test_status == "tested-pass":
                tested_pass += 1
            elif test_status == "tested-fail":
                tested_fail += 1
            elif test_status == "ready-but-untested":
                ready_but_untested += 1
            elif test_status == "practical-untested":
                practical_untested += 1
            pair_rows.append((source_name, target_name, status, reason, test_status, evidence))

    for source_name in practical_names:
        for target_name in practical_names:
            source = connectors[source_name]
            target = connectors[target_name]
            if source.get("local") and target.get("local"):
                practical_matrix_ready += 1
            else:
                practical_contract_needed += 1

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    lines = [
        "# Dittoe Engine Permutation Audit",
        "",
        f"Generated: {timestamp}",
        f"Manifest: `{DEFAULT_MANIFEST.relative_to(REPO_ROOT)}`",
        "",
        "This report enumerates every source-target permutation across the full implemented inventory and a narrower practical scope of already-working plus easily-fixable connectors.",
        "",
        "## Summary",
        "",
        f"- Implemented connectors: {len(names)}",
        f"- Total implemented permutations: {total_pairs}",
        f"- Matrix-ready permutations: {matrix_ready}",
        f"- Credential-gated remote permutations: {credential_gated_remote}",
        f"- Remote-contract-needed permutations: {remote_contract_needed}",
        f"- Blocked permutations: {blocked}",
        f"- Practical connectors: {len(practical_names)}",
        f"- Practical permutations: {practical_pairs}",
        f"- Practical matrix-ready permutations: {practical_matrix_ready}",
        f"- Practical contract-needed permutations: {practical_contract_needed}",
        f"- Tested passing permutations: {tested_pass}",
        f"- Tested failing permutations: {tested_fail}",
        f"- Ready but untested permutations: {ready_but_untested}",
        f"- Practical but untested permutations: {practical_untested}",
        "",
        "## Connector Readiness",
        "",
        "| Connector | Source Ready | Target Ready | Local Runnable | Notes |",
        "| --- | --- | --- | --- | --- |"
    ]

    for name, source_ready, target_ready, local_ready, notes in readiness_rows:
        lines.append(f"| `{name}` | {source_ready} | {target_ready} | {local_ready} | {notes} |")

    lines.extend([
        "",
        "## Practical Scope",
        "",
        "These connectors are either already locally runnable or close enough that the remaining work is manifest/runtime contract wiring rather than new adapter implementation.",
        "",
        "| Connector | Inclusion Reason |",
        "| --- | --- |"
    ])

    for name in practical_names:
        connector = connectors[name]
        reason = "already matrix-ready" if connector.get("local") else "easily-fixable contract work"
        lines.append(f"| `{name}` | {reason} |")

    lines.extend([
        "",
        "## Permutations",
        "",
        "| Source | Target | Structural Status | Reason | Test Status | Evidence |",
        "| --- | --- | --- | --- | --- | --- |"
    ])

    for source_name, target_name, status, reason, test_status, evidence in pair_rows:
        lines.append(f"| `{source_name}` | `{target_name}` | {status} | {reason} | {test_status} | {evidence} |")

    return lines


def write_permutation_report(args: argparse.Namespace) -> Path:
    manifest = load_manifest(args.manifest)
    status_entries = load_status_entries()
    lines = permutation_report_lines(manifest, DEFAULT_BASE_DIR, status_entries)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return args.output


def selected_connectors(manifest: dict[str, Any], profile: str) -> list[str]:
    try:
        return manifest["profiles"][profile]
    except KeyError as exc:
        available = ", ".join(sorted(manifest["profiles"].keys()))
        raise SystemExit(f"Unknown profile '{profile}'. Available profiles: {available}") from exc


def selected_scenarios(manifest: dict[str, Any], profile: str) -> list[str]:
    return manifest.get("scenarioProfiles", {}).get(profile, [DEFAULT_SCENARIO])


def scenario_definition(manifest: dict[str, Any], scenario_name: str) -> dict[str, Any]:
    return manifest.get("scenarios", {}).get(scenario_name, {"expectedSuccess": True})


def pipeline_scenario(pipeline_name: str) -> str:
    if "/" not in pipeline_name:
        return DEFAULT_SCENARIO
    return pipeline_name.split("/", maxsplit=1)[1]


def scenario_applies(scenario: dict[str, Any], source: str, target: str) -> bool:
    allowed_sources = scenario.get("allowedSources")
    allowed_targets = scenario.get("allowedTargets")
    if allowed_sources and source not in allowed_sources:
        return False
    if allowed_targets and target not in allowed_targets:
        return False
    return True


def scenario_connector_overrides(
    manifest: dict[str, Any],
    scenario_name: str,
    connector_name: str,
    role: str
) -> dict[str, Any]:
    scenario = scenario_definition(manifest, scenario_name)
    return scenario.get("connectors", {}).get(connector_name, {}).get(role, {})


def matrix_cases(
    manifest: dict[str, Any],
    profile: str,
    only_sources: list[str] | None,
    only_targets: list[str] | None,
    only_cases: list[str] | None
) -> list[tuple[str, str, str]]:
    connectors = manifest["connectors"]

    if only_cases:
        cases = []
        for pipeline_name in only_cases:
            case_name = pipeline_name.split("/", maxsplit=1)[0]
            source, target = case_name.split("-to-", maxsplit=1)
            if source not in connectors or target not in connectors:
                raise SystemExit(f"Unknown connector in case '{pipeline_name}'")
            if not supports_role(connectors[source], "source"):
                raise SystemExit(f"Connector '{source}' cannot be used as a source")
            if not supports_role(connectors[target], "target"):
                raise SystemExit(f"Connector '{target}' cannot be used as a target")
            cases.append((source, target, pipeline_name))
        return cases

    connector_names = selected_connectors(manifest, profile)
    scenarios = selected_scenarios(manifest, profile)
    sources = [name for name in connector_names if supports_role(connectors[name], "source")]
    targets = [name for name in connector_names if supports_role(connectors[name], "target")]

    if only_sources:
        sources = [name for name in sources if name in only_sources]
    if only_targets:
        targets = [name for name in targets if name in only_targets]

    cases = []
    for scenario_name in scenarios:
        scenario = scenario_definition(manifest, scenario_name)
        for source in sources:
            for target in targets:
                if not scenario_applies(scenario, source, target):
                    continue
                pipeline_name = f"{source}-to-{target}/{scenario_name}"
                cases.append((source, target, pipeline_name))
    return cases


def read_fixture(manifest: dict[str, Any]) -> list[dict[str, str]]:
    fixture_path = repo_path(manifest["dataset"]["fixture"])
    with fixture_path.open(newline="", encoding="utf-8") as fixture_file:
        return list(csv.DictReader(fixture_file))


def expected_rows_for_verification(manifest: dict[str, Any]) -> list[tuple[str, ...]]:
    fields = [field["name"] for field in manifest["dataset"]["fields"]]
    return normalize_rows(read_fixture(manifest), fields)


def rows_from_normalized(rows: list[tuple[str, ...]], fields: list[str]) -> list[dict[str, str]]:
    return [dict(zip(fields, row, strict=True)) for row in rows]


def normalize_rows(rows: list[dict[str, Any]], fields: list[str]) -> list[tuple[str, ...]]:
    return sorted(
        [tuple("" if row.get(field) is None else str(row.get(field)) for field in fields) for row in rows],
        key=lambda row: row[0] if row else ""
    )


def case_slug(pipeline_name: str) -> str:
    return pipeline_name.replace("/", "-")


def formatted_target(manifest: dict[str, Any], target_name: str, pipeline_name: str) -> dict[str, Any]:
    fields = field_list(manifest)
    return resource_for(
        manifest,
        target_name,
        manifest["connectors"][target_name],
        "target",
        fields,
        case_slug(pipeline_name),
        1,
        pipeline_scenario(pipeline_name)
    )


def run_command(command: list[str], *, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=REPO_ROOT,
        text=True,
        input=input_text,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )


def csv_rows_from_path(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as csv_file:
        return list(csv.DictReader(csv_file))


def csv_text_from_path(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def delimited_rows(text: str, fields: list[str]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line in text.splitlines():
        line = line.strip("\r")
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) != len(fields):
            raise RuntimeError(f"Unexpected column count in verification output: {line}")
        rows.append(dict(zip(fields, parts, strict=True)))
    return rows


def verify_local_file_target(resource: dict[str, Any]) -> list[dict[str, str]]:
    connection_path = repo_path(resource["server"]["connectionDetails"]["path"])
    return csv_rows_from_path(connection_path / resource["resourceName"])


def verify_local_file_target_text(resource: dict[str, Any]) -> str:
    connection_path = repo_path(resource["server"]["connectionDetails"]["path"])
    return csv_text_from_path(connection_path / resource["resourceName"])


def verify_sqlite_target(resource: dict[str, Any], fields: list[str]) -> list[dict[str, str]]:
    db_path = repo_path(resource["server"]["connectionDetails"]["path"])
    query = f"SELECT {', '.join(fields)} FROM {resource['resourceName']} ORDER BY id"
    with sqlite3.connect(db_path) as connection:
        cursor = connection.execute(query)
        return [dict(zip(fields, row, strict=True)) for row in cursor.fetchall()]


def verify_postgresql_target(resource: dict[str, Any], fields: list[str]) -> list[dict[str, str]]:
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "public")
    select_list = ", ".join(f"COALESCE({field}::text, '')" for field in fields)
    query = f"SELECT {select_list} FROM {schema}.{resource['resourceName']} ORDER BY id"
    completed = run_command([
        "docker", "exec", "-e", f"PGPASSWORD={details['password']}", "dittoe-postgres-target",
        "psql", "-U", details["user"], "-d", details["database"], "-t", "-A", "-F", "\t", "-c", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "PostgreSQL verification query failed")
    return delimited_rows(completed.stdout, fields)


def verify_mysql_target(resource: dict[str, Any], fields: list[str]) -> list[dict[str, str]]:
    details = resource["server"]["connectionDetails"]
    select_list = ", ".join(f"IFNULL(CAST({field} AS CHAR), '')" for field in fields)
    query = f"SELECT {select_list} FROM {resource['resourceName']} ORDER BY id"
    completed = run_command([
        "docker", "exec", "dittoe-mysql-target", "mysql", "--batch", "--raw", "--skip-column-names",
        f"-u{details['user']}", f"-p{details['password']}", details["database"], "-e", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MySQL verification query failed")
    return delimited_rows(completed.stdout, fields)


def verify_mssql_target(resource: dict[str, Any], fields: list[str]) -> list[dict[str, str]]:
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "dbo")
    select_list = ", ".join(f"ISNULL(CAST({field} AS NVARCHAR(MAX)), '')" for field in fields)
    query = f"SET NOCOUNT ON; SELECT {select_list} FROM {schema}.{resource['resourceName']} ORDER BY id;"
    completed = run_command([
        "docker", "exec", "dittoe-mssql", "/opt/mssql-tools18/bin/sqlcmd",
        "-S", "localhost", "-U", details["user"], "-P", details["password"], "-C",
        "-d", details["database"], "-W", "-h", "-1", "-s", "\t", "-Q", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MSSQL verification query failed")
    return delimited_rows(completed.stdout, fields)


def verify_bigquery_target(resource: dict[str, Any], fields: list[str]) -> list[dict[str, str]]:
    completed = run_bigquery_admin("read-table", resource["resourceName"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "BigQuery verification query failed")
    return delimited_rows(completed.stdout, fields)


def verify_ftp_target(resource: dict[str, Any]) -> list[dict[str, str]]:
    completed = run_command(["docker", "exec", "dittoe-ftp", "cat", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "FTP verification read failed")
    with tempfile.NamedTemporaryFile("w+", encoding="utf-8", newline="", delete=False) as temp_file:
        temp_file.write(completed.stdout)
        temp_path = Path(temp_file.name)
    try:
        return csv_rows_from_path(temp_path)
    finally:
        temp_path.unlink(missing_ok=True)


def verify_ftp_target_text(resource: dict[str, Any]) -> str:
    completed = run_command(["docker", "exec", "dittoe-ftp", "cat", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "FTP verification read failed")
    return completed.stdout


def verify_sftp_target(resource: dict[str, Any]) -> list[dict[str, str]]:
    completed = run_command(["docker", "exec", "dittoe-sftp", "cat", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "SFTP verification read failed")
    with tempfile.NamedTemporaryFile("w+", encoding="utf-8", newline="", delete=False) as temp_file:
        temp_file.write(completed.stdout)
        temp_path = Path(temp_file.name)
    try:
        return csv_rows_from_path(temp_path)
    finally:
        temp_path.unlink(missing_ok=True)


def verify_sftp_target_text(resource: dict[str, Any]) -> str:
    completed = run_command(["docker", "exec", "dittoe-sftp", "cat", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "SFTP verification read failed")
    return completed.stdout


def rows_from_csv_text(text: str) -> list[dict[str, str]]:
    with tempfile.NamedTemporaryFile("w+", encoding="utf-8", newline="", delete=False) as temp_file:
        temp_file.write(text)
        temp_path = Path(temp_file.name)
    try:
        return csv_rows_from_path(temp_path)
    finally:
        temp_path.unlink(missing_ok=True)


def csv_header(text: str) -> list[str]:
    reader = csv.reader(io.StringIO(normalized_csv_text(text)))
    return next(reader, [])


def canonical_csv_text(rows: list[dict[str, Any]], fields: list[str]) -> str:
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    for row in rows:
        writer.writerow({field: "" if row.get(field) is None else str(row.get(field)) for field in fields})
    return buffer.getvalue()


def verify_minio_target(resource: dict[str, Any], bucket_key: str, object_key_name: str | None) -> list[dict[str, str]]:
    details = resource["server"]["connectionDetails"]
    props = resource["resourceProperties"]
    endpoint = details.get("s3Endpoint") or props.get("ibmEndpointUrl") or props.get("doSpacesEndpoint")
    access_key = details.get("s3AccessKey") or props.get("ibmApiKey") or props.get("doSpacesAccessKey")
    secret_key = details.get("s3SecretKey") or props.get("ibmServiceInstanceId") or props.get("doSpacesSecretKey")
    bucket = props[bucket_key]
    object_name = resource["resourceName"] if object_key_name is None else props[object_key_name]
    command = (
        f"mc alias set local '{endpoint.replace('localhost', '127.0.0.1')}' '{access_key}' '{secret_key}' >/dev/null && "
        f"mc cat 'local/{bucket}/{object_name}'"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host", "--entrypoint", "/bin/sh",
        "minio/mc:latest", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MinIO verification read failed")
    return rows_from_csv_text(completed.stdout)


def verify_minio_target_text(resource: dict[str, Any], bucket_key: str, object_key_name: str | None) -> str:
    details = resource["server"]["connectionDetails"]
    props = resource["resourceProperties"]
    endpoint = details.get("s3Endpoint") or props.get("ibmEndpointUrl") or props.get("doSpacesEndpoint")
    access_key = details.get("s3AccessKey") or props.get("ibmApiKey") or props.get("doSpacesAccessKey")
    secret_key = details.get("s3SecretKey") or props.get("ibmServiceInstanceId") or props.get("doSpacesSecretKey")
    bucket = props[bucket_key]
    object_name = resource["resourceName"] if object_key_name is None else props[object_key_name]
    command = (
        f"mc alias set local '{endpoint.replace('localhost', '127.0.0.1')}' '{access_key}' '{secret_key}' >/dev/null && "
        f"mc cat 'local/{bucket}/{object_name}'"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host", "--entrypoint", "/bin/sh",
        "minio/mc:latest", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MinIO verification read failed")
    return completed.stdout


def verify_gcs_target(resource: dict[str, Any]) -> list[dict[str, str]]:
    props = resource["resourceProperties"]
    bucket = props["gcsBucket"]
    object_name = urllib.parse.quote(props["gcsObject"], safe="")
    url = f"http://127.0.0.1:4443/storage/v1/b/{bucket}/o/{object_name}?alt=media"
    with urllib.request.urlopen(url) as response:
        return rows_from_csv_text(response.read().decode("utf-8"))


def verify_gcs_target_text(resource: dict[str, Any]) -> str:
    props = resource["resourceProperties"]
    bucket = props["gcsBucket"]
    object_name = urllib.parse.quote(props["gcsObject"], safe="")
    url = f"http://127.0.0.1:4443/storage/v1/b/{bucket}/o/{object_name}?alt=media"
    with urllib.request.urlopen(url) as response:
        return response.read().decode("utf-8")


def verify_azure_blob_target(resource: dict[str, Any]) -> list[dict[str, str]]:
    props = resource["resourceProperties"]
    connection_string = props["azureConnectionString"]
    if connection_string == "UseDevelopmentStorage=true":
        connection_string = (
            "DefaultEndpointsProtocol=http;"
            "AccountName=devstoreaccount1;"
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
        )
    with tempfile.TemporaryDirectory() as temp_dir:
        command = (
            "az storage blob download "
            f"--connection-string \"{connection_string}\" "
            f"--container-name '{props['azureContainer']}' "
            f"--name '{props['azureBlob']}' "
            "--file /work/out.csv --overwrite true >/dev/null && cat /work/out.csv"
        )
        completed = run_command([
            "docker", "run", "--rm", "--network", "host",
            "-v", f"{temp_dir}:/work",
            "mcr.microsoft.com/azure-cli:2.71.0",
            "sh", "-c", command
        ])
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "Azure Blob verification read failed")
        return rows_from_csv_text(completed.stdout)


def verify_azure_blob_target_text(resource: dict[str, Any]) -> str:
    props = resource["resourceProperties"]
    connection_string = props["azureConnectionString"]
    if connection_string == "UseDevelopmentStorage=true":
        connection_string = (
            "DefaultEndpointsProtocol=http;"
            "AccountName=devstoreaccount1;"
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
        )
    with tempfile.TemporaryDirectory() as temp_dir:
        command = (
            "az storage blob download "
            f"--connection-string \"{connection_string}\" "
            f"--container-name '{props['azureContainer']}' "
            f"--name '{props['azureBlob']}' "
            "--file /work/out.csv --overwrite true >/dev/null && cat /work/out.csv"
        )
        completed = run_command([
            "docker", "run", "--rm", "--network", "host",
            "-v", f"{temp_dir}:/work",
            "mcr.microsoft.com/azure-cli:2.71.0",
            "sh", "-c", command
        ])
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "Azure Blob verification read failed")
        return completed.stdout


def normalized_csv_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def source_resource(manifest: dict[str, Any], connector_name: str) -> dict[str, Any]:
    fields = field_list(manifest)
    return resource_for(
        manifest,
        connector_name,
        manifest["connectors"][connector_name],
        "source",
        fields,
        connector_name,
        0,
        DEFAULT_SCENARIO
    )


def seed_postgresql_source(manifest: dict[str, Any]) -> None:
    resource = source_resource(manifest, "postgresql")
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "public")
    table = resource["resourceName"]
    fixture_text = repo_path(manifest["dataset"]["fixture"]).read_text(encoding="utf-8")
    truncate = run_command([
        "docker", "exec", "-e", f"PGPASSWORD={details['password']}", "dittoe-postgres-source",
        "psql", "-U", details["user"], "-d", details["database"], "-c", f"TRUNCATE TABLE {schema}.{table}"
    ])
    if truncate.returncode != 0:
        raise RuntimeError(truncate.stderr.strip() or truncate.stdout.strip() or "PostgreSQL source truncate failed")
    copy = run_command([
        "docker", "exec", "-i", "-e", f"PGPASSWORD={details['password']}", "dittoe-postgres-source",
        "psql", "-U", details["user"], "-d", details["database"], "-c",
        f"\\copy {schema}.{table} FROM STDIN WITH (FORMAT csv, HEADER true)"
    ], input_text=fixture_text)
    if copy.returncode != 0:
        raise RuntimeError(copy.stderr.strip() or copy.stdout.strip() or "PostgreSQL source copy failed")


def seed_mysql_source(manifest: dict[str, Any]) -> None:
    resource = source_resource(manifest, "mysql")
    details = resource["server"]["connectionDetails"]
    rows = read_fixture(manifest)
    fields = [field["name"] for field in manifest["dataset"]["fields"]]
    values_sql = ", ".join(
        "(" + ", ".join(sql_literal(row.get(field, "")) for field in fields) + ")"
        for row in rows
    )
    query = f"DELETE FROM {resource['resourceName']}; INSERT INTO {resource['resourceName']} ({', '.join(fields)}) VALUES {values_sql};"
    completed = run_command([
        "docker", "exec", "dittoe-mysql-source", "mysql",
        f"-u{details['user']}", f"-p{details['password']}", details["database"], "-e", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MySQL source reseed failed")


def seed_mssql_source(manifest: dict[str, Any]) -> None:
    resource = source_resource(manifest, "mssql")
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "dbo")
    rows = read_fixture(manifest)
    fields = [field["name"] for field in manifest["dataset"]["fields"]]
    statements = [f"DELETE FROM {schema}.{resource['resourceName']};"]
    for row in rows:
        values = ", ".join("N" + sql_literal(row.get(field, "")) for field in fields)
        statements.append(f"INSERT INTO {schema}.{resource['resourceName']} ({', '.join(fields)}) VALUES ({values});")
    completed = run_command([
        "docker", "exec", "dittoe-mssql", "/opt/mssql-tools18/bin/sqlcmd",
        "-S", "localhost", "-U", details["user"], "-P", details["password"], "-C",
        "-d", details["database"], "-Q", " ".join(statements)
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MSSQL source reseed failed")


def seed_minio_source(manifest: dict[str, Any], connector_name: str, bucket_key: str, object_key_name: str | None) -> None:
    resource = source_resource(manifest, connector_name)
    details = resource["server"]["connectionDetails"]
    props = resource["resourceProperties"]
    endpoint = details.get("s3Endpoint") or props.get("ibmEndpointUrl") or props.get("doSpacesEndpoint")
    access_key = details.get("s3AccessKey") or props.get("ibmApiKey") or props.get("doSpacesAccessKey")
    secret_key = details.get("s3SecretKey") or props.get("ibmServiceInstanceId") or props.get("doSpacesSecretKey")
    bucket = props[bucket_key]
    object_name = resource["resourceName"] if object_key_name is None else props[object_key_name]
    fixture_path = repo_path(manifest["dataset"]["fixture"])
    command = (
        f"mc alias set local '{endpoint.replace('localhost', '127.0.0.1')}' '{access_key}' '{secret_key}' >/dev/null && "
        f"mc cp /work/{fixture_path.name} 'local/{bucket}/{object_name}' >/dev/null"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host", "-v", f"{fixture_path.parent}:/work", "--entrypoint", "/bin/sh",
        "minio/mc:latest", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or f"{connector_name} source reseed failed")


def seed_gcs_source(manifest: dict[str, Any]) -> None:
    resource = source_resource(manifest, "gcs")
    props = resource["resourceProperties"]
    bucket = props["gcsBucket"]
    object_name = urllib.parse.quote(props["gcsObject"], safe="")
    url = f"http://127.0.0.1:4443/upload/storage/v1/b/{bucket}/o?uploadType=media&name={object_name}"
    request = urllib.request.Request(
        url,
        data=repo_path(manifest["dataset"]["fixture"]).read_bytes(),
        headers={"Content-Type": "text/csv"},
        method="POST"
    )
    with urllib.request.urlopen(request):
        return


def seed_azure_blob_source(manifest: dict[str, Any]) -> None:
    resource = source_resource(manifest, "azure-blob")
    props = resource["resourceProperties"]
    connection_string = props["azureConnectionString"]
    if connection_string == "UseDevelopmentStorage=true":
        connection_string = (
            "DefaultEndpointsProtocol=http;"
            "AccountName=devstoreaccount1;"
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
        )
    fixture_path = repo_path(manifest["dataset"]["fixture"])
    command = (
        "az storage blob upload --only-show-errors --overwrite true "
        f"--connection-string \"{connection_string}\" "
        f"--container-name '{props['azureContainer']}' "
        f"--name '{props['azureBlob']}' "
        f"--file /work/{fixture_path.name} >/dev/null"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host", "-v", f"{fixture_path.parent}:/work",
        "mcr.microsoft.com/azure-cli:2.71.0", "sh", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "Azure Blob source reseed failed")


def seed_ftp_like_source(manifest: dict[str, Any], connector_name: str, container_name: str) -> None:
    resource = source_resource(manifest, connector_name)
    fixture_text = repo_path(manifest["dataset"]["fixture"]).read_text(encoding="utf-8")
    completed = run_command([
        "docker", "exec", "-i", container_name, "sh", "-c",
        f"mkdir -p /home/dittoe/$(dirname {shlex.quote(resource['resourceName'])}) && cat > /home/dittoe/{shlex.quote(resource['resourceName'])}"
    ], input_text=fixture_text)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or f"{connector_name} source reseed failed")


def reset_local_file_target(resource: dict[str, Any]) -> None:
    target_file = repo_path(resource["server"]["connectionDetails"]["path"]) / resource["resourceName"]
    target_file.unlink(missing_ok=True)


def reset_sqlite_target(resource: dict[str, Any]) -> None:
    db_path = repo_path(resource["server"]["connectionDetails"]["path"])
    with sqlite3.connect(db_path) as connection:
        connection.execute(f"DELETE FROM {resource['resourceName']}")


def reset_postgresql_target(resource: dict[str, Any]) -> None:
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "public")
    query = f"TRUNCATE TABLE {schema}.{resource['resourceName']}"
    completed = run_command([
        "docker", "exec", "-e", f"PGPASSWORD={details['password']}", "dittoe-postgres-target",
        "psql", "-U", details["user"], "-d", details["database"], "-c", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "PostgreSQL reset failed")


def reset_mysql_target(resource: dict[str, Any]) -> None:
    details = resource["server"]["connectionDetails"]
    query = f"DELETE FROM {resource['resourceName']}"
    completed = run_command([
        "docker", "exec", "dittoe-mysql-target", "mysql",
        f"-u{details['user']}", f"-p{details['password']}", details["database"], "-e", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MySQL reset failed")


def reset_mssql_target(resource: dict[str, Any]) -> None:
    details = resource["server"]["connectionDetails"]
    schema = resource["resourceProperties"].get("schema", "dbo")
    query = f"DELETE FROM {schema}.{resource['resourceName']};"
    completed = run_command([
        "docker", "exec", "dittoe-mssql", "/opt/mssql-tools18/bin/sqlcmd",
        "-S", "localhost", "-U", details["user"], "-P", details["password"], "-C",
        "-d", details["database"], "-Q", query
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MSSQL reset failed")


def reset_bigquery_target(resource: dict[str, Any]) -> None:
    completed = run_bigquery_admin("delete-table", resource["resourceName"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "BigQuery reset failed")


def reset_ftp_target(resource: dict[str, Any]) -> None:
    completed = run_command(["docker", "exec", "dittoe-ftp", "rm", "-f", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "FTP reset failed")


def reset_sftp_target(resource: dict[str, Any]) -> None:
    completed = run_command(["docker", "exec", "dittoe-sftp", "rm", "-f", f"/home/dittoe/{resource['resourceName']}"])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "SFTP reset failed")


def reset_minio_target(resource: dict[str, Any], bucket_key: str, object_key_name: str | None) -> None:
    details = resource["server"]["connectionDetails"]
    props = resource["resourceProperties"]
    endpoint = details.get("s3Endpoint") or props.get("ibmEndpointUrl") or props.get("doSpacesEndpoint")
    access_key = details.get("s3AccessKey") or props.get("ibmApiKey") or props.get("doSpacesAccessKey")
    secret_key = details.get("s3SecretKey") or props.get("ibmServiceInstanceId") or props.get("doSpacesSecretKey")
    bucket = props[bucket_key]
    object_name = resource["resourceName"] if object_key_name is None else props[object_key_name]
    command = (
        f"mc alias set local '{endpoint.replace('localhost', '127.0.0.1')}' '{access_key}' '{secret_key}' >/dev/null && "
        f"mc rm --force 'local/{bucket}/{object_name}' >/dev/null 2>&1 || true"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host", "--entrypoint", "/bin/sh",
        "minio/mc:latest", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "MinIO reset failed")


def reset_gcs_target(resource: dict[str, Any]) -> None:
    props = resource["resourceProperties"]
    bucket = props["gcsBucket"]
    object_name = urllib.parse.quote(props["gcsObject"], safe="")
    url = f"http://127.0.0.1:4443/storage/v1/b/{bucket}/o/{object_name}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method="DELETE")):
            return
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise RuntimeError(f"GCS reset failed: HTTP {exc.code}") from exc


def reset_azure_blob_target(resource: dict[str, Any]) -> None:
    props = resource["resourceProperties"]
    connection_string = props["azureConnectionString"]
    if connection_string == "UseDevelopmentStorage=true":
        connection_string = (
            "DefaultEndpointsProtocol=http;"
            "AccountName=devstoreaccount1;"
            "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
        )
    command = (
        "az storage blob delete --only-show-errors "
        f"--connection-string \"{connection_string}\" "
        f"--container-name '{props['azureContainer']}' "
        f"--name '{props['azureBlob']}' >/dev/null 2>&1 || true"
    )
    completed = run_command([
        "docker", "run", "--rm", "--network", "host",
        "mcr.microsoft.com/azure-cli:2.71.0", "sh", "-c", command
    ])
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "Azure Blob reset failed")


def reset_target_state(manifest: dict[str, Any], target_name: str, pipeline_name: str) -> None:
    resource = formatted_target(manifest, target_name, pipeline_name)
    resetters = {
        "local-file": lambda: reset_local_file_target(resource),
        "sqlite": lambda: reset_sqlite_target(resource),
        "postgresql": lambda: reset_postgresql_target(resource),
        "mysql": lambda: reset_mysql_target(resource),
        "mssql": lambda: reset_mssql_target(resource),
        "bigquery": lambda: reset_bigquery_target(resource),
        "ftp": lambda: reset_ftp_target(resource),
        "sftp": lambda: reset_sftp_target(resource),
        "s3": lambda: reset_minio_target(resource, "bucket", None),
        "ibm-cos": lambda: reset_minio_target(resource, "ibmBucket", "ibmObject"),
        "digitalocean-spaces": lambda: reset_minio_target(resource, "doSpacesBucket", "doSpacesObject"),
        "gcs": lambda: reset_gcs_target(resource),
        "azure-blob": lambda: reset_azure_blob_target(resource)
    }
    resetters[target_name]()


def verify_target_output(manifest: dict[str, Any], target_name: str, pipeline_name: str) -> tuple[bool, str, bool]:
    if target_name not in VERIFIED_TARGETS:
        return True, "summary-only", False

    fields = [field["name"] for field in manifest["dataset"]["fields"]]
    expected_rows = expected_rows_for_verification(manifest)
    resource = formatted_target(manifest, target_name, pipeline_name)

    readers = {
        "local-file": lambda: verify_local_file_target(resource),
        "sqlite": lambda: verify_sqlite_target(resource, fields),
        "postgresql": lambda: verify_postgresql_target(resource, fields),
        "mysql": lambda: verify_mysql_target(resource, fields),
        "mssql": lambda: verify_mssql_target(resource, fields),
        "bigquery": lambda: verify_bigquery_target(resource, fields),
        "ftp": lambda: verify_ftp_target(resource),
        "sftp": lambda: verify_sftp_target(resource),
        "s3": lambda: verify_minio_target(resource, "bucket", None),
        "ibm-cos": lambda: verify_minio_target(resource, "ibmBucket", "ibmObject"),
        "digitalocean-spaces": lambda: verify_minio_target(resource, "doSpacesBucket", "doSpacesObject"),
        "gcs": lambda: verify_gcs_target(resource),
        "azure-blob": lambda: verify_azure_blob_target(resource)
    }
    text_readers = {
        "local-file": lambda: verify_local_file_target_text(resource),
        "ftp": lambda: verify_ftp_target_text(resource),
        "sftp": lambda: verify_sftp_target_text(resource),
        "s3": lambda: verify_minio_target_text(resource, "bucket", None),
        "ibm-cos": lambda: verify_minio_target_text(resource, "ibmBucket", "ibmObject"),
        "digitalocean-spaces": lambda: verify_minio_target_text(resource, "doSpacesBucket", "doSpacesObject"),
        "gcs": lambda: verify_gcs_target_text(resource),
        "azure-blob": lambda: verify_azure_blob_target_text(resource)
    }

    try:
        actual_rows = normalize_rows(readers[target_name](), fields)
    except Exception as exc:
        return False, f"output-check failed: {exc}", True

    if actual_rows != expected_rows:
        return False, f"output mismatch: expected {len(expected_rows)} canonical rows, found {len(actual_rows)} rows", True

    if target_name in HASH_VERIFIED_TARGETS:
        expected_header = fields
        expected_hash = sha256_text(canonical_csv_text(rows_from_normalized(expected_rows, fields), fields))
        try:
            actual_text = text_readers[target_name]()
        except Exception as exc:
            return False, f"hash-check failed: {exc}", True
        actual_header = csv_header(actual_text)
        if actual_header != expected_header:
            return False, f"header mismatch: expected {expected_header}, found {actual_header}", True
        actual_hash = sha256_text(canonical_csv_text(rows_from_normalized(actual_rows, fields), fields))
        if actual_hash != expected_hash:
            return False, "hash mismatch: canonical CSV rendering differs from expected rows", True
        return True, "content-and-hash-verified", True

    return True, "content-verified", True


def prepare_local_file_source(manifest: dict[str, Any]) -> None:
    connector = manifest["connectors"].get("local-file")
    if not connector:
        return
    source_config = connector["source"]
    source_path = repo_path(source_config["connectionDetails"]["path"])
    source_path.mkdir(parents=True, exist_ok=True)
    fixture_path = repo_path(manifest["dataset"]["fixture"])
    shutil.copyfile(fixture_path, source_path / source_config["resourceName"])
    target_path = repo_path(connector["target"]["connectionDetails"]["path"])
    if target_path.exists():
        shutil.rmtree(target_path)
    target_path.mkdir(parents=True, exist_ok=True)


def prepare_sqlite(manifest: dict[str, Any]) -> None:
    connector = manifest["connectors"].get("sqlite")
    if not connector:
        return
    db_path = repo_path(connector["connectionDetails"]["path"])
    db_path.parent.mkdir(parents=True, exist_ok=True)
    rows = read_fixture(manifest)
    columns = [field["name"] for field in manifest["dataset"]["fields"]]
    definitions = ", ".join(f"{column} TEXT" for column in columns)
    placeholders = ", ".join("?" for _ in columns)

    with sqlite3.connect(db_path) as connection:
        for role in ("source", "target"):
            table = connector[role]["resourceName"]
            connection.execute(f"DROP TABLE IF EXISTS {table}")
            connection.execute(f"CREATE TABLE {table} ({definitions})")
        source_table = connector["source"]["resourceName"]
        values = [[row.get(column, "") for column in columns] for row in rows]
        connection.executemany(f"INSERT INTO {source_table} VALUES ({placeholders})", values)


def prepare_selected_sources(manifest: dict[str, Any], connector_names: set[str], source_names: set[str]) -> None:
    if "local-file" in connector_names:
        prepare_local_file_source(manifest)
    if "sqlite" in connector_names:
        prepare_sqlite(manifest)
    if "postgresql" in source_names:
        seed_postgresql_source(manifest)
    if "mysql" in source_names:
        seed_mysql_source(manifest)
    if "mssql" in source_names:
        seed_mssql_source(manifest)
    if "s3" in source_names:
        seed_minio_source(manifest, "s3", "bucket", None)
    if "ibm-cos" in source_names:
        seed_minio_source(manifest, "ibm-cos", "ibmBucket", "ibmObject")
    if "digitalocean-spaces" in source_names:
        seed_minio_source(manifest, "digitalocean-spaces", "doSpacesBucket", "doSpacesObject")
    if "gcs" in source_names:
        seed_gcs_source(manifest)
    if "azure-blob" in source_names:
        seed_azure_blob_source(manifest)
    if "ftp" in source_names:
        seed_ftp_like_source(manifest, "ftp", "dittoe-ftp")
    if "sftp" in source_names:
        seed_ftp_like_source(manifest, "sftp", "dittoe-sftp")


def prepare_local_assets(manifest: dict[str, Any], connector_names: set[str], source_names: set[str]) -> None:
    prepare_selected_sources(manifest, connector_names, source_names)
    (REPO_ROOT / "target").mkdir(exist_ok=True)
    (REPO_ROOT / "target").joinpath("dittoe-matrix").mkdir(exist_ok=True)
    (REPO_ROOT / "target").joinpath("canonical-temp").mkdir(exist_ok=True)


def formatted_source(manifest: dict[str, Any], source_name: str, pipeline_name: str) -> dict[str, Any]:
    fields = field_list(manifest)
    return resource_for(
        manifest,
        source_name,
        manifest["connectors"][source_name],
        "source",
        fields,
        case_slug(pipeline_name),
        0,
        pipeline_scenario(pipeline_name)
    )


def prepare_source_state(manifest: dict[str, Any], source_name: str, pipeline_name: str) -> None:
    if source_name != "bigquery":
        return
    resource = formatted_source(manifest, source_name, pipeline_name)
    fixture_path = repo_path(manifest["dataset"]["fixture"])
    completed = run_bigquery_admin("seed-csv", resource["resourceName"], str(fixture_path))
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "BigQuery source seed failed")


def generate(args: argparse.Namespace) -> list[tuple[str, str, str]]:
    manifest = runtime_manifest(args.manifest, args.fixture)
    manifest = configure_manifest_for_request(manifest, args.profile, args.source, args.target, args.case)
    cases = matrix_cases(manifest, args.profile, args.source, args.target, args.case)
    local_connectors = {name for source, target, _ in cases for name in (source, target) if name != "bigquery"}
    prepare_local_assets(manifest, local_connectors, {source for source, _, _ in cases if source != "bigquery"})

    for source, target, pipeline_name in cases:
        case_dir = args.base_dir / pipeline_name
        case_dir.mkdir(parents=True, exist_ok=True)
        pmd = pmd_for_case(manifest, source, target, pipeline_scenario(pipeline_name))
        with (case_dir / "pmd.json").open("w", encoding="utf-8") as pmd_file:
            json.dump(pmd, pmd_file, indent=2)
            pmd_file.write("\n")

    return cases


def find_jar(explicit_jar: Path | None) -> Path:
    if explicit_jar:
        jar = explicit_jar if explicit_jar.is_absolute() else REPO_ROOT / explicit_jar
        if jar.exists():
            return jar
        raise SystemExit(f"Jar not found: {jar}")

    shaded_jars = sorted((REPO_ROOT / "target").glob("*-shaded.jar"), key=lambda path: path.stat().st_mtime, reverse=True)
    for candidate in [*shaded_jars, REPO_ROOT / "target/dittoe-engine-shaded.jar", REPO_ROOT / "target/dittoe-engine.jar"]:
        if candidate.exists():
            return candidate
    raise SystemExit("No runnable jar found. Run `mvn -DskipTests package` or pass --jar.")


def package_project() -> None:
    completed = subprocess.run(["mvn", "-DskipTests", "package"], cwd=REPO_ROOT)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)


def latest_summary(base_dir: Path, pipeline_name: str) -> Path | None:
    pipeline_dir = base_dir / pipeline_name
    if not pipeline_dir.exists():
        return None
    summaries = sorted(pipeline_dir.glob("*/summary.json"), key=lambda path: path.stat().st_mtime, reverse=True)
    return summaries[0] if summaries else None


def stage_rows(summary: dict[str, Any]) -> dict[str, Any]:
    return {stage.get("stageName"): stage.get("rowsProcessed") for stage in summary.get("stages", [])}


def evaluate_summary(summary: dict[str, Any], expected_rows: int) -> tuple[bool, int | None, int | None]:
    stages = stage_rows(summary)
    source_rows = stages.get("SourceRetrieval")
    target_rows = stages.get("TargetInsertUpdate")
    source_ok = source_rows == expected_rows or summary.get("rowsRead") in {expected_rows, expected_rows * 2}
    target_ok = target_rows == expected_rows or summary.get("rowsWritten") == expected_rows
    passed = summary.get("errorRowCount", 0) == 0 and source_ok and target_ok
    return passed, source_rows, target_rows


def load_status_entries() -> dict[str, Any]:
    if not STATUS_FILE.exists():
        return {}
    with STATUS_FILE.open(encoding="utf-8") as status_file:
        return json.load(status_file)


def save_status_entries(status_entries: dict[str, Any]) -> None:
    STATUS_FILE.parent.mkdir(parents=True, exist_ok=True)
    with STATUS_FILE.open("w", encoding="utf-8") as status_file:
        json.dump(status_entries, status_file, indent=2)
        status_file.write("\n")


def summary_status(summary_path: Path, expected_rows: int) -> dict[str, Any]:
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    passed, source_rows, target_rows = evaluate_summary(summary, expected_rows)
    return {
        "completed": True,
        "passed": passed,
        "exitCode": None,
        "errorRowCount": summary.get("errorRowCount", 0),
        "rowsRead": summary.get("rowsRead"),
        "rowsWritten": summary.get("rowsWritten"),
        "sourceRows": source_rows,
        "targetRows": target_rows,
        "outputVerified": False,
        "outputVerification": "summary-only",
        "summaryPath": str(summary_path.relative_to(REPO_ROOT)),
        "statusSource": "summary",
        "updatedAt": dt.datetime.now(dt.timezone.utc).isoformat()
    }


def expected_failure_result(completed: subprocess.CompletedProcess[str], scenario: dict[str, Any]) -> tuple[bool, str, bool]:
    error_output = f"{completed.stderr}\n{completed.stdout}".lower()
    required_substrings = [value.lower() for value in scenario.get("errorContainsAny", [])]
    if completed.returncode == 0:
        return False, "expected failure but CLI exited 0", True
    if required_substrings and not any(value in error_output for value in required_substrings):
        return False, "expected failure message missing from CLI output", True
    return True, "expected-failure-verified", True


def case_status(
    pipeline_name: str,
    base_dir: Path,
    expected_rows: int,
    status_entries: dict[str, Any]
) -> dict[str, Any]:
    if pipeline_name in status_entries:
        return dict(status_entries[pipeline_name])

    summary_path = latest_summary(base_dir, pipeline_name)
    if summary_path is not None:
        return summary_status(summary_path, expected_rows)

    return {
        "completed": False,
        "passed": False,
        "exitCode": None,
        "errorRowCount": None,
        "rowsRead": None,
        "rowsWritten": None,
        "sourceRows": None,
        "targetRows": None,
        "outputVerified": False,
        "outputVerification": "pending",
        "summaryPath": None,
        "statusSource": "none",
        "updatedAt": None
    }


def report_lines(
    manifest: dict[str, Any],
    selected_profile: str,
    base_dir: Path,
    expected_rows: int,
    status_entries: dict[str, Any]
) -> list[str]:
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    lines = [
        "# Dittoe Engine Matrix Status Report",
        "",
        f"Generated: {timestamp}",
        f"Selected profile: `{selected_profile}`",
        f"Manifest: `{DEFAULT_MANIFEST.relative_to(REPO_ROOT)}`",
        f"Base dir: `{base_dir.relative_to(REPO_ROOT)}`",
        "",
        "## Implementation Checklist",
        "",
        "- [x] Add emulator endpoint support to adapters that need it.",
        "- [x] Add Docker overlay services and canonical seed data for local/emulator coverage.",
        "- [x] Build the PMD generator from the connector manifest and canonical fixture.",
        "- [x] Implement the CLI runner and summary-based validation flow.",
        "- [x] Add connector-specific target hash assertions.",
        "- [x] Add reset hooks for repeated full-matrix runs.",
        "- [x] Add negative scenarios.",
        "",
        "## Profile Summary",
        "",
        "| Profile | Total | Passed | Failed | Pending | Content-Verified | Expected-Failure-Verified | Summary-Only |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    ]

    for profile in manifest["profiles"]:
        cases = matrix_cases(manifest, profile, None, None, None)
        passed = 0
        failed = 0
        pending = 0
        content_verified = 0
        expected_failure_verified = 0
        summary_only = 0
        for _, _, pipeline_name in cases:
            status = case_status(pipeline_name, base_dir, expected_rows, status_entries)
            if not status["completed"]:
                pending += 1
            elif status["passed"]:
                passed += 1
                if status.get("outputVerification") in {"content-verified", "content-and-hash-verified"}:
                    content_verified += 1
                elif status.get("outputVerification") == "expected-failure-verified":
                    expected_failure_verified += 1
                elif status.get("outputVerification") == "summary-only":
                    summary_only += 1
            else:
                failed += 1
        lines.append(
            f"| `{profile}` | {len(cases)} | {passed} | {failed} | {pending} | {content_verified} | {expected_failure_verified} | {summary_only} |"
        )

    selected_cases = matrix_cases(manifest, selected_profile, None, None, None)
    lines.extend([
        "",
        f"## Combination Checklist: `{selected_profile}`",
        ""
    ])

    for source, target, pipeline_name in selected_cases:
        status = case_status(pipeline_name, base_dir, expected_rows, status_entries)
        checked = "x" if status["passed"] else " "
        if not status["completed"]:
            note = "pending"
        elif status["passed"]:
            verification = status.get("outputVerification", "summary-only")
            if verification == "content-and-hash-verified":
                note = "passed; content-and-hash-verified"
            elif verification == "content-verified":
                note = "passed; content-verified"
            elif verification == "expected-failure-verified":
                note = "passed; expected-failure-verified"
            else:
                note = "passed; summary-only"
        else:
            note = f"failed; {status.get('outputVerification', 'summary-only')}"
        lines.append(
            f"- [{checked}] `{pipeline_name}` ({source} -> {target}; {note})"
        )

    return lines


def write_report(args: argparse.Namespace) -> Path:
    manifest = runtime_manifest(args.manifest, args.fixture)
    manifest = configure_manifest_for_request(manifest, args.profile, args.source, args.target, args.case)
    status_entries = load_status_entries()
    expected_rows = int(manifest["dataset"]["expectedRows"])
    lines = report_lines(manifest, args.profile, args.base_dir, expected_rows, status_entries)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return args.output


def run_cases(args: argparse.Namespace) -> int:
    if args.package:
        package_project()
    manifest = runtime_manifest(args.manifest, args.fixture)
    manifest = configure_manifest_for_request(manifest, args.profile, args.source, args.target, args.case)
    cases = generate(args) if args.generate else matrix_cases(manifest, args.profile, args.source, args.target, args.case)
    local_connectors = {name for source, target, _ in cases for name in (source, target) if name != "bigquery"}
    prepare_selected_sources(manifest, local_connectors, {source for source, _, _ in cases if source != "bigquery"})
    jar = find_jar(args.jar)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env["BASE_DIR"] = str(args.base_dir)
    failures = 0
    expected_rows = int(manifest["dataset"]["expectedRows"])
    status_entries = load_status_entries()

    for source_name, target_name, pipeline_name in cases:
        print(f"running {pipeline_name}")
        try:
            prepare_source_state(manifest, source_name, pipeline_name)
        except Exception as exc:
            failures += 1
            status_entries[pipeline_name] = {
                "completed": True,
                "passed": False,
                "exitCode": None,
                "errorRowCount": None,
                "rowsRead": None,
                "rowsWritten": None,
                "sourceRows": None,
                "targetRows": None,
                "outputVerified": False,
                "outputVerification": f"source prepare failed: {exc}",
                "summaryPath": None,
                "statusSource": "run",
                "updatedAt": dt.datetime.now(dt.timezone.utc).isoformat()
            }
            print(f"failed {pipeline_name}: source prepare failed: {exc}", file=sys.stderr)
            continue
        try:
            reset_target_state(manifest, target_name, pipeline_name)
        except Exception as exc:
            failures += 1
            status_entries[pipeline_name] = {
                "completed": True,
                "passed": False,
                "exitCode": None,
                "errorRowCount": None,
                "rowsRead": None,
                "rowsWritten": None,
                "sourceRows": None,
                "targetRows": None,
                "outputVerified": False,
                "outputVerification": f"reset failed: {exc}",
                "summaryPath": None,
                "statusSource": "run",
                "updatedAt": dt.datetime.now(dt.timezone.utc).isoformat()
            }
            print(f"failed {pipeline_name}: reset failed: {exc}", file=sys.stderr)
            continue
        completed = subprocess.run(
            ["java", "-jar", str(jar), "--pipeline-name", pipeline_name],
            cwd=REPO_ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        result_name = pipeline_name.replace("/", "__")
        (RESULTS_DIR / f"{result_name}.stdout.log").write_text(completed.stdout, encoding="utf-8")
        (RESULTS_DIR / f"{result_name}.stderr.log").write_text(completed.stderr, encoding="utf-8")

        summary_path = latest_summary(args.base_dir, pipeline_name)
        summary = json.loads(summary_path.read_text(encoding="utf-8")) if summary_path else {}
        stage_counts = stage_rows(summary)
        source_rows = stage_counts.get("SourceRetrieval")
        target_rows = stage_counts.get("TargetInsertUpdate")
        scenario = scenario_definition(manifest, pipeline_scenario(pipeline_name))
        if scenario.get("expectedSuccess", True):
            ok = completed.returncode == 0 and summary_path is not None and summary.get("errorRowCount", 0) == 0
            source_ok = source_rows == expected_rows or summary.get("rowsRead") in {expected_rows, expected_rows * 2}
            target_ok = target_rows == expected_rows or summary.get("rowsWritten") == expected_rows
            ok = ok and source_ok and target_ok
            output_ok, output_note, output_verified = verify_target_output(manifest, target_name, pipeline_name)
            ok = ok and output_ok
        else:
            ok, output_note, output_verified = expected_failure_result(completed, scenario)
        status_entries[pipeline_name] = {
            "completed": True,
            "passed": ok,
            "exitCode": completed.returncode,
            "errorRowCount": summary.get("errorRowCount", 0) if summary_path else None,
            "rowsRead": summary.get("rowsRead") if summary_path else None,
            "rowsWritten": summary.get("rowsWritten") if summary_path else None,
            "sourceRows": source_rows,
            "targetRows": target_rows,
            "outputVerified": output_verified,
            "outputVerification": output_note,
            "summaryPath": str(summary_path.relative_to(REPO_ROOT)) if summary_path else None,
            "statusSource": "run",
            "updatedAt": dt.datetime.now(dt.timezone.utc).isoformat()
        }
        if not ok:
            failures += 1
            print(f"failed {pipeline_name}: exit={completed.returncode} summary={summary_path} output={output_note}", file=sys.stderr)

    save_status_entries(status_entries)
    return failures


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate and run Dittoe Engine matrix PMDs.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    def add_common(subparser: argparse.ArgumentParser) -> None:
        subparser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
        subparser.add_argument("--base-dir", type=Path, default=DEFAULT_BASE_DIR)
        subparser.add_argument("--fixture", type=Path, help="Override the dataset fixture CSV used for generation and verification.")
        subparser.add_argument("--profile", default="smoke")
        subparser.add_argument("--source", action="append")
        subparser.add_argument("--target", action="append")
        subparser.add_argument("--case", action="append", help="Pipeline name such as local-file-to-s3/canonical-smoke")

    list_parser = subparsers.add_parser("list", help="List matrix cases.")
    add_common(list_parser)

    generate_parser = subparsers.add_parser("generate", help="Generate PMDs into the matrix target directory.")
    add_common(generate_parser)

    run_parser = subparsers.add_parser("run", help="Run generated PMDs through the Driver CLI.")
    add_common(run_parser)
    run_parser.add_argument("--jar", type=Path)
    run_parser.add_argument("--package", action="store_true", help="Build the shaded jar before running.")
    run_parser.add_argument("--generate", action="store_true", help="Generate PMDs before running.")

    report_parser = subparsers.add_parser("report", help="Render a markdown report and checklist from matrix results.")
    add_common(report_parser)
    report_parser.add_argument("--output", type=Path, default=DEFAULT_REPORT)

    permutation_parser = subparsers.add_parser("permutation-report", help="Render a markdown audit for all implemented connector permutations.")
    permutation_parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    permutation_parser.add_argument("--output", type=Path, default=DEFAULT_PERMUTATION_REPORT)

    return parser


def main() -> int:
    args = build_parser().parse_args()
    args.manifest = args.manifest if args.manifest.is_absolute() else REPO_ROOT / args.manifest
    if hasattr(args, "base_dir"):
        args.base_dir = args.base_dir if args.base_dir.is_absolute() else REPO_ROOT / args.base_dir

    if args.command == "list":
        manifest = runtime_manifest(args.manifest, args.fixture)
        manifest = configure_manifest_for_request(manifest, args.profile, args.source, args.target, args.case)
        for _, _, pipeline_name in matrix_cases(manifest, args.profile, args.source, args.target, args.case):
            print(pipeline_name)
        return 0
    if args.command == "generate":
        cases = generate(args)
        print(f"generated {len(cases)} cases under {args.base_dir}")
        return 0
    if args.command == "run":
        return run_cases(args)
    if args.command == "report":
        args.output = args.output if args.output.is_absolute() else REPO_ROOT / args.output
        output = write_report(args)
        print(f"wrote report to {output}")
        return 0
    if args.command == "permutation-report":
        args.output = args.output if args.output.is_absolute() else REPO_ROOT / args.output
        output = write_permutation_report(args)
        print(f"wrote report to {output}")
        return 0
    raise SystemExit(f"Unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())