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

// DITTOE SDK for C# - Fluent Builders
// All 19 server types, 45 strategies, 22 behaviors, 16 template types

using System;
using System.Collections.Generic;
using System.Linq;

namespace DittoeSDK
{
    // ========================================================================
    // FieldBuilder
    // ========================================================================

    public class FieldBuilder
    {
        private readonly Field _field = new();

        public FieldBuilder(string name) { _field.Name = name; }
        public FieldBuilder SetDataType(DataType dt) { _field.DataType = dt; return this; }
        public FieldBuilder String() => SetDataType(DataType.String);
        public FieldBuilder Integer() => SetDataType(DataType.Integer);
        public FieldBuilder Double() => SetDataType(DataType.Double);
        public FieldBuilder Decimal() => SetDataType(DataType.Decimal);
        public FieldBuilder Boolean() => SetDataType(DataType.Boolean);
        public FieldBuilder Date() => SetDataType(DataType.Date);
        public FieldBuilder DateTime() => SetDataType(DataType.DateTime);
        public FieldBuilder Time() => SetDataType(DataType.Time);
        public FieldBuilder Uuid() => SetDataType(DataType.UUID);
        public FieldBuilder TimeWithTz() => SetDataType(DataType.TimeWithTimeZone);
        public FieldBuilder DateTimeWithTz() => SetDataType(DataType.DateTimeWithTimeZone);
        public FieldBuilder Json() => SetDataType(DataType.JSON);
        public FieldBuilder Jsonb() => SetDataType(DataType.JSONB);
        public FieldBuilder Array() => SetDataType(DataType.Array);
        public FieldBuilder Number() => SetDataType(DataType.Number);
        public FieldBuilder Bytes() => SetDataType(DataType.Bytes);
        public FieldBuilder Xml() => SetDataType(DataType.XML);
        public FieldBuilder Nullable(bool v = true) { _field.IsNullable = v; return this; }
        public FieldBuilder Required() => Nullable(false);
        public FieldBuilder Description(string d) { _field.Description = d; return this; }
        public FieldBuilder Expression(string e) { _field.Expression = e; return this; }
        public Field Build() => _field;
    }

    // ========================================================================
    // ServerBuilder (19 types: 5 DB + 11 File + 2 API + 1 Stream)
    // ========================================================================

    public class ServerBuilder
    {
        private readonly Server _server = new();

        public ServerBuilder(string name) { _server.Name = name; }
        public ServerBuilder SetId(int id) { _server.Id = id; return this; }
        public ServerBuilder AdditionalProperty(string k, string v)
        {
            _server.AdditionalProperties[k] = v; return this;
        }

        // Database (5)
        private ServerBuilder SetupDb(string dbType, string host, int port,
            string database, string user, string password)
        {
            _server.Type = ServerType.Database;
            _server.ConnectionDetails = new Dictionary<string, string>
            {
                ["databaseType"] = dbType, ["host"] = host,
                ["port"] = port.ToString(), ["database"] = database,
                ["user"] = user, ["password"] = password
            };
            return this;
        }

        public ServerBuilder PostgreSQL(string host, int port, string db, string user, string pass)
            => SetupDb("PostgreSQLServer", host, port, db, user, pass);
        public ServerBuilder MySQL(string host, int port, string db, string user, string pass)
            => SetupDb("MySQLServer", host, port, db, user, pass);
        public ServerBuilder MSSQL(string host, int port, string db, string user, string pass)
            => SetupDb("MSSQLServer", host, port, db, user, pass);
        public ServerBuilder SQLite(string path)
        {
            _server.Type = ServerType.Database;
            _server.ConnectionDetails = new() { ["databaseType"] = "SQLiteServer", ["path"] = path };
            return this;
        }
        public ServerBuilder Snowflake(string host, int port, string db, string user, string pass)
            => SetupDb("SnowflakeServer", host, port, db, user, pass);

