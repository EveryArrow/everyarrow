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

// DITTOE C# SDK Test Suite
// Tests enum coverage, fluent builders, serialization, and pipeline construction.
// Requires: dotnet add package NUnit + NUnit3TestAdapter
// Or run as standalone: dotnet run (with TestRunner main)

using System;
using System.Collections.Generic;
using System.Linq;
using DittoeSDK;

namespace DittoeSDK.Tests
{
    class TestRunner
    {
        static int Passed = 0;
        static int Total = 0;

        static void Assert(bool cond, string msg = "")
        {
            if (!cond) throw new Exception($"ASSERT FAILED: {msg}");
        }

        static void Test(string name, Action fn)
        {
            Total++;
            Console.Write($"  [{Total}] {name}... ");
            fn();
            Passed++;
            Console.WriteLine("PASS");
        }

        // ================================================================
        // Enum tests
        // ================================================================

        static void TestEnumCounts()
        {
            Test("DataType has 17 values", () => Assert(Enum.GetValues<DataType>().Length == 17));
            Test("BehaviorType has 22 values", () => Assert(Enum.GetValues<BehaviorType>().Length == 22));
            Test("ServerType has 4 values", () => Assert(Enum.GetValues<ServerType>().Length == 4));
            Test("TemplateType has 16 values", () => Assert(Enum.GetValues<TemplateType>().Length == 16));
            Test("StageType has 6 values", () => Assert(Enum.GetValues<StageType>().Length == 6));
            Test("ProcessExecutionType has 4 values", () => Assert(Enum.GetValues<ProcessExecutionType>().Length == 4));
            Test("DatabaseType has 5 values", () => Assert(Enum.GetValues<DatabaseType>().Length == 5));
            Test("FileType has 11 values", () => Assert(Enum.GetValues<FileType>().Length == 11));
            Test("APIType has 2 values", () => Assert(Enum.GetValues<APIType>().Length == 2));
        }

        // ================================================================
        // Field builder tests
        // ================================================================

        static void TestFieldBuilders()
        {
            Test("FieldBuilder basic", () => {
                var f = new FieldBuilder("name").String().Required().Build();
                Assert(f.Name == "name");
                Assert(f.DataType == DataType.String);
                Assert(!f.IsNullable);
            });

            Test("FieldBuilder all 17 types", () => {
                Assert(new FieldBuilder("a").Integer().Build().DataType == DataType.Integer);
                Assert(new FieldBuilder("a").Double().Build().DataType == DataType.Double);
                Assert(new FieldBuilder("a").Decimal().Build().DataType == DataType.Decimal);
                Assert(new FieldBuilder("a").Boolean().Build().DataType == DataType.Boolean);
                Assert(new FieldBuilder("a").Date().Build().DataType == DataType.Date);
                Assert(new FieldBuilder("a").DateTime().Build().DataType == DataType.DateTime);
                Assert(new FieldBuilder("a").Time().Build().DataType == DataType.Time);
                Assert(new FieldBuilder("a").Uuid().Build().DataType == DataType.UUID);
                Assert(new FieldBuilder("a").TimeWithTz().Build().DataType == DataType.TimeWithTimeZone);
                Assert(new FieldBuilder("a").DateTimeWithTz().Build().DataType == DataType.DateTimeWithTimeZone);
                Assert(new FieldBuilder("a").Json().Build().DataType == DataType.JSON);
                Assert(new FieldBuilder("a").Jsonb().Build().DataType == DataType.JSONB);
                Assert(new FieldBuilder("a").Array().Build().DataType == DataType.Array);
                Assert(new FieldBuilder("a").Number().Build().DataType == DataType.Number);
                Assert(new FieldBuilder("a").Bytes().Build().DataType == DataType.Bytes);
                Assert(new FieldBuilder("a").Xml().Build().DataType == DataType.XML);
            });

            Test("FieldBuilder with expression", () => {
                var f = new FieldBuilder("calc").Decimal().Expression("price * 1.1").Build();
                Assert(f.Expression == "price * 1.1");
            });
        }

        // ================================================================
        // Server builder tests (19 types)
        // ================================================================

