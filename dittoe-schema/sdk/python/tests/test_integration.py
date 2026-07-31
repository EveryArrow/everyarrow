# DITTOE SCHEMA:
#
# Copyright (c) 2026 EveryArrow, Inc.
#
# Licensed under the Apache License, Version 2.0.
# You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0

#!/usr/bin/env python3
"""
DITTOE SDK Facade Integration Tests
Runs real migrations through the facade API against Docker PostgreSQL containers.

Requires:
    - Docker containers running (dittoe-postgres-source :5433, dittoe-postgres-target :5434)
    - Java 21+ on PATH
    - DITTOE_JAR env var (or default path to execution-1.0-shaded.jar)
"""
import os
import sys
import subprocess

# Add SDK to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from dittoe_sdk.config import DittoeConfig
from dittoe_sdk.facade import ConnectionBuilder, Dittoe

# Default JAR path (can be overridden by DITTOE_JAR env var)
DEFAULT_JAR = os.path.abspath(os.path.join(os.path.dirname(__file__),
    '..', '..', '..', '..', 'dittoe-engine', 'target', 'execution-1.0-shaded.jar'))

# DB connection settings
PG_SRC = {"host": "localhost", "port": 5433, "db": "adventureworks", "user": "dittoe", "pass": "dittoe123"}
PG_TGT = {"host": "localhost", "port": 5434, "db": "adventureworks", "user": "dittoe", "pass": "dittoe123"}


def pg_query(port, query):
    """Run a PostgreSQL query and return output."""
    env = os.environ.copy()
    env['PGPASSWORD'] = 'dittoe123'
    result = subprocess.run(
        ['psql', '-h', 'localhost', '-p', str(port), '-U', 'dittoe', '-d', 'adventureworks',
         '-t', '-A', '-c', query],
        capture_output=True, text=True, env=env
    )
    return result.stdout.strip()


def pg_truncate(port, table):
    """Truncate a table on the target database."""
    pg_query(port, f"TRUNCATE production.{table};")


def get_config():
    """Build DittoeConfig, preferring env vars, falling back to defaults."""
    cfg = DittoeConfig.from_env()
    if not cfg.jar_path:
        cfg.jar_path = DEFAULT_JAR
    if not cfg.base_dir:
        cfg.base_dir = os.path.abspath(os.path.join(
            os.path.dirname(__file__), '..', '..', '..', '..', 'dittoe-engine'))
    return cfg


# ============================================================================
# Facade-based tests
# ============================================================================

def test_department_facade():
    """Test 1: Department migration (16 rows) via Dittoe.migrate() facade."""
    print("\n" + "=" * 60)
    print("TEST 1: Facade — Department Migration (PG → PG, 16 rows)")
    print("=" * 60)

    pg_truncate(5434, "department")
    src_count = int(pg_query(5433, "SELECT count(*) FROM production.department;"))
    tgt_before = int(pg_query(5434, "SELECT count(*) FROM production.department;"))
    print(f"  Source: {src_count}, Target (before): {tgt_before}")
    assert src_count == 16, f"Expected 16 source departments, got {src_count}"
    assert tgt_before == 0, f"Expected 0 target departments, got {tgt_before}"

    # ---- FACADE API ----
    config = get_config()
    dittoe = Dittoe(config)

    source = ConnectionBuilder("department") \
        .postgresql(PG_SRC["host"], PG_SRC["port"], PG_SRC["db"], PG_SRC["user"], PG_SRC["pass"]) \
        .schema("production") \
        .fields(["departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime"]) \
        .as_source() \
        .build()

    target = ConnectionBuilder("department") \
        .postgresql(PG_TGT["host"], PG_TGT["port"], PG_TGT["db"], PG_TGT["user"], PG_TGT["pass"]) \
        .schema("production") \
        .fields(["departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime"]) \
        .as_target() \
        .build()

    result = dittoe.migrate(source, target, pipeline_name="facade-department")

    tgt_after = int(pg_query(5434, "SELECT count(*) FROM production.department;"))
    print(f"  Engine exit code: {result.return_code}")
    print(f"  Target (after): {tgt_after}")

    if result.return_code == 0 and tgt_after == src_count:
        print("  ✅ PASS: All 16 departments migrated via facade!")
        return True
    else:
        print(f"  ❌ FAIL: rc={result.return_code}, expected {src_count} got {tgt_after}")
        if result.stderr:
            print(f"  STDERR (last 500): {result.stderr[-500:]}")
        return False


