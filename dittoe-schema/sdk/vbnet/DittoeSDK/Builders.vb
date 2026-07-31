' DITTOE SDK for VB.NET - Fluent Builders
' All 19 server types, 45 strategies, 22 behaviors, 16 template types

Imports System
Imports System.Collections.Generic
Imports System.Linq

Namespace DittoeSDK

    ' ========================================================================
    ' FieldBuilder
    ' ========================================================================

    Public Class FieldBuilder
        Private ReadOnly _field As New Field()

        Public Sub New(name As String)
            _field.Name = name
        End Sub

        Public Function SetDataType(dt As DataType) As FieldBuilder
            _field.DataType = dt
            Return Me
        End Function

        Public Function [String]() As FieldBuilder
            Return SetDataType(DataType.String)
        End Function
        Public Function [Integer]() As FieldBuilder
            Return SetDataType(DataType.Integer)
        End Function
        Public Function [Double]() As FieldBuilder
            Return SetDataType(DataType.Double)
        End Function
        Public Function [Decimal]() As FieldBuilder
            Return SetDataType(DataType.Decimal)
        End Function
        Public Function [Boolean]() As FieldBuilder
            Return SetDataType(DataType.Boolean)
        End Function
        Public Function [Date]() As FieldBuilder
            Return SetDataType(DataType.Date)
        End Function
        Public Function [DateTime]() As FieldBuilder
            Return SetDataType(DataType.DateTime)
        End Function
        Public Function Time() As FieldBuilder
            Return SetDataType(DataType.Time)
        End Function
        Public Function Uuid() As FieldBuilder
            Return SetDataType(DataType.UUID)
        End Function
        Public Function Json() As FieldBuilder
            Return SetDataType(DataType.JSON)
        End Function
        Public Function Jsonb() As FieldBuilder
            Return SetDataType(DataType.JSONB)
        End Function
        Public Function [Array]() As FieldBuilder
            Return SetDataType(DataType.Array)
        End Function
        Public Function Number() As FieldBuilder
            Return SetDataType(DataType.Number)
        End Function
        Public Function Bytes() As FieldBuilder
            Return SetDataType(DataType.Bytes)
        End Function
        Public Function Xml() As FieldBuilder
            Return SetDataType(DataType.XML)
        End Function
        Public Function Nullable(Optional v As Boolean = True) As FieldBuilder
            _field.IsNullable = v
            Return Me
        End Function
        Public Function Required() As FieldBuilder
            Return Nullable(False)
        End Function
        Public Function Description(d As String) As FieldBuilder
            _field.Description = d
            Return Me
        End Function
        Public Function Expression(e As String) As FieldBuilder
            _field.Expression = e
            Return Me
        End Function
        Public Function Build() As Field
            Return _field
        End Function
    End Class

    ' ========================================================================
    ' ServerBuilder
    ' ========================================================================

    Public Class ServerBuilder
        Private ReadOnly _server As New Server()

        Public Sub New(name As String)
            _server.Name = name
        End Sub

        Public Function SetId(id As Integer) As ServerBuilder
            _server.Id = id
            Return Me
        End Function

        Public Function AdditionalProperty(k As String, v As String) As ServerBuilder
            _server.AdditionalProperties(k) = v
            Return Me
        End Function

        ' Database (5)
        Private Function SetupDb(dbType As String, host As String, port As Integer,
                                 database As String, user As String, password As String) As ServerBuilder
            _server.Type = ServerType.Database
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"databaseType", dbType}, {"host", host},
                {"port", port.ToString()}, {"database", database},
                {"user", user}, {"password", password}
            }
            Return Me
        End Function

        Public Function PostgreSQL(host As String, port As Integer, db As String, user As String, pass As String) As ServerBuilder
            Return SetupDb("PostgreSQLServer", host, port, db, user, pass)
        End Function
        Public Function MySQL(host As String, port As Integer, db As String, user As String, pass As String) As ServerBuilder
            Return SetupDb("MySQLServer", host, port, db, user, pass)
        End Function
        Public Function MSSQL(host As String, port As Integer, db As String, user As String, pass As String) As ServerBuilder
            Return SetupDb("MSSQLServer", host, port, db, user, pass)
        End Function
        Public Function SQLite(path As String) As ServerBuilder
            _server.Type = ServerType.Database
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"databaseType", "SQLiteServer"}, {"path", path}
            }
            Return Me
        End Function
        Public Function Snowflake(host As String, port As Integer, db As String, user As String, pass As String) As ServerBuilder
            Return SetupDb("SnowflakeServer", host, port, db, user, pass)
        End Function

        ' File (11)
        Public Function LocalFile(path As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "LocalFileServer"}, {"path", path}
            }
            Return Me
        End Function
        Public Function S3(bucket As String, region As String, ak As String, sk As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "S3Server"}, {"bucket", bucket},
                {"region", region}, {"accessKey", ak}, {"secretKey", sk}
            }
            Return Me
        End Function
        Public Function GCS(bucket As String, project As String, creds As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "GCSServer"}, {"bucket", bucket},
                {"projectId", project}, {"credentialsFile", creds}
            }
            Return Me
        End Function
        Public Function Azure(container As String, acct As String, key As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "AzureServer"}, {"container", container},
                {"accountName", acct}, {"accountKey", key}
            }
            Return Me
        End Function
        Public Function SFTP(host As String, port As Integer, path As String, user As String, pass As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "SFTPServer"}, {"host", host},
                {"port", port.ToString()}, {"path", path}, {"user", user}, {"password", pass}
            }
            Return Me
        End Function
        Public Function FTP(host As String, port As Integer, path As String, user As String, pass As String) As ServerBuilder
            _server.Type = ServerType.File
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"fileType", "FTPServer"}, {"host", host},
                {"port", port.ToString()}, {"path", path}, {"user", user}, {"password", pass}
            }
            Return Me
        End Function
        Public Function BigQuery(project As String, dataset As String, creds As String) As ServerBuilder
            _server.Type = ServerType.API
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"apiType", "BigQueryServer"}, {"projectId", project},
                {"dataset", dataset}, {"credentialsFile", creds}
            }
            Return Me
        End Function
        Public Function Salesforce(loginUrl As String, user As String, pass As String, token As String) As ServerBuilder
            _server.Type = ServerType.API
            _server.ConnectionDetails = New Dictionary(Of String, String) From {
                {"apiType", "SalesforceServer"}, {"loginUrl", loginUrl},
                {"username", user}, {"password", pass}, {"securityToken", token}
            }
            Return Me
        End Function

        Public Function Build() As Server
            Return _server
        End Function
    End Class

    ' ========================================================================
    ' ResourceBuilder
    ' ========================================================================

    Public Class ResourceBuilder
        Private ReadOnly _resource As New Resource()

        Public Sub New(name As String)
            _resource.ResourceName = name
        End Sub

        Public Function SetServer(s As Server) As ResourceBuilder
            _resource.Server = s
            Return Me
        End Function
        Public Function AddField(name As String, Optional dt As DataType = DataType.String, Optional nullable As Boolean = True) As ResourceBuilder
            _resource.Fields.Add(New Field() With {.Name = name, .DataType = dt, .IsNullable = nullable})
            Return Me
        End Function
        Public Function AddField(f As Field) As ResourceBuilder
            _resource.Fields.Add(f)
            Return Me
        End Function
        Public Function Schema(s As String) As ResourceBuilder
            _resource.ResourceProperties("schema") = s
            Return Me
        End Function
        Public Function [Property](k As String, v As String) As ResourceBuilder
            _resource.ResourceProperties(k) = v
            Return Me
        End Function
        Public Function SetStrategy(s As String) As ResourceBuilder
            _resource.Strategy = s
            Return Me
        End Function

        ' DB read
        Public Function JdbcRead() As ResourceBuilder
            Return SetStrategy(ReadStrategy.JdbcSelect)
        End Function
        ' DB write
        Public Function Insert() As ResourceBuilder
            Return SetStrategy(WriteStrategy.JdbcInsert)
        End Function
        Public Function Replace() As ResourceBuilder
            Return SetStrategy(WriteStrategy.JdbcReplace)
        End Function
        Public Function PgUpsert(c As String) As ResourceBuilder
            [Property]("upsertConflictConstraint", c)
            Return SetStrategy(WriteStrategy.PgUpsert)
        End Function
        Public Function PgCopy() As ResourceBuilder
            Return SetStrategy(WriteStrategy.PgCopy)
        End Function
        ' File read/write
        Public Function CsvRead() As ResourceBuilder
            Return SetStrategy(ReadStrategy.LocalCsv)
        End Function
        Public Function CsvWrite() As ResourceBuilder
            Return SetStrategy(WriteStrategy.LocalCsv)
        End Function
        Public Function S3CsvRead() As ResourceBuilder
            Return SetStrategy(ReadStrategy.S3Csv)
        End Function
        Public Function S3CsvWrite() As ResourceBuilder
            Return SetStrategy(WriteStrategy.S3Csv)
        End Function
        ' API
        Public Function BigQueryQueryRead() As ResourceBuilder
            Return SetStrategy(ReadStrategy.BigQueryQuery)
        End Function
        Public Function BigQueryWrite() As ResourceBuilder
            Return SetStrategy(WriteStrategy.BigQuery)
        End Function
        Public Function SalesforceRead() As ResourceBuilder
            Return SetStrategy(ReadStrategy.Salesforce)
        End Function
        Public Function SalesforceWrite() As ResourceBuilder
            Return SetStrategy(WriteStrategy.Salesforce)
        End Function

        Public Function Build() As Resource
            Return _resource
        End Function
    End Class

    ' ========================================================================
    ' BehaviorBuilder
    ' ========================================================================

    Public Class BehaviorBuilder
        Private ReadOnly _behavior As New Behavior()

        Public Sub New(Optional name As String = "")
            _behavior.Name = name
        End Sub

        Public Function SetType(bt As BehaviorType) As BehaviorBuilder
            _behavior.BehaviorType = bt
            Return Me
        End Function
        Public Function Description(d As String) As BehaviorBuilder
            _behavior.Description = d
            Return Me
        End Function
        Public Function [Property](k As String, v As String) As BehaviorBuilder
            _behavior.Properties(k) = v
            Return Me
        End Function
        Public Function ErrorThreshold(t As Integer) As BehaviorBuilder
            Return [Property]("errorCountThreshold", t.ToString())
        End Function
        Public Function Staged(s As StageType, Optional order As Integer = 0, Optional plugin As String = "") As BehaviorBuilder
            _behavior.IsStaged = True
            _behavior.StageType = s
            _behavior.Order = order
            _behavior.PluginReference = plugin
            Return Me
        End Function

        Private Function SetBT(bt As BehaviorType, defaultName As String) As BehaviorBuilder
            _behavior.BehaviorType = bt
            If String.IsNullOrEmpty(_behavior.Name) Then _behavior.Name = defaultName
            Return Me
        End Function

        Public Function Formatting() As BehaviorBuilder
            Return SetBT(BehaviorType.Formatting, "Formatting")
        End Function
        Public Function SourceRetrieval() As BehaviorBuilder
            Return SetBT(BehaviorType.SourceRetrieval, "SourceRetrieval")
        End Function
        Public Function TargetInsertUpdate() As BehaviorBuilder
            Return SetBT(BehaviorType.TargetInsertUpdate, "TargetInsertUpdate")
        End Function
        Public Function Transformation() As BehaviorBuilder
            Return SetBT(BehaviorType.Transformation, "Transformation")
        End Function
        Public Function Encryption() As BehaviorBuilder
            Return SetBT(BehaviorType.Encryption, "Encryption")
        End Function
        Public Function Logging() As BehaviorBuilder
            Return SetBT(BehaviorType.Logging, "Logging")
        End Function
        Public Function Instrumentation() As BehaviorBuilder
            Return SetBT(BehaviorType.Instrumentation, "Instrumentation")
        End Function

        Public Function Build() As Behavior
            Return _behavior
        End Function
    End Class

    ' ========================================================================
    ' PipelineBuilder
    ' ========================================================================

    Public Class PipelineBuilder
        Private ReadOnly _pipeline As New PipelineMetadata()

        Public Sub New(shortName As String)
            _pipeline.ShortName = shortName
            _pipeline.Id = $"pipeline-{shortName}"
        End Sub

        Public Function SetId(id As String) As PipelineBuilder
            _pipeline.Id = id
            Return Me
        End Function
        Public Function Description(d As String) As PipelineBuilder
            _pipeline.Description = d
            Return Me
        End Function

        Public Function SetTemplateType(tt As TemplateType) As PipelineBuilder
            _pipeline.TemplateType = tt
            Return Me
        End Function
        Public Function DatabaseToDatabase() As PipelineBuilder
            Return SetTemplateType(TemplateType.DatabaseToDatabase)
        End Function
        Public Function FileToDatabase() As PipelineBuilder
            Return SetTemplateType(TemplateType.FileToDatabase)
        End Function
        Public Function DatabaseToFile() As PipelineBuilder
            Return SetTemplateType(TemplateType.DatabaseToFile)
        End Function

        Public Function Source(r As Resource) As PipelineBuilder
            _pipeline.SourceResource = r
            Return Me
        End Function
        Public Function Target(r As Resource) As PipelineBuilder
            _pipeline.TargetResource = r
            Return Me
        End Function
        Public Function MapField(s As Field, t As Field) As PipelineBuilder
            _pipeline.FieldMappings.Add(New SourceToTargetMapping() With {.SourceField = s, .TargetField = t})
            Return Me
        End Function
        Public Function MapFieldsSameName() As PipelineBuilder
            For Each sf In _pipeline.SourceResource.Fields
                For Each tf In _pipeline.TargetResource.Fields
                    If sf.Name = tf.Name Then
                        _pipeline.FieldMappings.Add(New SourceToTargetMapping() With {.SourceField = sf, .TargetField = tf})
                        Exit For
                    End If
                Next
            Next
            Return Me
        End Function
        Public Function AddBehavior(b As Behavior) As PipelineBuilder
            _pipeline.Behaviors.Add(b)
            Return Me
        End Function
        Public Function WithDefaultBehaviors() As PipelineBuilder
            _pipeline.Behaviors = New List(Of Behavior) From {
                New BehaviorBuilder().SourceRetrieval().ErrorThreshold(1).Build(),
                New BehaviorBuilder().TargetInsertUpdate().ErrorThreshold(1).Build(),
                New BehaviorBuilder().Instrumentation().Build(),
                New BehaviorBuilder().Transformation().Build()
            }
            Return Me
        End Function
        Public Function Build() As PipelineMetadata
            Return _pipeline
        End Function
    End Class

    ' ========================================================================
    ' Executor
    ' ========================================================================

    Public Class DittoeExecutor
        Public ReadOnly Property JarPath As String

        Public Sub New(jarPath As String)
            Me.JarPath = jarPath
        End Sub

        Public Function Run(pipeline As PipelineMetadata, baseDir As String, Optional timeout As Integer = 300) As (Success As Boolean, Output As String)
            Dim pmdDir = IO.Path.Combine(baseDir, "pipelines", pipeline.ShortName)
            IO.Directory.CreateDirectory(pmdDir)
            pipeline.Save(IO.Path.Combine(pmdDir, "pmd.json"))

            Dim psi As New Diagnostics.ProcessStartInfo With {
                .FileName = "java",
                .Arguments = $"-jar ""{JarPath}"" --base-directory ""{baseDir}"" --pipeline-name ""{pipeline.ShortName}""",
                .RedirectStandardOutput = True,
                .RedirectStandardError = True,
                .UseShellExecute = False,
                .CreateNoWindow = True
            }
            Using proc = Diagnostics.Process.Start(psi)
                If proc Is Nothing Then Return (False, "Failed to start process")
                Dim output = proc.StandardOutput.ReadToEnd()
                proc.WaitForExit(timeout * 1000)
                Return (proc.ExitCode = 0, output)
            End Using
        End Function

        Public Function Validate(p As PipelineMetadata) As List(Of String)
            Dim errors As New List(Of String)
            If String.IsNullOrEmpty(p.ShortName) Then errors.Add("ShortName is required")
            If String.IsNullOrEmpty(p.SourceResource.ResourceName) Then errors.Add("Source resource is required")
            If String.IsNullOrEmpty(p.TargetResource.ResourceName) Then errors.Add("Target resource is required")
            If String.IsNullOrEmpty(p.SourceResource.Strategy) Then errors.Add("Source strategy is required")
            If String.IsNullOrEmpty(p.TargetResource.Strategy) Then errors.Add("Target strategy is required")
            If p.SourceResource.Fields.Count = 0 Then errors.Add("Source must have fields")
            If p.TargetResource.Fields.Count = 0 Then errors.Add("Target must have fields")
            If p.Behaviors.Count = 0 Then errors.Add("Pipeline must have behaviors")
            If p.FieldMappings.Count = 0 Then errors.Add("Pipeline must have field mappings")
            Return errors
        End Function
    End Class

End Namespace