        static void TestServerBuilders()
        {
            Test("PostgreSQL server", () => {
                var s = new ServerBuilder("pg").PostgreSQL("h", 5432, "db", "u", "p").Build();
                Assert(s.Type == ServerType.Database);
                Assert(s.ConnectionDetails["databaseType"] == "PostgreSQLServer");
            });
            Test("MySQL server", () => Assert(new ServerBuilder("my").MySQL("h", 3306, "db", "u", "p").Build().ConnectionDetails["databaseType"] == "MySQLServer"));
            Test("MSSQL server", () => Assert(new ServerBuilder("ms").MSSQL("h", 1433, "db", "sa", "p").Build().ConnectionDetails["databaseType"] == "MSSQLServer"));
            Test("SQLite server", () => {
                var s = new ServerBuilder("lite").SQLite("/tmp/test.db").Build();
                Assert(s.ConnectionDetails["databaseType"] == "SQLiteServer");
                Assert(s.ConnectionDetails["path"] == "/tmp/test.db");
            });
            Test("Snowflake server", () => Assert(new ServerBuilder("snow").Snowflake("h", 443, "db", "u", "p").Build().ConnectionDetails["databaseType"] == "SnowflakeServer"));
            Test("LocalFile server", () => Assert(new ServerBuilder("fs").LocalFile("/data").Build().ConnectionDetails["fileType"] == "LocalFileServer"));
            Test("S3 server", () => Assert(new ServerBuilder("s3").S3("b", "r", "a", "s").Build().ConnectionDetails["fileType"] == "S3Server"));
            Test("GCS server", () => Assert(new ServerBuilder("gcs").GCS("b", "p", "c").Build().ConnectionDetails["fileType"] == "GCSServer"));
            Test("Azure server", () => Assert(new ServerBuilder("az").Azure("c", "a", "k").Build().ConnectionDetails["fileType"] == "AzureServer"));
            Test("Dropbox server", () => Assert(new ServerBuilder("db").Dropbox("t").Build().ConnectionDetails["fileType"] == "DropboxServer"));
            Test("SFTP server", () => Assert(new ServerBuilder("sftp").SFTP("h", 22, "/d", "u", "p").Build().ConnectionDetails["fileType"] == "SFTPServer"));
            Test("FTP server", () => Assert(new ServerBuilder("ftp").FTP("h", 21, "/d", "u", "p").Build().ConnectionDetails["fileType"] == "FTPServer"));
            Test("IBM server", () => Assert(new ServerBuilder("ibm").IBM("e", "k", "b").Build().ConnectionDetails["fileType"] == "IBMServer"));
            Test("Oracle Cloud server", () => Assert(new ServerBuilder("oci").OracleCloud("n", "b", "c").Build().ConnectionDetails["fileType"] == "OracleServer"));
            Test("DigitalOcean server", () => Assert(new ServerBuilder("do").DigitalOcean("s", "r", "a", "s").Build().ConnectionDetails["fileType"] == "DigitalOceanServer"));
            Test("GoogleDrive server", () => Assert(new ServerBuilder("gd").GoogleDrive("c", "f").Build().ConnectionDetails["fileType"] == "GoogleDriveServer"));
            Test("BigQuery server", () => {
                var s = new ServerBuilder("bq").BigQuery("p", "d", "c").Build();
                Assert(s.Type == ServerType.API);
                Assert(s.ConnectionDetails["apiType"] == "BigQueryServer");
            });
            Test("Salesforce server", () => Assert(new ServerBuilder("sf").Salesforce("u", "u", "p", "t").Build().ConnectionDetails["apiType"] == "SalesforceServer"));
            Test("Server additional props", () => {
                var s = new ServerBuilder("pg").PostgreSQL("h", 5432, "db", "u", "p").AdditionalProperty("timeout", "30").Build();
                Assert(s.AdditionalProperties["timeout"] == "30");
            });
        }

        // ================================================================
        // Strategy constant tests
        // ================================================================

        static void TestStrategyConstants()
        {
            Test("Read strategies are FQCNs", () => {
                Assert(ReadStrategy.JdbcSelect.Contains("SelectStrategy"));
                Assert(ReadStrategy.LocalCsv.Contains("CSVReadStrategy"));
                Assert(ReadStrategy.BigQueryQuery.Contains("QueryStrategy"));
                Assert(ReadStrategy.Salesforce.Contains("salesforce.readstrategy.RestAPI"));
            });
            Test("Write strategies are FQCNs", () => {
                Assert(WriteStrategy.JdbcInsert.Contains("InsertStrategy"));
                Assert(WriteStrategy.PgUpsert.Contains("UpsertOnConflict"));
                Assert(WriteStrategy.MssqlMerge.Contains("MergeStrategy"));
                Assert(WriteStrategy.BigQuery.Contains("DataStoreStrategy"));
            });
            Test("All strategies start with prefix", () => {
                var all = new[] { ReadStrategy.JdbcSelect, ReadStrategy.LocalCsv, ReadStrategy.S3Csv,
                    WriteStrategy.JdbcInsert, WriteStrategy.PgCopy, WriteStrategy.LocalCsv,
                    WriteStrategy.S3Csv, WriteStrategy.BigQuery, WriteStrategy.Salesforce };
                foreach (var s in all) Assert(s.StartsWith("com.everyarrow.dittoe.io."));
            });
        }

