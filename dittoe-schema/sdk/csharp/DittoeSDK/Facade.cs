// DITTOE SDK for C# - Facade
// High-level API for data migration. One function, any server pair.

using System;
using System.Collections.Generic;
using System.Linq;

namespace DittoeSDK
{
    // ========================================================================
    // Connection
    // ========================================================================

    /// <summary>
    /// Encapsulates everything needed for one end of a pipeline.
    /// Built by ConnectionBuilder. Contains server, fields, strategy, etc.
    /// </summary>
    public class Connection
    {
        public string Name { get; set; } = "";
        public Server Server { get; set; } = new();
        public List<Field> Fields { get; set; } = new();
        public string Strategy { get; set; } = "";
        public string SchemaName { get; set; } = "";
        public Dictionary<string, string> ResourceProperties { get; set; } = new();
        internal string DbType { get; set; } = "";

        /// <summary>
        /// Validate this connection. Returns list of error messages.
        /// </summary>
        public List<string> Validate()
        {
            var errors = new List<string>();
            if (string.IsNullOrEmpty(Name))
                errors.Add("Connection name is required");
            if (Server == null || string.IsNullOrEmpty(Server.Name))
                errors.Add($"Connection '{Name}': server is required");
            if (Fields.Count == 0)
                errors.Add($"Connection '{Name}': at least one field is required");
            if (string.IsNullOrEmpty(Strategy))
                errors.Add($"Connection '{Name}': strategy is required");
            return errors;
        }
    }

    // ========================================================================
    // ConnectionBuilder
    // ========================================================================

    /// <summary>
    /// Fluent builder for creating a Connection (source or target).
    /// 
    /// Usage:
    ///   var source = new ConnectionBuilder("products")
    ///       .PostgreSQL("host", 5433, "mydb", "user", "pass")
    ///       .Schema("production")
    ///       .Fields("id:integer", "name:string", "price:decimal")
    ///       .AsSource()
    ///       .Build();
    /// </summary>
    public class ConnectionBuilder
    {
        private readonly string _name;
        private readonly ServerBuilder _serverBuilder;
        private readonly List<Field> _fields = new();
        private string _strategy = "";
        private string _schema = "";
        private readonly Dictionary<string, string> _properties = new();
        private bool? _isSource;
        private string _dbType = "";
        private bool _serverBuilt;

        public ConnectionBuilder(string resourceName)
        {
            _name = resourceName;
            _serverBuilder = new ServerBuilder(resourceName);
        }

        // ---- Server type methods ----

