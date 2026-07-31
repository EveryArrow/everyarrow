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
 * DITTOE C++ SDK Facade Integration Test
 * Uses ConnectionBuilder + migrate() facade.
 */
#include "../include/dittoe.hpp"
#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <string>

using namespace dittoe;

static int tests_passed = 0;
static int tests_total = 0;

// TEST-ONLY: Default paths for local development environment for dev .
// In CI/Production, these should be set via DITTOE_JAR and DITTOE_BASE_DIR env vars.
static const std::string DEFAULT_JAR_PATH = "EveryArrowWorkspace/Dittoe/dittoe-engine/target/execution-1.0-shaded.jar";
static const std::string DEFAULT_BASE_DIR = "EveryArrowWorkspace/Dittoe/dittoe-engine";

static std::string get_env_or_default(const char* var, const std::string& def) {
    const char* val = std::getenv(var);
    return val ? std::string(val) : def;
}

static int pg_count(int port, const std::string& table) {
    std::string cmd = "PGPASSWORD=dittoe123 psql -h localhost -p " + std::to_string(port) +
        " -U dittoe -d adventureworks -t -A -c \"SELECT count(*) FROM production." + table + ";\" 2>/dev/null";
    FILE* fp = popen(cmd.c_str(), "r");
    if (!fp) return -1;
    char buf[64] = {};
    if (fgets(buf, sizeof(buf), fp)) {}
    pclose(fp);
    return atoi(buf);
}

static void pg_truncate(int port, const std::string& table) {
    std::string cmd = "PGPASSWORD=dittoe123 psql -h localhost -p " + std::to_string(port) +
        " -U dittoe -d adventureworks -c \"TRUNCATE production." + table + ";\" 2>/dev/null";
    system(cmd.c_str());
}

static void test_department_facade() {
    tests_total++;
    std::cout << "\n[" << tests_total << "] Facade: Department Migration (PG -> PG, 16 rows)\n";

    pg_truncate(5434, "department");
    int src = pg_count(5433, "department");
    int tgt_before = pg_count(5434, "department");
    std::cout << "  Source: " << src << ", Target (before): " << tgt_before << "\n";

    DittoeConfig cfg;
    cfg.jarPath = get_env_or_default("DITTOE_JAR", DEFAULT_JAR_PATH);
    cfg.baseDir = get_env_or_default("DITTOE_BASE_DIR", DEFAULT_BASE_DIR);
    cfg.timeout = 120;

    auto source = ConnectionBuilder("department")
        .postgresql("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
        .schema("production")
        .fields({"departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime"})
        .asSource()
        .build();

    auto target = ConnectionBuilder("department")
        .postgresql("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
        .schema("production")
        .fields({"departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime"})
        .asTarget()
        .build();

    auto result = dittoe::migrate(cfg, source, target, "cpp-facade-department");

    int tgt_after = pg_count(5434, "department");
    std::cout << "  Engine return code: " << result.returnCode << "\n";
    std::cout << "  Target (after): " << tgt_after << "\n";

    if (result.returnCode == 0 && tgt_after == src) {
        std::cout << "  \033[32mPASS: All " << src << " departments migrated via facade!\033[0m\n";
        tests_passed++;
    } else {
        std::cout << "  \033[31mFAIL: rc=" << result.returnCode << ", expected " << src << " got " << tgt_after << "\033[0m\n";
    }
}

static void test_person_facade() {
    tests_total++;
    std::cout << "\n[" << tests_total << "] Facade: Person Migration (PG -> PG, 20 rows)\n";

    pg_truncate(5434, "person");
    int src = pg_count(5433, "person");
    int tgt_before = pg_count(5434, "person");
    std::cout << "  Source: " << src << ", Target (before): " << tgt_before << "\n";

    DittoeConfig cfg;
    cfg.jarPath = get_env_or_default("DITTOE_JAR", DEFAULT_JAR_PATH);
    cfg.baseDir = get_env_or_default("DITTOE_BASE_DIR", DEFAULT_BASE_DIR);
    cfg.timeout = 120;

    auto source = ConnectionBuilder("person")
        .postgresql("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
        .schema("production")
        .fields({"businessentityid:integer", "persontype:string", "namestyle:boolean",
                 "title:string", "firstname:string", "middlename:string",
                 "lastname:string", "suffix:string", "emailpromotion:integer",
                 "modifieddate:datetime"})
        .asSource()
        .build();

    auto target = ConnectionBuilder("person")
        .postgresql("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
        .schema("production")
        .fields({"businessentityid:integer", "persontype:string", "namestyle:boolean",
                 "title:string", "firstname:string", "middlename:string",
                 "lastname:string", "suffix:string", "emailpromotion:integer",
                 "modifieddate:datetime"})
        .asTarget()
        .build();

    auto result = dittoe::migrate(cfg, source, target, "cpp-facade-person");

    int tgt_after = pg_count(5434, "person");
    std::cout << "  Engine return code: " << result.returnCode << "\n";
    std::cout << "  Target (after): " << tgt_after << "\n";

    if (result.returnCode == 0 && tgt_after == src) {
        std::cout << "  \033[32mPASS: All " << src << " persons migrated via facade!\033[0m\n";
        tests_passed++;
    } else {
        std::cout << "  \033[31mFAIL: rc=" << result.returnCode << ", expected " << src << " got " << tgt_after << "\033[0m\n";
    }
}

int main() {
    std::cout << "\n=== DITTOE C++ SDK Facade Integration Tests ===\n";
    test_department_facade();
    test_person_facade();
    std::cout << "\n=== Results: " << tests_passed << "/" << tests_total << " facade integration tests passed ===\n\n";
    return tests_passed == tests_total ? 0 : 1;
}