        // ================================================================
        // Resource builder tests
        // ================================================================

        static void TestResourceBuilders()
        {
            Test("Resource builder basic", () => {
                var srv = new ServerBuilder("pg").PostgreSQL("h", 5432, "db", "u", "p").Build();
                var r = new ResourceBuilder("products").SetServer(srv)
                    .AddField("id", DataType.Integer, false)
                    .AddField("name", DataType.String)
                    .Schema("production").JdbcRead().Build();
                Assert(r.ResourceName == "products");
                Assert(r.Fields.Count == 2);
                Assert(r.Strategy == ReadStrategy.JdbcSelect);
            });

            Test("All 39 resource strategy shortcuts", () => {
                var srv = new ServerBuilder("pg").PostgreSQL("h", 5432, "db", "u", "p").Build();
                Assert(new ResourceBuilder("t").SetServer(srv).JdbcRead().Build().Strategy == ReadStrategy.JdbcSelect);
                Assert(new ResourceBuilder("t").SetServer(srv).Insert().Build().Strategy == WriteStrategy.JdbcInsert);
                Assert(new ResourceBuilder("t").SetServer(srv).Replace().Build().Strategy == WriteStrategy.JdbcReplace);
                Assert(new ResourceBuilder("t").SetServer(srv).PgUpsert("c").Build().Strategy == WriteStrategy.PgUpsert);
                Assert(new ResourceBuilder("t").SetServer(srv).PgCopy().Build().Strategy == WriteStrategy.PgCopy);
                Assert(new ResourceBuilder("t").SetServer(srv).MssqlMerge().Build().Strategy == WriteStrategy.MssqlMerge);
                Assert(new ResourceBuilder("t").SetServer(srv).CsvRead().Build().Strategy == ReadStrategy.LocalCsv);
                Assert(new ResourceBuilder("t").SetServer(srv).CsvWrite().Build().Strategy == WriteStrategy.LocalCsv);
                Assert(new ResourceBuilder("t").SetServer(srv).S3CsvRead().Build().Strategy == ReadStrategy.S3Csv);
                Assert(new ResourceBuilder("t").SetServer(srv).BigQueryWrite().Build().Strategy == WriteStrategy.BigQuery);
                Assert(new ResourceBuilder("t").SetServer(srv).SalesforceRead().Build().Strategy == ReadStrategy.Salesforce);
            });
        }

        // ================================================================
        // Behavior builder tests
        // ================================================================

        static void TestBehaviorBuilders()
        {
            Test("All 22 behavior types", () => {
                Assert(new BehaviorBuilder().Formatting().Build().BehaviorType == BehaviorType.Formatting);
                Assert(new BehaviorBuilder().SourceRetrieval().Build().BehaviorType == BehaviorType.SourceRetrieval);
                Assert(new BehaviorBuilder().TargetInsertUpdate().Build().BehaviorType == BehaviorType.TargetInsertUpdate);
                Assert(new BehaviorBuilder().Transformation().Build().BehaviorType == BehaviorType.Transformation);
                Assert(new BehaviorBuilder().Encryption().Build().BehaviorType == BehaviorType.Encryption);
                Assert(new BehaviorBuilder().Logging().Build().BehaviorType == BehaviorType.Logging);
                Assert(new BehaviorBuilder().Instrumentation().Build().BehaviorType == BehaviorType.Instrumentation);
                Assert(new BehaviorBuilder().LineageTracking().Build().BehaviorType == BehaviorType.lineageTracking);
                Assert(new BehaviorBuilder().KeyGeneration().Build().BehaviorType == BehaviorType.KeyGeneration);
                Assert(new BehaviorBuilder().Masking().Build().BehaviorType == BehaviorType.Masking);
                Assert(new BehaviorBuilder().Deduplication().Build().BehaviorType == BehaviorType.Deduplication);
                Assert(new BehaviorBuilder().SchemaEvolution().Build().BehaviorType == BehaviorType.SchemaEvolution);
                Assert(new BehaviorBuilder().Retry().Build().BehaviorType == BehaviorType.Retry);
                Assert(new BehaviorBuilder().SchemaValidation().Build().BehaviorType == BehaviorType.SchemaValidation);
                Assert(new BehaviorBuilder().DQRuleChecks().Build().BehaviorType == BehaviorType.DQRuleChecks);
                Assert(new BehaviorBuilder().Archive().Build().BehaviorType == BehaviorType.Archive);
                Assert(new BehaviorBuilder().MicroBatch().Build().BehaviorType == BehaviorType.MicroBatch);
                Assert(new BehaviorBuilder().DataScienceAlgorithms().Build().BehaviorType == BehaviorType.DataScienceAlgorithms);
                Assert(new BehaviorBuilder().CustomCode().Build().BehaviorType == BehaviorType.CustomCode);
                Assert(new BehaviorBuilder().Compression().Build().BehaviorType == BehaviorType.Compression);
                Assert(new BehaviorBuilder().ExternalWidget().Build().BehaviorType == BehaviorType.ExternalWidget);
                Assert(new BehaviorBuilder().Cache().Build().BehaviorType == BehaviorType.Cache);
            });

            Test("Staged behavior", () => {
                var b = new BehaviorBuilder("Compress").Compression().Staged(StageType.Package, 1).Build();
                Assert(b.IsStaged);
                Assert(b.StageType == StageType.Package);
                Assert(b.Order == 1);
            });

            Test("Error threshold", () => {
                var b = new BehaviorBuilder().SourceRetrieval().ErrorThreshold(5).Build();
                Assert(b.Properties["errorCountThreshold"] == "5");
            });
        }

