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

using System;
using System.Diagnostics;
using System.IO;
using DittoeSDK;

class Program
{
    static int testsPassed = 0;
    static int testsTotal = 0;

    // JAR path — env var or default
    static readonly string JAR = Environment.GetEnvironmentVariable("DITTOE_JAR")
        ?? Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
            "..", "..", "..", "..", "..", "..", "..", "dittoe-engine", "target", "execution-1.0-shaded.jar"));

    static readonly string BASE_DIR = Environment.GetEnvironmentVariable("DITTOE_BASE_DIR")
        ?? Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
            "..", "..", "..", "..", "..", "..", "..", "dittoe-engine"));

    static int PgCount(int port, string table)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "psql",
            Arguments = $"-h localhost -p {port} -U dittoe -d adventureworks -t -A -c \"SELECT count(*) FROM production.{table};\"",
            RedirectStandardOutput = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.EnvironmentVariables["PGPASSWORD"] = "dittoe123";
        using var p = Process.Start(psi);
        var outStr = p.StandardOutput.ReadToEnd().Trim();
        p.WaitForExit();
        return int.TryParse(outStr, out int res) ? res : -1;
    }

    static void PgTruncate(int port, string table)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "psql",
            Arguments = $"-h localhost -p {port} -U dittoe -d adventureworks -c \"TRUNCATE production.{table};\"",
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.EnvironmentVariables["PGPASSWORD"] = "dittoe123";
        using var p = Process.Start(psi);
        p.WaitForExit();
    }

    // ========================================================================
    // Facade-based integration tests
    // ========================================================================

    static void TestDepartmentFacade()
    {
        testsTotal++;
        Console.WriteLine($"\n[{testsTotal}] Facade: Department Migration (PG → PG, 16 rows)");

        PgTruncate(5434, "department");
        int src = PgCount(5433, "department");
        int tgtBefore = PgCount(5434, "department");
        Console.WriteLine($"  Source: {src}, Target (before): {tgtBefore}");

        // ---- FACADE API ----
        var config = new DittoeConfig { JarPath = JAR, BaseDir = BASE_DIR, Timeout = 120 };

        var source = new ConnectionBuilder("department")
            .PostgreSQL("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
            .Schema("production")
            .Fields("departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime")
            .AsSource()
            .Build();

        var target = new ConnectionBuilder("department")
            .PostgreSQL("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
            .Schema("production")
            .Fields("departmentid:integer", "name:string", "groupname:string", "modifieddate:datetime")
            .AsTarget()
            .Build();

        var dittoe = new Dittoe(config);
        var (success, output) = dittoe.Migrate(source, target, "csharp-facade-department");

        int tgtAfter = PgCount(5434, "department");
        Console.WriteLine($"  Engine success: {success}");
        Console.WriteLine($"  Target (after): {tgtAfter}");

        if (success && tgtAfter == src)
        {
            Console.WriteLine($"  ✅ PASS: All {src} departments migrated via facade!");
            testsPassed++;
        }
        else
        {
            Console.WriteLine($"  ❌ FAIL: success={success}, expected {src} got {tgtAfter}");
        }
    }

    static void TestPersonFacade()
    {
        testsTotal++;
        Console.WriteLine($"\n[{testsTotal}] Facade: Person Migration (PG → PG, 20 rows)");

        PgTruncate(5434, "person");
        int src = PgCount(5433, "person");
        int tgtBefore = PgCount(5434, "person");
        Console.WriteLine($"  Source: {src}, Target (before): {tgtBefore}");

        var config = new DittoeConfig { JarPath = JAR, BaseDir = BASE_DIR, Timeout = 120 };

        var source = new ConnectionBuilder("person")
            .PostgreSQL("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
            .Schema("production")
            .Fields("businessentityid:integer", "persontype:string", "namestyle:boolean",
                    "title:string", "firstname:string", "middlename:string",
                    "lastname:string", "suffix:string", "emailpromotion:integer",
                    "modifieddate:datetime")
            .AsSource()
            .Build();

        var target = new ConnectionBuilder("person")
            .PostgreSQL("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
            .Schema("production")
            .Fields("businessentityid:integer", "persontype:string", "namestyle:boolean",
                    "title:string", "firstname:string", "middlename:string",
                    "lastname:string", "suffix:string", "emailpromotion:integer",
                    "modifieddate:datetime")
            .AsTarget()
            .Build();

        var dittoe = new Dittoe(config);
        var (success, output) = dittoe.Migrate(source, target, "csharp-facade-person");

        int tgtAfter = PgCount(5434, "person");
        Console.WriteLine($"  Engine success: {success}");
        Console.WriteLine($"  Target (after): {tgtAfter}");

        if (success && tgtAfter == src)
        {
            Console.WriteLine($"  ✅ PASS: All {src} persons migrated via facade!");
            testsPassed++;
        }
        else
        {
            Console.WriteLine($"  ❌ FAIL: success={success}, expected {src} got {tgtAfter}");
        }
    }

    static int Main()
    {
        Console.WriteLine("\n=== DITTOE C# SDK — Facade Integration Tests ===");
        Console.WriteLine($"JAR: {JAR}");
        Console.WriteLine($"BASE_DIR: {BASE_DIR}");
        Console.WriteLine($"JAR exists: {File.Exists(JAR)}\n");

        TestDepartmentFacade();
        TestPersonFacade();

        Console.WriteLine($"\n=== Results: {testsPassed}/{testsTotal} facade integration tests passed ===\n");
        return testsPassed == testsTotal ? 0 : 1;
    }
}
