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

// DITTOE SDK for C#
// All enums, models, and strategy constants for Pipeline Metadata Documents.
// Copyright (c) 2026 EveryArrow. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DittoeSDK
{
    // ========================================================================
    // Enums (9)
    // ========================================================================

    public enum DataType
    {
        String, Integer, Double, Decimal, Boolean,
        Date, DateTime, Time, UUID,
        TimeWithTimeZone, DateTimeWithTimeZone,
        JSON, JSONB, Array, Number, Bytes, XML
    }

    public enum BehaviorType
    {
        Formatting, SourceRetrieval, TargetInsertUpdate,
        Transformation, Encryption, Logging, Instrumentation,
        lineageTracking, KeyGeneration, Masking,
        Deduplication, SchemaEvolution, Retry,
        SchemaValidation, DQRuleChecks, Archive,
        MicroBatch, DataScienceAlgorithms, CustomCode,
        Compression, ExternalWidget, Cache
    }

    public enum ServerType { Stream, API, File, Database }

    public enum TemplateType
    {
        FileToFile, FileToDatabase, FileToStream, FileToAPI,
        DatabaseToDatabase, DatabaseToFile, DatabaseToStream, DatabaseToAPI,
        StreamToStream, StreamToFile, StreamToDatabase, StreamToAPI,
        APIToAPI, APIToFile, APIToDatabase, APIToStream
    }

    public enum StageType { Begin, Acquire, Process, Package, Transmit, End }
    public enum ProcessExecutionType { SYNC, ASYNC, QUEUED, JOINED }

    public enum DatabaseType
    {
        PostgreSQLServer, MySQLServer, MSSQLServer, SQLiteServer, SnowflakeServer
    }

    public enum FileType
    {
        LocalFileServer, S3Server, GCSServer, AzureServer, DropboxServer,
        SFTPServer, FTPServer, IBMServer, OracleServer, DigitalOceanServer, GoogleDriveServer
    }

    public enum APIType { BigQueryServer, SalesforceServer }

    // ========================================================================
    // Models
    // ========================================================================

    public class Field
    {
        public string Name { get; set; } = "";
        public DataType DataType { get; set; } = DataType.String;
        public bool IsNullable { get; set; } = true;
        public string Description { get; set; } = "";
        public string Expression { get; set; } = "";

        public Dictionary<string, object> ToDict()
        {
            var d = new Dictionary<string, object>
            {
                ["name"] = Name,
                ["dataType"] = DataType.ToString(),
                ["isNullable"] = IsNullable
            };
            if (!string.IsNullOrEmpty(Description)) d["description"] = Description;
            if (!string.IsNullOrEmpty(Expression)) d["expression"] = Expression;
            return d;
        }
    }

    public class Server
    {
        public int Id { get; set; }
        public string Name { get; set; } = "";
        public ServerType Type { get; set; } = ServerType.Database;
        public Dictionary<string, string> ConnectionDetails { get; set; } = new();
        public Dictionary<string, string> AdditionalProperties { get; set; } = new();

        public Dictionary<string, object> ToDict()
        {
            return new Dictionary<string, object>
            {
                ["id"] = Id,
                ["name"] = Name,
                ["type"] = Type.ToString(),
                ["connectionDetails"] = ConnectionDetails,
                ["additionalProperties"] = AdditionalProperties
            };
        }
    }

    public class Resource
    {
        public int ResourceID { get; set; } = 1;
        public string ResourceName { get; set; } = "";
        public Server Server { get; set; } = new();
        public string Strategy { get; set; } = "";
        public Dictionary<string, string> ResourceProperties { get; set; } = new();
        public List<Field> Fields { get; set; } = new();

        public Dictionary<string, object> ToDict()
        {
            return new Dictionary<string, object>
            {
                ["resourceID"] = ResourceID,
                ["resourceName"] = ResourceName,
                ["server"] = Server.ToDict(),
                ["strategy"] = Strategy,
                ["resourceProperties"] = ResourceProperties,
                ["fields"] = Fields.Select(f => f.ToDict()).ToList()
            };
        }
    }

    public class DQRule
    {
        public string RuleName { get; set; } = "";
        public string RuleExpression { get; set; } = "";

        public Dictionary<string, object> ToDict()
        {
            return new Dictionary<string, object>
            {
                ["ruleName"] = RuleName,
                ["ruleExpression"] = RuleExpression
            };
        }
    }

    public class Behavior
    {
        public string Name { get; set; } = "";
        public BehaviorType BehaviorType { get; set; }
        public string Description { get; set; } = "";
        public Dictionary<string, string> Properties { get; set; } = new();
        public bool IsStaged { get; set; }
        public string PluginReference { get; set; } = "";
        public int Order { get; set; }
        public StageType StageType { get; set; }

        public Dictionary<string, object> ToDict()
        {
            var d = new Dictionary<string, object>
            {
                ["name"] = Name,
                ["behaviorType"] = BehaviorType.ToString(),
                ["description"] = Description,
                ["properties"] = Properties
            };
            if (IsStaged)
            {
                d["pluginReference"] = PluginReference;
                d["order"] = Order;
                d["stageType"] = StageType.ToString();
            }
            return d;
        }
    }

    public class SourceToTargetMapping
    {
        public Field SourceField { get; set; } = new();
        public Field TargetField { get; set; } = new();

        public Dictionary<string, object> ToDict()
        {
            return new Dictionary<string, object>
            {
                ["sourceField"] = SourceField.ToDict(),
                ["targetField"] = TargetField.ToDict()
            };
        }
    }

    public class ResourceDependencyMapping
    {
        public Resource Resource { get; set; } = new();
        public List<Resource> Dependencies { get; set; } = new();

        public Dictionary<string, object> ToDict()
        {
            return new Dictionary<string, object>
            {
                ["resource"] = Resource.ToDict(),
                ["dependencies"] = Dependencies.Select(d => d.ToDict()).ToList()
            };
        }
    }

    public class PipelineMetadata
    {
        public string Id { get; set; } = "";
        public string ShortName { get; set; } = "";
        public string Description { get; set; } = "";
        public TemplateType TemplateType { get; set; }
        public Resource SourceResource { get; set; } = new();
        public Resource TargetResource { get; set; } = new();
        public List<SourceToTargetMapping> FieldMappings { get; set; } = new();
        public List<Behavior> Behaviors { get; set; } = new();
        public Dictionary<string, string> GlobalProperties { get; set; } = new();
        public string CreateDate { get; set; } = "";
        public string CreatedBy { get; set; } = "";
        public string LastUpdatedBy { get; set; } = "";
        public string LastUpdatedDate { get; set; } = "";

        public Dictionary<string, object> ToDict()
        {
            var d = new Dictionary<string, object>
            {
                ["id"] = Id,
                ["shortName"] = ShortName,
                ["description"] = Description,
                ["templateType"] = TemplateType.ToString(),
                ["globalProperties"] = GlobalProperties,
                ["behaviors"] = Behaviors.Select(b => b.ToDict()).ToList(),
                ["sourceResourceToProcess"] = SourceResource.ToDict(),
                ["targetResourceToProcess"] = TargetResource.ToDict(),
                ["S2TMap"] = FieldMappings.Select(m => m.ToDict()).ToList()
            };
            if (!string.IsNullOrEmpty(CreateDate)) d["createDate"] = CreateDate;
            if (!string.IsNullOrEmpty(CreatedBy)) d["createdBy"] = CreatedBy;
            return d;
        }

        public string ToJson()
        {
            return JsonSerializer.Serialize(ToDict(),
                new JsonSerializerOptions { WriteIndented = false });
        }

        public void Save(string path)
        {
            var dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            File.WriteAllText(path, ToJson());
        }
    }

    // ========================================================================
    // Strategy Constants (all 45)
    // ========================================================================

    public static class ReadStrategy
    {
        public const string JdbcSelect = "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy";
        public const string LocalCsv = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.CSVReadStrategy";
        public const string LocalJson = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.JSONReadStrategy";
        public const string S3Csv = "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.CSVReadStrategy";
        public const string GcsCsv = "com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy.CSVReadStrategy";
        public const string AzureCsv = "com.everyarrow.dittoe.io.file.servers.azure.reader.strategy.CSVReadStrategy";
        public const string DropboxCsv = "com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy.CSVReadStrategy";
        public const string SftpCsv = "com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy.CSVReadStrategy";
        public const string FtpCsv = "com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy.CSVReadStrategy";
        public const string IbmCsv = "com.everyarrow.dittoe.io.file.servers.ibm.reader.strategy.CSVReadStrategy";
        public const string OracleCsv = "com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy.CSVReadStrategy";
        public const string DigitalOceanCsv = "com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy.CSVReadStrategy";
        public const string GoogleDriveCsv = "com.everyarrow.dittoe.io.file.servers.googledrive.reader.strategy.CSVReadStrategy";
        public const string BigQueryQuery = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.QueryStrategy";
        public const string BigQueryAvro = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.AvroStrategy";
        public const string Salesforce = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI";
    }

    public static class WriteStrategy
    {
        public const string JdbcInsert = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy";
        public const string JdbcReplace = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.ReplaceStrategy";
        public const string PgUpsert = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy";
        public const string PgInsertDoNothing = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertDoNothingOnConflictStrategy";
        public const string PgCopy = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy";
        public const string PgCopyOrAutoId = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyOrAutoIdStrategy";
        public const string PgInsertOrUpdate = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertOrUpdateStrategy";
        public const string MssqlMerge = "com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy";
        public const string SqliteUpsert = "com.everyarrow.dittoe.io.database.servers.sqlite.writer.strategy.UpsertOnConflictStrategy";
        public const string LocalCsv = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.CSVWriteStrategy";
        public const string LocalJson = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.JSONWriteStrategy";
        public const string S3Csv = "com.everyarrow.dittoe.io.file.servers.s3.writer.strategy.CSVWriteStrategy";
        public const string GcsCsv = "com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy.CSVWriteStrategy";
        public const string AzureCsv = "com.everyarrow.dittoe.io.file.servers.azure.writer.strategy.CSVWriteStrategy";
        public const string DropboxCsv = "com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy.CSVWriteStrategy";
        public const string SftpCsv = "com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy.CSVWriteStrategy";
        public const string FtpCsv = "com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy.CSVWriteStrategy";
        public const string IbmCsv = "com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy.CSVWriteStrategy";
        public const string OracleCsv = "com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy.CSVWriteStrategy";
        public const string DigitalOceanCsv = "com.everyarrow.dittoe.io.file.servers.digitalocean.writer.strategy.CSVWriteStrategy";
        public const string GoogleDriveCsv = "com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy.CSVWriteStrategy";
        public const string BigQuery = "com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.DataStoreStrategy";
        public const string Salesforce = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI";
    }
}