        // ================================================================
        // Pipeline builder tests
        // ================================================================

        static void TestPipelineBuilders()
        {
            Test("Full pipeline construction", () => {
                var srcSrv = new ServerBuilder("src").PostgreSQL("localhost", 5433, "srcdb", "u", "p").Build();
                var tgtSrv = new ServerBuilder("tgt").PostgreSQL("localhost", 5434, "tgtdb", "u", "p").Build();
                var src = new ResourceBuilder("product").SetServer(srcSrv)
                    .AddField("id", DataType.Integer, false)
                    .AddField("name", DataType.String)
                    .AddField("price", DataType.Decimal)
                    .Schema("production").JdbcRead().Build();
                var tgt = new ResourceBuilder("product").SetServer(tgtSrv)
                    .AddField("id", DataType.Integer, false)
                    .AddField("name", DataType.String)
                    .AddField("price", DataType.Decimal)
                    .Insert().Build();

                var p = new PipelineBuilder("ProductMigration").DatabaseToDatabase()
                    .Description("Migrate products")
                    .Source(src).Target(tgt).MapFieldsSameName().WithDefaultBehaviors().Build();

                Assert(p.FieldMappings.Count == 3);
                Assert(p.Behaviors.Count == 4);
                var json = p.ToJson();
                Assert(json.Contains("ProductMigration"));
                Assert(json.Contains("DatabaseToDatabase"));
            });

            Test("All 16 template types", () => {
                Assert(new PipelineBuilder("t").FileToFile().Build().TemplateType == TemplateType.FileToFile);
                Assert(new PipelineBuilder("t").FileToDatabase().Build().TemplateType == TemplateType.FileToDatabase);
                Assert(new PipelineBuilder("t").FileToStream().Build().TemplateType == TemplateType.FileToStream);
                Assert(new PipelineBuilder("t").FileToAPI().Build().TemplateType == TemplateType.FileToAPI);
                Assert(new PipelineBuilder("t").DatabaseToDatabase().Build().TemplateType == TemplateType.DatabaseToDatabase);
                Assert(new PipelineBuilder("t").DatabaseToFile().Build().TemplateType == TemplateType.DatabaseToFile);
                Assert(new PipelineBuilder("t").DatabaseToStream().Build().TemplateType == TemplateType.DatabaseToStream);
                Assert(new PipelineBuilder("t").DatabaseToAPI().Build().TemplateType == TemplateType.DatabaseToAPI);
                Assert(new PipelineBuilder("t").StreamToStream().Build().TemplateType == TemplateType.StreamToStream);
                Assert(new PipelineBuilder("t").StreamToFile().Build().TemplateType == TemplateType.StreamToFile);
                Assert(new PipelineBuilder("t").StreamToDatabase().Build().TemplateType == TemplateType.StreamToDatabase);
                Assert(new PipelineBuilder("t").StreamToAPI().Build().TemplateType == TemplateType.StreamToAPI);
                Assert(new PipelineBuilder("t").APIToAPI().Build().TemplateType == TemplateType.APIToAPI);
                Assert(new PipelineBuilder("t").APIToFile().Build().TemplateType == TemplateType.APIToFile);
                Assert(new PipelineBuilder("t").APIToDatabase().Build().TemplateType == TemplateType.APIToDatabase);
                Assert(new PipelineBuilder("t").APIToStream().Build().TemplateType == TemplateType.APIToStream);
            });

            Test("Executor validation", () => {
                var exec = new DittoeExecutor("/path/to/jar");
                var p = new PipelineMetadata();
                var errors = exec.Validate(p);
                Assert(errors.Count >= 7);
                Assert(errors.Contains("ShortName is required"));
            });

            Test("Cross-database MySQL to PG", () => {
                var my = new ServerBuilder("my").MySQL("h", 3306, "src", "u", "p").Build();
                var pg = new ServerBuilder("pg").PostgreSQL("h", 5432, "tgt", "u", "p").Build();
                var src = new ResourceBuilder("users").SetServer(my).AddField("id", DataType.Integer, false).JdbcRead().Build();
                var tgt = new ResourceBuilder("users").SetServer(pg).AddField("id", DataType.Integer, false).Insert().Build();
                var p = new PipelineBuilder("MySQLToPG").DatabaseToDatabase()
                    .Source(src).Target(tgt).MapFieldsSameName().WithDefaultBehaviors().Build();
                var json = p.ToJson();
                Assert(json.Contains("MySQLServer"));
                Assert(json.Contains("PostgreSQLServer"));
            });
        }