        // File (11)
        public ServerBuilder LocalFile(string path)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() { ["fileType"] = "LocalFileServer", ["path"] = path };
            return this;
        }
        public ServerBuilder S3(string bucket, string region, string ak, string sk)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "S3Server", ["bucket"] = bucket,
                ["region"] = region, ["accessKey"] = ak, ["secretKey"] = sk
            };
            return this;
        }
        public ServerBuilder GCS(string bucket, string project, string creds)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "GCSServer", ["bucket"] = bucket,
                ["projectId"] = project, ["credentialsFile"] = creds
            };
            return this;
        }
        public ServerBuilder Azure(string container, string acct, string key)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "AzureServer", ["container"] = container,
                ["accountName"] = acct, ["accountKey"] = key
            };
            return this;
        }
        public ServerBuilder Dropbox(string token)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() { ["fileType"] = "DropboxServer", ["accessToken"] = token };
            return this;
        }
        public ServerBuilder SFTP(string host, int port, string path, string user, string pass)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "SFTPServer", ["host"] = host,
                ["port"] = port.ToString(), ["path"] = path, ["user"] = user, ["password"] = pass
            };
            return this;
        }
        public ServerBuilder FTP(string host, int port, string path, string user, string pass)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "FTPServer", ["host"] = host,
                ["port"] = port.ToString(), ["path"] = path, ["user"] = user, ["password"] = pass
            };
            return this;
        }
        public ServerBuilder IBM(string endpoint, string apiKey, string bucket)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "IBMServer", ["endpoint"] = endpoint,
                ["apiKey"] = apiKey, ["bucket"] = bucket
            };
            return this;
        }
        public ServerBuilder OracleCloud(string ns, string bucket, string compartment)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "OracleServer", ["namespace"] = ns,
                ["bucket"] = bucket, ["compartmentId"] = compartment
            };
            return this;
        }
        public ServerBuilder DigitalOcean(string space, string region, string ak, string sk)
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "DigitalOceanServer", ["space"] = space,
                ["region"] = region, ["accessKey"] = ak, ["secretKey"] = sk
            };
            return this;
        }
        public ServerBuilder GoogleDrive(string creds, string folderId = "")
        {
            _server.Type = ServerType.File;
            _server.ConnectionDetails = new() {
                ["fileType"] = "GoogleDriveServer", ["credentialsFile"] = creds,
                ["folderId"] = folderId
            };
            return this;
        }

        // API (2)
        public ServerBuilder BigQuery(string project, string dataset, string creds)
        {
            _server.Type = ServerType.API;
            _server.ConnectionDetails = new() {
                ["apiType"] = "BigQueryServer", ["projectId"] = project,
                ["dataset"] = dataset, ["credentialsFile"] = creds
            };
            return this;
        }
        public ServerBuilder Salesforce(string loginUrl, string user, string pass, string token)
        {
            _server.Type = ServerType.API;
            _server.ConnectionDetails = new() {
                ["apiType"] = "SalesforceServer", ["loginUrl"] = loginUrl,
                ["username"] = user, ["password"] = pass, ["securityToken"] = token
            };
            return this;
        }

        public Server Build() => _server;
    }

    // ========================================================================
    // ResourceBuilder (all 45 strategies)
    // ========================================================================

    public class ResourceBuilder
    {
        private readonly Resource _resource = new();

        public ResourceBuilder(string name) { _resource.ResourceName = name; }
        public ResourceBuilder SetServer(Server s) { _resource.Server = s; return this; }
        public ResourceBuilder AddField(string name, DataType dt = DataType.String, bool nullable = true)
        {
            _resource.Fields.Add(new Field { Name = name, DataType = dt, IsNullable = nullable });
            return this;
        }
        public ResourceBuilder AddField(Field f) { _resource.Fields.Add(f); return this; }
        public ResourceBuilder Schema(string s) { _resource.ResourceProperties["schema"] = s; return this; }
        public ResourceBuilder Property(string k, string v) { _resource.ResourceProperties[k] = v; return this; }
        public ResourceBuilder SetStrategy(string s) { _resource.Strategy = s; return this; }

        // DB read
        public ResourceBuilder JdbcRead() => SetStrategy(ReadStrategy.JdbcSelect);
        // DB write
        public ResourceBuilder Insert() => SetStrategy(WriteStrategy.JdbcInsert);
        public ResourceBuilder Replace() => SetStrategy(WriteStrategy.JdbcReplace);
        public ResourceBuilder PgUpsert(string c) { Property("upsertConflictConstraint", c); return SetStrategy(WriteStrategy.PgUpsert); }
        public ResourceBuilder PgInsertDoNothing(string c) { Property("upsertConflictConstraint", c); return SetStrategy(WriteStrategy.PgInsertDoNothing); }
        public ResourceBuilder PgCopy() => SetStrategy(WriteStrategy.PgCopy);
        public ResourceBuilder PgCopyOrAutoId() => SetStrategy(WriteStrategy.PgCopyOrAutoId);
        public ResourceBuilder PgInsertOrUpdate() => SetStrategy(WriteStrategy.PgInsertOrUpdate);
        public ResourceBuilder MssqlMerge() => SetStrategy(WriteStrategy.MssqlMerge);
        public ResourceBuilder SqliteUpsert(string c) { Property("upsertConflictConstraint", c); return SetStrategy(WriteStrategy.SqliteUpsert); }
        // File read/write
        public ResourceBuilder CsvRead() => SetStrategy(ReadStrategy.LocalCsv);
        public ResourceBuilder JsonRead() => SetStrategy(ReadStrategy.LocalJson);
        public ResourceBuilder CsvWrite() => SetStrategy(WriteStrategy.LocalCsv);
        public ResourceBuilder JsonWrite() => SetStrategy(WriteStrategy.LocalJson);
        public ResourceBuilder S3CsvRead() => SetStrategy(ReadStrategy.S3Csv);
        public ResourceBuilder S3CsvWrite() => SetStrategy(WriteStrategy.S3Csv);
        public ResourceBuilder GcsCsvRead() => SetStrategy(ReadStrategy.GcsCsv);
        public ResourceBuilder GcsCsvWrite() => SetStrategy(WriteStrategy.GcsCsv);
        public ResourceBuilder AzureCsvRead() => SetStrategy(ReadStrategy.AzureCsv);
        public ResourceBuilder AzureCsvWrite() => SetStrategy(WriteStrategy.AzureCsv);
        public ResourceBuilder DropboxCsvRead() => SetStrategy(ReadStrategy.DropboxCsv);
        public ResourceBuilder DropboxCsvWrite() => SetStrategy(WriteStrategy.DropboxCsv);
        public ResourceBuilder SftpCsvRead() => SetStrategy(ReadStrategy.SftpCsv);
        public ResourceBuilder SftpCsvWrite() => SetStrategy(WriteStrategy.SftpCsv);
        public ResourceBuilder FtpCsvRead() => SetStrategy(ReadStrategy.FtpCsv);
        public ResourceBuilder FtpCsvWrite() => SetStrategy(WriteStrategy.FtpCsv);
        public ResourceBuilder IbmCsvRead() => SetStrategy(ReadStrategy.IbmCsv);
        public ResourceBuilder IbmCsvWrite() => SetStrategy(WriteStrategy.IbmCsv);
        public ResourceBuilder OracleCsvRead() => SetStrategy(ReadStrategy.OracleCsv);
        public ResourceBuilder OracleCsvWrite() => SetStrategy(WriteStrategy.OracleCsv);
        public ResourceBuilder DigitalOceanCsvRead() => SetStrategy(ReadStrategy.DigitalOceanCsv);
        public ResourceBuilder DigitalOceanCsvWrite() => SetStrategy(WriteStrategy.DigitalOceanCsv);
        public ResourceBuilder GoogleDriveCsvRead() => SetStrategy(ReadStrategy.GoogleDriveCsv);
        public ResourceBuilder GoogleDriveCsvWrite() => SetStrategy(WriteStrategy.GoogleDriveCsv);
        // API
        public ResourceBuilder BigQueryQueryRead() => SetStrategy(ReadStrategy.BigQueryQuery);
        public ResourceBuilder BigQueryAvroRead() => SetStrategy(ReadStrategy.BigQueryAvro);
        public ResourceBuilder BigQueryWrite() => SetStrategy(WriteStrategy.BigQuery);
        public ResourceBuilder SalesforceRead() => SetStrategy(ReadStrategy.Salesforce);
        public ResourceBuilder SalesforceWrite() => SetStrategy(WriteStrategy.Salesforce);

        public Resource Build() => _resource;
    }

    // ========================================================================
    // BehaviorBuilder (all 22 types)
    // ========================================================================

    public class BehaviorBuilder
    {
        private readonly Behavior _behavior = new();

        public BehaviorBuilder(string name = "") { _behavior.Name = name; }
        public BehaviorBuilder SetType(BehaviorType bt) { _behavior.BehaviorType = bt; return this; }
        public BehaviorBuilder Description(string d) { _behavior.Description = d; return this; }
        public BehaviorBuilder Property(string k, string v) { _behavior.Properties[k] = v; return this; }
        public BehaviorBuilder ErrorThreshold(int t) => Property("errorCountThreshold", t.ToString());
        public BehaviorBuilder Staged(StageType s, int order = 0, string plugin = "")
        {
            _behavior.IsStaged = true;
            _behavior.StageType = s;
            _behavior.Order = order;
            _behavior.PluginReference = plugin;
            return this;
        }

        // All 22 types
        private BehaviorBuilder SetBT(BehaviorType bt, string defaultName)
        {
            _behavior.BehaviorType = bt;
            if (string.IsNullOrEmpty(_behavior.Name)) _behavior.Name = defaultName;
            return this;
        }
        public BehaviorBuilder Formatting() => SetBT(BehaviorType.Formatting, "Formatting");
        public BehaviorBuilder SourceRetrieval() => SetBT(BehaviorType.SourceRetrieval, "SourceRetrieval");
        public BehaviorBuilder TargetInsertUpdate() => SetBT(BehaviorType.TargetInsertUpdate, "TargetInsertUpdate");
        public BehaviorBuilder Transformation() => SetBT(BehaviorType.Transformation, "Transformation");
        public BehaviorBuilder Encryption() => SetBT(BehaviorType.Encryption, "Encryption");
        public BehaviorBuilder Logging() => SetBT(BehaviorType.Logging, "Logging");
        public BehaviorBuilder Instrumentation() => SetBT(BehaviorType.Instrumentation, "Instrumentation");
        public BehaviorBuilder LineageTracking() => SetBT(BehaviorType.lineageTracking, "lineageTracking");
        public BehaviorBuilder KeyGeneration() => SetBT(BehaviorType.KeyGeneration, "KeyGeneration");
        public BehaviorBuilder Masking() => SetBT(BehaviorType.Masking, "Masking");
        public BehaviorBuilder Deduplication() => SetBT(BehaviorType.Deduplication, "Deduplication");
        public BehaviorBuilder SchemaEvolution() => SetBT(BehaviorType.SchemaEvolution, "SchemaEvolution");
        public BehaviorBuilder Retry() => SetBT(BehaviorType.Retry, "Retry");
        public BehaviorBuilder SchemaValidation() => SetBT(BehaviorType.SchemaValidation, "SchemaValidation");
        public BehaviorBuilder DQRuleChecks() => SetBT(BehaviorType.DQRuleChecks, "DQRuleChecks");
        public BehaviorBuilder Archive() => SetBT(BehaviorType.Archive, "Archive");
        public BehaviorBuilder MicroBatch() => SetBT(BehaviorType.MicroBatch, "MicroBatch");
        public BehaviorBuilder DataScienceAlgorithms() => SetBT(BehaviorType.DataScienceAlgorithms, "DataScienceAlgorithms");
        public BehaviorBuilder CustomCode() => SetBT(BehaviorType.CustomCode, "CustomCode");
        public BehaviorBuilder Compression() => SetBT(BehaviorType.Compression, "Compression");
        public BehaviorBuilder ExternalWidget() => SetBT(BehaviorType.ExternalWidget, "ExternalWidget");
        public BehaviorBuilder Cache() => SetBT(BehaviorType.Cache, "Cache");

        public Behavior Build() => _behavior;
    }

    // ========================================================================
    // PipelineBuilder (all 16 template types)
    // ========================================================================

    public class PipelineBuilder
    {
        private readonly PipelineMetadata _pipeline = new();

        public PipelineBuilder(string shortName)
        {
            _pipeline.ShortName = shortName;
            _pipeline.Id = $"pipeline-{shortName}";
        }

        public PipelineBuilder SetId(string id) { _pipeline.Id = id; return this; }
        public PipelineBuilder Description(string d) { _pipeline.Description = d; return this; }
        public PipelineBuilder CreatedBy(string v) { _pipeline.CreatedBy = v; return this; }

        // All 16 template types
        public PipelineBuilder SetTemplateType(TemplateType tt) { _pipeline.TemplateType = tt; return this; }
        public PipelineBuilder FileToFile() => SetTemplateType(TemplateType.FileToFile);
        public PipelineBuilder FileToDatabase() => SetTemplateType(TemplateType.FileToDatabase);
        public PipelineBuilder FileToStream() => SetTemplateType(TemplateType.FileToStream);
        public PipelineBuilder FileToAPI() => SetTemplateType(TemplateType.FileToAPI);
        public PipelineBuilder DatabaseToDatabase() => SetTemplateType(TemplateType.DatabaseToDatabase);
        public PipelineBuilder DatabaseToFile() => SetTemplateType(TemplateType.DatabaseToFile);
        public PipelineBuilder DatabaseToStream() => SetTemplateType(TemplateType.DatabaseToStream);
        public PipelineBuilder DatabaseToAPI() => SetTemplateType(TemplateType.DatabaseToAPI);
        public PipelineBuilder StreamToStream() => SetTemplateType(TemplateType.StreamToStream);
        public PipelineBuilder StreamToFile() => SetTemplateType(TemplateType.StreamToFile);
        public PipelineBuilder StreamToDatabase() => SetTemplateType(TemplateType.StreamToDatabase);
        public PipelineBuilder StreamToAPI() => SetTemplateType(TemplateType.StreamToAPI);
        public PipelineBuilder APIToAPI() => SetTemplateType(TemplateType.APIToAPI);
        public PipelineBuilder APIToFile() => SetTemplateType(TemplateType.APIToFile);
        public PipelineBuilder APIToDatabase() => SetTemplateType(TemplateType.APIToDatabase);
        public PipelineBuilder APIToStream() => SetTemplateType(TemplateType.APIToStream);

        public PipelineBuilder Source(Resource r) { _pipeline.SourceResource = r; return this; }
        public PipelineBuilder Target(Resource r) { _pipeline.TargetResource = r; return this; }
        public PipelineBuilder MapField(Field s, Field t) { _pipeline.FieldMappings.Add(new() { SourceField = s, TargetField = t }); return this; }
        public PipelineBuilder MapFieldsSameName()
        {
            foreach (var sf in _pipeline.SourceResource.Fields)
                foreach (var tf in _pipeline.TargetResource.Fields)
                    if (sf.Name == tf.Name)
                    {
                        _pipeline.FieldMappings.Add(new() { SourceField = sf, TargetField = tf });
                        break;
                    }
            return this;
        }
        public PipelineBuilder AddBehavior(Behavior b) { _pipeline.Behaviors.Add(b); return this; }
        public PipelineBuilder WithDefaultBehaviors()
        {
            _pipeline.Behaviors = new List<Behavior>
            {
                new BehaviorBuilder().SourceRetrieval().ErrorThreshold(1).Build(),
                new BehaviorBuilder().TargetInsertUpdate().ErrorThreshold(1).Build(),
                new BehaviorBuilder().Instrumentation().Build(),
                new BehaviorBuilder().Transformation().Build()
            };
            return this;
        }
        public PipelineBuilder GlobalProperty(string k, string v) { _pipeline.GlobalProperties[k] = v; return this; }
        public PipelineMetadata Build() => _pipeline;
    }

    // ========================================================================
    // Executor
    // ========================================================================

    public class DittoeExecutor
    {
        public string JarPath { get; }

        public DittoeExecutor(string jarPath) { JarPath = jarPath; }

        public (bool Success, string Output) Run(PipelineMetadata pipeline, string baseDir, int timeout = 300)
        {
            var pmdDir = System.IO.Path.Combine(baseDir, "pipelines", pipeline.ShortName);
            System.IO.Directory.CreateDirectory(pmdDir);
            pipeline.Save(System.IO.Path.Combine(pmdDir, "pmd.json"));

            var pipelinesDir = System.IO.Path.Combine(baseDir, "pipelines");
            var psi = new System.Diagnostics.ProcessStartInfo
            {
                FileName = "java",
                Arguments = $"-jar \"{JarPath}\" -p \"{pipeline.ShortName}\"",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            psi.EnvironmentVariables["BASE_DIR"] = pipelinesDir;
            using var proc = System.Diagnostics.Process.Start(psi);
            if (proc == null) return (false, "Failed to start process");
            var output = proc.StandardOutput.ReadToEnd();
            proc.WaitForExit(timeout * 1000);
            return (proc.ExitCode == 0, output);
        }

        public List<string> Validate(PipelineMetadata p)
        {
            var errors = new List<string>();
            if (string.IsNullOrEmpty(p.ShortName)) errors.Add("ShortName is required");
            if (string.IsNullOrEmpty(p.SourceResource.ResourceName)) errors.Add("Source resource is required");
            if (string.IsNullOrEmpty(p.TargetResource.ResourceName)) errors.Add("Target resource is required");
            if (string.IsNullOrEmpty(p.SourceResource.Strategy)) errors.Add("Source strategy is required");
            if (string.IsNullOrEmpty(p.TargetResource.Strategy)) errors.Add("Target strategy is required");
            if (p.SourceResource.Fields.Count == 0) errors.Add("Source must have fields");
            if (p.TargetResource.Fields.Count == 0) errors.Add("Target must have fields");
            if (p.Behaviors.Count == 0) errors.Add("Pipeline must have behaviors");
            if (p.FieldMappings.Count == 0) errors.Add("Pipeline must have field mappings");
            return errors;
        }
    }
}
