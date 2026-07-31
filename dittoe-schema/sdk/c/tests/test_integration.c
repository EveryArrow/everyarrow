/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * DITTOE C SDK Facade Integration Test
 * Tests real DB migrations using the facade API (DittoeConfig + DittoeConnection + dittoe_migrate).
 */
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../include/dittoe.h"

static int tests_passed = 0;
static int tests_total = 0;

// TEST-ONLY: Default paths for local development environment.
// In CI/Production, these should be set via DITTOE_JAR and DITTOE_BASE_DIR env vars.
static const char* DEFAULT_JAR_PATH = "../../../dittoe-engine/target/execution-1.0-shaded.jar";
static const char* DEFAULT_BASE_DIR = "../../../dittoe-engine";

static const char* get_env_or_default(const char* var, const char* def) {
    const char* val = getenv(var);
    return val ? val : def;
}

static int pg_count(int port, const char* table) {
    char cmd[512];
    snprintf(cmd, sizeof(cmd),
        "PGPASSWORD=dittoe123 psql -h localhost -p %d -U dittoe -d adventureworks -t -A -c "
        "\"SELECT count(*) FROM production.%s;\" 2>/dev/null", port, table);
    FILE* fp = popen(cmd, "r");
    if (!fp) return -1;
    char buf[64] = {};
    if (fgets(buf, sizeof(buf), fp)) {}
    pclose(fp);
    return atoi(buf);
}

static void pg_truncate(int port, const char* table) {
    char cmd[512];
    snprintf(cmd, sizeof(cmd),
        "PGPASSWORD=dittoe123 psql -h localhost -p %d -U dittoe -d adventureworks -c "
        "\"TRUNCATE production.%s;\" 2>/dev/null", port, table);
    system(cmd);
}

static void test_department_facade(void) {
    tests_total++;
    printf("\n[%d] Facade: Department Migration (PG -> PG, 16 rows)\n", tests_total);

    pg_truncate(5434, "department");
    int src = pg_count(5433, "department");
    int tgt_before = pg_count(5434, "department");
    printf("  Source: %d, Target (before): %d\n", src, tgt_before);

    /* ---- FACADE API ---- */
    DittoeConfig cfg;
    dittoe_config_init(&cfg);
    strncpy(cfg.jar_path, get_env_or_default("DITTOE_JAR", DEFAULT_JAR_PATH), DITTOE_MAX_STR - 1);
    strncpy(cfg.base_dir, get_env_or_default("DITTOE_BASE_DIR", DEFAULT_BASE_DIR), DITTOE_MAX_STR - 1);
    cfg.timeout = 120;

    DittoeConnection source;
    dittoe_connection_init_pg(&source, "department",
        "localhost", 5433, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&source, "production");
    dittoe_connection_add_field(&source, "departmentid", DT_INTEGER, 0);
    dittoe_connection_add_field(&source, "name", DT_STRING, 1);
    dittoe_connection_add_field(&source, "groupname", DT_STRING, 1);
    dittoe_connection_add_field(&source, "modifieddate", DT_DATETIME, 1);

    DittoeConnection target;
    dittoe_connection_init_pg(&target, "department",
        "localhost", 5434, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&target, "production");
    dittoe_connection_add_field(&target, "departmentid", DT_INTEGER, 0);
    dittoe_connection_add_field(&target, "name", DT_STRING, 1);
    dittoe_connection_add_field(&target, "groupname", DT_STRING, 1);
    dittoe_connection_add_field(&target, "modifieddate", DT_DATETIME, 1);

    DittoeExecResult result = dittoe_migrate(&cfg, &source, &target, "c-facade-department");

    int tgt_after = pg_count(5434, "department");
    printf("  Engine exit code: %d\n", result.return_code);
    printf("  Target (after): %d\n", tgt_after);

    if (result.return_code == 0 && tgt_after == src) {
        printf("  PASS: All %d departments migrated via facade!\n", src);
        tests_passed++;
    } else {
        printf("  FAIL: rc=%d, expected %d got %d\n", result.return_code, src, tgt_after);
    }
}

static void test_person_facade(void) {
    tests_total++;
    printf("\n[%d] Facade: Person Migration (PG -> PG, 20 rows)\n", tests_total);

    pg_truncate(5434, "person");
    int src = pg_count(5433, "person");
    int tgt_before = pg_count(5434, "person");
    printf("  Source: %d, Target (before): %d\n", src, tgt_before);

    DittoeConfig cfg;
    dittoe_config_init(&cfg);
    strncpy(cfg.jar_path, get_env_or_default("DITTOE_JAR", DEFAULT_JAR_PATH), DITTOE_MAX_STR - 1);
    strncpy(cfg.base_dir, get_env_or_default("DITTOE_BASE_DIR", DEFAULT_BASE_DIR), DITTOE_MAX_STR - 1);
    cfg.timeout = 120;

    DittoeConnection source;
    dittoe_connection_init_pg(&source, "person",
        "localhost", 5433, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&source, "production");
    dittoe_connection_add_field(&source, "businessentityid", DT_INTEGER, 0);
    dittoe_connection_add_field(&source, "persontype", DT_STRING, 1);
    dittoe_connection_add_field(&source, "namestyle", DT_BOOLEAN, 1);
    dittoe_connection_add_field(&source, "title", DT_STRING, 1);
    dittoe_connection_add_field(&source, "firstname", DT_STRING, 1);
    dittoe_connection_add_field(&source, "middlename", DT_STRING, 1);
    dittoe_connection_add_field(&source, "lastname", DT_STRING, 1);
    dittoe_connection_add_field(&source, "suffix", DT_STRING, 1);
    dittoe_connection_add_field(&source, "emailpromotion", DT_INTEGER, 1);
    dittoe_connection_add_field(&source, "modifieddate", DT_DATETIME, 1);

    DittoeConnection target;
    dittoe_connection_init_pg(&target, "person",
        "localhost", 5434, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&target, "production");
    dittoe_connection_add_field(&target, "businessentityid", DT_INTEGER, 0);
    dittoe_connection_add_field(&target, "persontype", DT_STRING, 1);
    dittoe_connection_add_field(&target, "namestyle", DT_BOOLEAN, 1);
    dittoe_connection_add_field(&target, "title", DT_STRING, 1);
    dittoe_connection_add_field(&target, "firstname", DT_STRING, 1);
    dittoe_connection_add_field(&target, "middlename", DT_STRING, 1);
    dittoe_connection_add_field(&target, "lastname", DT_STRING, 1);
    dittoe_connection_add_field(&target, "suffix", DT_STRING, 1);
    dittoe_connection_add_field(&target, "emailpromotion", DT_INTEGER, 1);
    dittoe_connection_add_field(&target, "modifieddate", DT_DATETIME, 1);

    DittoeExecResult result = dittoe_migrate(&cfg, &source, &target, "c-facade-person");

    int tgt_after = pg_count(5434, "person");
    printf("  Engine exit code: %d\n", result.return_code);
    printf("  Target (after): %d\n", tgt_after);

    if (result.return_code == 0 && tgt_after == src) {
        printf("  PASS: All %d persons migrated via facade!\n", src);
        tests_passed++;
    } else {
        printf("  FAIL: rc=%d, expected %d got %d\n", result.return_code, src, tgt_after);
    }
}

int main(void) {
    printf("\n=== DITTOE C SDK — Facade Integration Tests ===\n");
    test_department_facade();
    test_person_facade();
    printf("\n=== Results: %d/%d facade integration tests passed ===\n\n", tests_passed, tests_total);
    return tests_passed == tests_total ? 0 : 1;
}