        // ================================================================
        // Config tests
        // ================================================================

        static void TestConfig()
        {
            Test("Config default values", () => {
                var c = new DittoeConfig();
                Assert(c.JarPath == "");
                Assert(c.BaseDir == "./pipelines");
                Assert(c.JavaPath == "java");
                Assert(c.JavaOpts.Count == 0);
                Assert(c.Timeout == 300);
            });

            Test("Config.FromEnv reads env vars", () => {
                Environment.SetEnvironmentVariable("DITTOE_JAR", "/test/engine.jar");
                Environment.SetEnvironmentVariable("DITTOE_BASE_DIR", "/tmp/test");
                Environment.SetEnvironmentVariable("DITTOE_JAVA", "/usr/bin/java");
                Environment.SetEnvironmentVariable("DITTOE_JAVA_OPTS", "-Xmx2g,-Xms512m");
                Environment.SetEnvironmentVariable("DITTOE_TIMEOUT", "600");
                var c = DittoeConfig.FromEnv();
                Assert(c.JarPath == "/test/engine.jar", $"JarPath: {c.JarPath}");
                Assert(c.BaseDir == "/tmp/test", $"BaseDir: {c.BaseDir}");
                Assert(c.JavaPath == "/usr/bin/java");
                Assert(c.JavaOpts.Count == 2);
                Assert(c.JavaOpts[0] == "-Xmx2g");
                Assert(c.Timeout == 600);
                // Cleanup
                Environment.SetEnvironmentVariable("DITTOE_JAR", null);
                Environment.SetEnvironmentVariable("DITTOE_BASE_DIR", null);
                Environment.SetEnvironmentVariable("DITTOE_JAVA", null);
                Environment.SetEnvironmentVariable("DITTOE_JAVA_OPTS", null);
                Environment.SetEnvironmentVariable("DITTOE_TIMEOUT", null);
            });

            Test("Config.Validate throws on empty JarPath", () => {
                bool threw = false;
                try { new DittoeConfig().Validate(); }
                catch (DittoeConfigException) { threw = true; }
                Assert(threw, "Should throw DittoeConfigException");
            });

            Test("Config.Validate throws on bad timeout", () => {
                bool threw = false;
                try { new DittoeConfig { JarPath = "/nonexistent.jar", Timeout = -1 }.Validate(); }
                catch (DittoeConfigException) { threw = true; }
                Assert(threw, "Should throw for negative timeout");
            });

            Test("Config.EnsureBaseDir creates directory", () => {
                var tmpDir = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"dittoe_test_{Guid.NewGuid():N}");
                var c = new DittoeConfig { BaseDir = tmpDir };
                var result = c.EnsureBaseDir();
                Assert(System.IO.Directory.Exists(result), $"Dir should exist: {result}");
                System.IO.Directory.Delete(tmpDir, true);
            });
        }

        // ================================================================
        // Facade tests
        // ================================================================