        public ConnectionBuilder PostgreSQL(string host, int port, string db, string user, string pass)
        { _serverBuilder.PostgreSQL(host, port, db, user, pass); _dbType = "PostgreSQLServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder MySQL(string host, int port, string db, string user, string pass)
        { _serverBuilder.MySQL(host, port, db, user, pass); _dbType = "MySQLServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder MSSQL(string host, int port, string db, string user, string pass)
        { _serverBuilder.MSSQL(host, port, db, user, pass); _dbType = "MSSQLServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder SQLite(string path)
        { _serverBuilder.SQLite(path); _dbType = "SQLiteServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder Snowflake(string host, int port, string db, string user, string pass)
        { _serverBuilder.Snowflake(host, port, db, user, pass); _dbType = "SnowflakeServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder LocalFile(string path)
        { _serverBuilder.LocalFile(path); _dbType = "LocalFileServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder S3(string bucket, string region, string ak, string sk)
        { _serverBuilder.S3(bucket, region, ak, sk); _dbType = "S3Server"; _serverBuilt = true; return this; }

        public ConnectionBuilder GCS(string bucket, string project, string creds)
        { _serverBuilder.GCS(bucket, project, creds); _dbType = "GCSServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder Azure(string container, string acct, string key)
        { _serverBuilder.Azure(container, acct, key); _dbType = "AzureServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder SFTP(string host, int port, string path, string user, string pass)
        { _serverBuilder.SFTP(host, port, path, user, pass); _dbType = "SFTPServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder FTP(string host, int port, string path, string user, string pass)
        { _serverBuilder.FTP(host, port, path, user, pass); _dbType = "FTPServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder BigQuery(string project, string dataset, string creds)
        { _serverBuilder.BigQuery(project, dataset, creds); _dbType = "BigQueryServer"; _serverBuilt = true; return this; }

        public ConnectionBuilder Salesforce(string loginUrl, string user, string pass, string token)
        { _serverBuilder.Salesforce(loginUrl, user, pass, token); _dbType = "SalesforceServer"; _serverBuilt = true; return this; }

        // ---- Configuration ----

        public ConnectionBuilder Schema(string schemaName) { _schema = schemaName; return this; }

        public ConnectionBuilder Fields(params string[] fieldSpecs)
        {
            _fields.Clear();
            foreach (var spec in fieldSpecs)
                _fields.Add(ParseField(spec));
            return this;
        }

        public ConnectionBuilder AddField(string name, DataType dt = DataType.String, bool nullable = true)
        {
            _fields.Add(new Field { Name = name, DataType = dt, IsNullable = nullable });
            return this;
        }

        public ConnectionBuilder ReadStrategy(string strategy)
        {
            _strategy = strategy.StartsWith("com.") ? strategy : ResolveStrategy(strategy, true);
            _isSource = true;
            return this;
        }

        public ConnectionBuilder WriteStrategy(string strategy)
        {
            _strategy = strategy.StartsWith("com.") ? strategy : ResolveStrategy(strategy, false);
            _isSource = false;
            return this;
        }

        public ConnectionBuilder AsSource()
        {
            _isSource = true;
            if (string.IsNullOrEmpty(_strategy))
                _strategy = GetDefaultReadStrategy(_dbType);
            return this;
        }

        public ConnectionBuilder AsTarget()
        {
            _isSource = false;
            if (string.IsNullOrEmpty(_strategy))
                _strategy = GetDefaultWriteStrategy(_dbType);
            return this;
        }

        public ConnectionBuilder Property(string key, string value) { _properties[key] = value; return this; }

        public Connection Build()
        {
            if (!_serverBuilt)
                throw new InvalidOperationException("No server type configured. Call PostgreSQL(), MySQL(), etc. first.");

            var server = _serverBuilder.Build();
            var strategy = _strategy;
            if (string.IsNullOrEmpty(strategy))
            {
                strategy = (_isSource == true || _isSource == null)
                    ? GetDefaultReadStrategy(_dbType)
                    : GetDefaultWriteStrategy(_dbType);
            }

            var props = new Dictionary<string, string>(_properties);
            if (!string.IsNullOrEmpty(_schema))
                props["schema"] = _schema;

            return new Connection
            {
                Name = _name,
                Server = server,
                Fields = new List<Field>(_fields),
                Strategy = strategy,
                SchemaName = _schema,
                ResourceProperties = props,
                DbType = _dbType,
            };
        }

        // ---- Field parsing ----

        /// <summary>
        /// Parse "name:type" spec into a Field. Defaults to String if no type given.
        /// </summary>
        public static Field ParseField(string spec)
        {
            var parts = spec.Split(':', 2);
            var name = parts[0].Trim();
            var typeName = parts.Length > 1 ? parts[1].Trim().ToLower() : "string";
            return new Field
            {
                Name = name,
                DataType = ResolveDataType(typeName),
            };
        }

        private static DataType ResolveDataType(string name) => name switch
        {
            "string" or "str" => DataType.String,
            "integer" or "int" => DataType.Integer,
            "double" or "float" => DataType.Double,
            "decimal" => DataType.Decimal,
            "boolean" or "bool" => DataType.Boolean,
            "date" => DataType.Date,
            "datetime" => DataType.DateTime,
            "time" => DataType.Time,
            "uuid" => DataType.UUID,
            "json" => DataType.JSON,
            "jsonb" => DataType.JSONB,
            "array" => DataType.Array,
            "number" => DataType.Number,
            "bytes" => DataType.Bytes,
            "xml" => DataType.XML,
            _ => DataType.String,
        };

        // ---- Default strategies ----

        private static string GetDefaultReadStrategy(string dbType) => dbType switch
        {
            "PostgreSQLServer" or "MySQLServer" or "MSSQLServer" or
            "SQLiteServer" or "SnowflakeServer" => DittoeSDK.ReadStrategy.JdbcSelect,
            "LocalFileServer" => DittoeSDK.ReadStrategy.LocalCsv,
            "S3Server" => DittoeSDK.ReadStrategy.S3Csv,
            "GCSServer" => DittoeSDK.ReadStrategy.GcsCsv,
            "AzureServer" => DittoeSDK.ReadStrategy.AzureCsv,
            "SFTPServer" => DittoeSDK.ReadStrategy.SftpCsv,
            "FTPServer" => DittoeSDK.ReadStrategy.FtpCsv,
            "BigQueryServer" => DittoeSDK.ReadStrategy.BigQueryQuery,
            "SalesforceServer" => DittoeSDK.ReadStrategy.Salesforce,
            _ => "",
        };

        private static string GetDefaultWriteStrategy(string dbType) => dbType switch
        {
            "PostgreSQLServer" or "MySQLServer" or "MSSQLServer" or
            "SQLiteServer" or "SnowflakeServer" => DittoeSDK.WriteStrategy.JdbcInsert,
            "LocalFileServer" => DittoeSDK.WriteStrategy.LocalCsv,
            "S3Server" => DittoeSDK.WriteStrategy.S3Csv,
            "GCSServer" => DittoeSDK.WriteStrategy.GcsCsv,
            "AzureServer" => DittoeSDK.WriteStrategy.AzureCsv,
            "SFTPServer" => DittoeSDK.WriteStrategy.SftpCsv,
            "FTPServer" => DittoeSDK.WriteStrategy.FtpCsv,
            "BigQueryServer" => DittoeSDK.WriteStrategy.BigQuery,
            "SalesforceServer" => DittoeSDK.WriteStrategy.Salesforce,
            _ => "",
        };

        private static string ResolveStrategy(string name, bool isRead) => name; // passthrough for unknown
    }

    // ========================================================================
    // Dittoe Facade
    // ========================================================================

    /// <summary>
    /// High-level facade for DITTOE pipeline execution.
    /// 
    /// Usage:
    ///   var config = DittoeConfig.FromEnv();
    ///   var dittoe = new Dittoe(config);
    ///   
    ///   var source = new ConnectionBuilder("products")
    ///       .PostgreSQL("src-host", 5433, "mydb", "user", "pass")
    ///       .Schema("production")
    ///       .Fields("id:integer", "name:string", "price:decimal")
    ///       .AsSource().Build();
    ///   
    ///   var target = new ConnectionBuilder("products")
    ///       .PostgreSQL("tgt-host", 5434, "mydb", "user", "pass")
    ///       .Schema("production")
    ///       .Fields("id:integer", "name:string", "price:decimal")
    ///       .AsTarget().Build();
    ///   
    ///   var result = dittoe.Migrate(source, target);
    /// </summary>
    public class Dittoe
    {
        private readonly DittoeConfig _config;
        private readonly DittoeExecutor _executor;

        public Dittoe(DittoeConfig config)
        {
            _config = config;
            _executor = new DittoeExecutor(config.JarPath);
        }

        /// <summary>
        /// Execute a data migration pipeline. One function, any server pair.
        /// </summary>
        public (bool Success, string Output) Migrate(
            Connection source,
            Connection target,
            string pipelineName = null,
            List<Behavior> behaviors = null,
            string fieldMapping = "same_name",
            Dictionary<string, string> explicitMappings = null,
            int? timeout = null)
        {
            // Validate connections
            var errors = source.Validate();
            errors.AddRange(target.Validate());
            if (errors.Count > 0)
                throw new DittoeConfigException(
                    "Connection validation failed:\n" +
                    string.Join("\n", errors.Select(e => $"  - {e}")));

            // Build pipeline name
            if (string.IsNullOrEmpty(pipelineName))
                pipelineName = $"{source.Name}-to-{target.Name}";

            // Build resources
            var srcResource = new Resource
            {
                ResourceID = 1,
                ResourceName = source.Name,
                Server = source.Server,
                Strategy = source.Strategy,
                ResourceProperties = new Dictionary<string, string>(source.ResourceProperties),
                Fields = new List<Field>(source.Fields),
            };

            var tgtResource = new Resource
            {
                ResourceID = 2,
                ResourceName = target.Name,
                Server = target.Server,
                Strategy = target.Strategy,
                ResourceProperties = new Dictionary<string, string>(target.ResourceProperties),
                Fields = new List<Field>(target.Fields),
            };

            // Build field mappings
            var mappings = BuildFieldMappings(source.Fields, target.Fields, fieldMapping, explicitMappings);

            // Build behaviors
            var behaviorList = behaviors ?? DefaultBehaviors();

            // Resolve template type
            var templateType = ResolveTemplateType(source.Server.Type, target.Server.Type);

            // Assemble pipeline
            var pipeline = new PipelineMetadata
            {
                Id = pipelineName,
                ShortName = pipelineName,
                Description = $"Migration: {source.Name} -> {target.Name}",
                SourceResource = srcResource,
                TargetResource = tgtResource,
                TemplateType = templateType,
                FieldMappings = mappings,
                Behaviors = behaviorList,
            };

            // Execute
            var baseDir = _config.EnsureBaseDir();
            return _executor.Run(pipeline, baseDir, timeout ?? _config.Timeout);
        }

        /// <summary>
        /// Create default pipeline behaviors matching engine expectations.
        /// </summary>
        public static List<Behavior> DefaultBehaviors()
        {
            return new List<Behavior>
            {
                new BehaviorBuilder("source-retrieval").SourceRetrieval().ErrorThreshold(1).Build(),
                new BehaviorBuilder("target-insert-update").TargetInsertUpdate().ErrorThreshold(1).Build(),
                new BehaviorBuilder("instrumentation").Instrumentation().Build(),
                new BehaviorBuilder("transformation").Transformation().Build(),
            };
        }

        public static List<SourceToTargetMapping> BuildFieldMappings(
            List<Field> sourceFields, List<Field> targetFields,
            string mode, Dictionary<string, string> explicitMap)
        {
            var mappings = new List<SourceToTargetMapping>();

            if (mode == "explicit" && explicitMap != null)
            {
                var srcByName = sourceFields.ToDictionary(f => f.Name);
                var tgtByName = targetFields.ToDictionary(f => f.Name);
                foreach (var kvp in explicitMap)
                {
                    if (srcByName.TryGetValue(kvp.Key, out var sf) &&
                        tgtByName.TryGetValue(kvp.Value, out var tf))
                    {
                        mappings.Add(new SourceToTargetMapping { SourceField = sf, TargetField = tf });
                    }
                }
                return mappings;
            }

            // Default: same name mapping
            var tgtDict = targetFields.ToDictionary(f => f.Name);
            foreach (var sf in sourceFields)
            {
                if (tgtDict.TryGetValue(sf.Name, out var tf))
                    mappings.Add(new SourceToTargetMapping { SourceField = sf, TargetField = tf });
            }
            return mappings;
        }

        private static TemplateType ResolveTemplateType(ServerType source, ServerType target)
        {
            var key = $"{source}To{target}";
            if (Enum.TryParse<TemplateType>(key, out var tt)) return tt;
            return TemplateType.DatabaseToDatabase;
        }
    }
}