def test_person_facade():
    """Test 2: Person migration (20 rows) via Dittoe.migrate() facade."""
    print("\n" + "=" * 60)
    print("TEST 2: Facade — Person Migration (PG → PG, 20 rows)")
    print("=" * 60)

    pg_truncate(5434, "person")
    src_count = int(pg_query(5433, "SELECT count(*) FROM production.person;"))
    tgt_before = int(pg_query(5434, "SELECT count(*) FROM production.person;"))
    print(f"  Source: {src_count}, Target (before): {tgt_before}")

    config = get_config()
    dittoe = Dittoe(config)

    source = ConnectionBuilder("person") \
        .postgresql(PG_SRC["host"], PG_SRC["port"], PG_SRC["db"], PG_SRC["user"], PG_SRC["pass"]) \
        .schema("production") \
        .fields([
            "businessentityid:integer", "persontype:string", "namestyle:boolean",
            "title:string", "firstname:string", "middlename:string",
            "lastname:string", "suffix:string", "emailpromotion:integer",
            "modifieddate:datetime"
        ]) \
        .as_source() \
        .build()

    target = ConnectionBuilder("person") \
        .postgresql(PG_TGT["host"], PG_TGT["port"], PG_TGT["db"], PG_TGT["user"], PG_TGT["pass"]) \
        .schema("production") \
        .fields([
            "businessentityid:integer", "persontype:string", "namestyle:boolean",
            "title:string", "firstname:string", "middlename:string",
            "lastname:string", "suffix:string", "emailpromotion:integer",
            "modifieddate:datetime"
        ]) \
        .as_target() \
        .build()

    result = dittoe.migrate(source, target, pipeline_name="facade-person")

    tgt_after = int(pg_query(5434, "SELECT count(*) FROM production.person;"))
    print(f"  Engine exit code: {result.return_code}")
    print(f"  Target (after): {tgt_after}")

    if result.return_code == 0 and tgt_after == src_count:
        print(f"  ✅ PASS: All {src_count} persons migrated via facade!")
        return True
    else:
        print(f"  ❌ FAIL: rc={result.return_code}, expected {src_count} got {tgt_after}")
        if result.stderr:
            print(f"  STDERR (last 500): {result.stderr[-500:]}")
        return False


def test_product_facade():
    """Test 3: Product migration (100 rows) via Dittoe.migrate() facade."""
    print("\n" + "=" * 60)
    print("TEST 3: Facade — Product Migration (PG → PG, 100 rows)")
    print("=" * 60)

    pg_truncate(5434, "product")
    src_count = int(pg_query(5433, "SELECT count(*) FROM production.product;"))
    tgt_before = int(pg_query(5434, "SELECT count(*) FROM production.product;"))
    print(f"  Source: {src_count}, Target (before): {tgt_before}")

    config = get_config()
    dittoe = Dittoe(config)

    source = ConnectionBuilder("product") \
        .postgresql(PG_SRC["host"], PG_SRC["port"], PG_SRC["db"], PG_SRC["user"], PG_SRC["pass"]) \
        .schema("production") \
        .fields([
            "productid:integer", "name:string", "productnumber:string",
            "color:string", "standardcost:decimal", "listprice:decimal",
            "size:string", "weight:decimal", "productcategoryid:integer",
            "productmodelid:integer", "sellstartdate:datetime",
            "sellenddate:datetime", "discontinueddate:datetime",
            "modifieddate:datetime"
        ]) \
        .as_source() \
        .build()

    target = ConnectionBuilder("product") \
        .postgresql(PG_TGT["host"], PG_TGT["port"], PG_TGT["db"], PG_TGT["user"], PG_TGT["pass"]) \
        .schema("production") \
        .fields([
            "productid:integer", "name:string", "productnumber:string",
            "color:string", "standardcost:decimal", "listprice:decimal",
            "size:string", "weight:decimal", "productcategoryid:integer",
            "productmodelid:integer", "sellstartdate:datetime",
            "sellenddate:datetime", "discontinueddate:datetime",
            "modifieddate:datetime"
        ]) \
        .as_target() \
        .build()

    result = dittoe.migrate(source, target, pipeline_name="facade-product")

    tgt_after = int(pg_query(5434, "SELECT count(*) FROM production.product;"))
    print(f"  Engine exit code: {result.return_code}")
    print(f"  Target (after): {tgt_after}")

    if result.return_code == 0 and tgt_after == src_count:
        print(f"  ✅ PASS: All {src_count} products migrated via facade!")
        return True
    else:
        print(f"  ❌ FAIL: rc={result.return_code}, expected {src_count} got {tgt_after}")
        if result.stderr:
            print(f"  STDERR (last 500): {result.stderr[-500:]}")
        return False


if __name__ == "__main__":
    print("\n╔══════════════════════════════════════════════════╗")
    print("║   DITTOE Python SDK — Facade Integration Tests  ║")
    print("║   Testing Dittoe.migrate() against Docker PG    ║")
    print("╚══════════════════════════════════════════════════╝")

    jar = os.environ.get("DITTOE_JAR", DEFAULT_JAR)
    print(f"\nJAR: {jar}")
    print(f"JAR exists: {os.path.exists(jar)}")

    results = []
    results.append(("Department Migration (16 rows)", test_department_facade()))
    results.append(("Person Migration (20 rows)", test_person_facade()))
    results.append(("Product Migration (100 rows)", test_product_facade()))

    print("\n" + "=" * 60)
    print("FACADE INTEGRATION TEST RESULTS")
    print("=" * 60)
    for name, passed in results:
        status = "✅ PASS" if passed else "❌ FAIL"
        print(f"  {status}: {name}")

    passed = sum(1 for _, p in results if p)
    total = len(results)
    print(f"\n  {passed}/{total} facade integration tests passed")
    sys.exit(0 if passed == total else 1)