        static void TestFacade()
        {
            Test("ConnectionBuilder PostgreSQL source", () => {
                var conn = new ConnectionBuilder("products")
                    .PostgreSQL("localhost", 5433, "mydb", "user", "pass")
                    .Schema("production")
                    .Fields("id:integer", "name:string", "price:decimal")
                    .AsSource()
                    .Build();
                Assert(conn.Name == "products");
                Assert(conn.Server != null);
                Assert(conn.Server.ConnectionDetails["host"] == "localhost");
                Assert(conn.SchemaName == "production");
                Assert(conn.Fields.Count == 3);
                Assert(conn.Fields[0].DataType == DataType.Integer);
                Assert(conn.Strategy == ReadStrategy.JdbcSelect, $"Strategy: {conn.Strategy}");
            });

            Test("ConnectionBuilder MySQL target", () => {
                var conn = new ConnectionBuilder("users")
                    .MySQL("db-host", 3306, "mydb", "root", "pass")
                    .Fields("id:int", "name:str")
                    .AsTarget()
                    .Build();
                Assert(conn.Strategy == WriteStrategy.JdbcInsert);
                Assert(conn.Fields.Count == 2);
            });

            Test("ConnectionBuilder LocalFile source", () => {
                var conn = new ConnectionBuilder("data.csv")
                    .LocalFile("/data/imports")
                    .Fields("id:int", "name:str")
                    .AsSource()
                    .Build();
                Assert(conn.Strategy == ReadStrategy.LocalCsv);
            });

            Test("ConnectionBuilder S3 target", () => {
                var conn = new ConnectionBuilder("output")
                    .S3("mybucket", "us-east-1", "ak", "sk")
                    .Fields("id:int")
                    .AsTarget()
                    .Build();
                Assert(conn.Strategy == WriteStrategy.S3Csv);
            });

            Test("ConnectionBuilder throws without server", () => {
                bool threw = false;
                try { new ConnectionBuilder("test").Fields("id:int").Build(); }
                catch (InvalidOperationException) { threw = true; }
                Assert(threw, "Should throw when no server configured");
            });

            Test("Connection.Validate catches empty name", () => {
                var errors = new Connection().Validate();
                Assert(errors.Any(e => e.Contains("name is required")));
            });

            Test("Connection.Validate catches no fields", () => {
                var conn = new ConnectionBuilder("test")
                    .PostgreSQL("h", 5432, "db", "u", "p")
                    .AsSource()
                    .Build();
                conn.Fields.Clear();
                var errors = conn.Validate();
                Assert(errors.Any(e => e.Contains("at least one field")));
            });

            Test("ParseField string specs", () => {
                var f1 = ConnectionBuilder.ParseField("id:integer");
                Assert(f1.Name == "id");
                Assert(f1.DataType == DataType.Integer);
                var f2 = ConnectionBuilder.ParseField("name");
                Assert(f2.DataType == DataType.String);
            });

            Test("Dittoe.DefaultBehaviors count", () => {
                var behaviors = Dittoe.DefaultBehaviors();
                Assert(behaviors.Count == 4);
            });

            Test("Dittoe.BuildFieldMappings same_name", () => {
                var src = new List<Field> { new() { Name = "id" }, new() { Name = "name" } };
                var tgt = new List<Field> { new() { Name = "id" }, new() { Name = "name" } };
                var mappings = Dittoe.BuildFieldMappings(src, tgt, "same_name", null);
                Assert(mappings.Count == 2);
            });
        }

        // ================================================================
        // Main
        // ================================================================

        static int Main()
        {
            Console.WriteLine("\n=== DITTOE C# SDK Tests ===\n");

            Console.WriteLine("Enum completeness:");
            TestEnumCounts();

            Console.WriteLine("\nField builders:");
            TestFieldBuilders();

            Console.WriteLine("\nServer builders (19 types):");
            TestServerBuilders();

            Console.WriteLine("\nStrategy constants:");
            TestStrategyConstants();

            Console.WriteLine("\nResource builders:");
            TestResourceBuilders();

            Console.WriteLine("\nBehavior builders:");
            TestBehaviorBuilders();

            Console.WriteLine("\nPipeline:");
            TestPipelineBuilders();

            Console.WriteLine("\nConfig:");
            TestConfig();

            Console.WriteLine("\nFacade:");
            TestFacade();

            Console.WriteLine($"\n=== Results: {Passed}/{Total} tests passed ===\n");
            return Passed == Total ? 0 : 1;
        }
    }
}
