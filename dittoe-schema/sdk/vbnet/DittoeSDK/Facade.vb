' DITTOE SDK for VB.NET - Facade
' High-level API for data migration. One function, any server pair.

Imports System
Imports System.Collections.Generic
Imports System.Linq

Namespace DittoeSDK

    ' ========================================================================
    ' Connection
    ' ========================================================================

    Public Class Connection
        Public Property Name As String = ""
        Public Property Server As Server = New Server()
        Public Property Fields As New List(Of Field)
        Public Property Strategy As String = ""
        Public Property SchemaName As String = ""
        Public Property ResourceProperties As New Dictionary(Of String, String)
        Friend Property DbType As String = ""

        Public Function Validate() As List(Of String)
            Dim errors As New List(Of String)
            If String.IsNullOrEmpty(Name) Then errors.Add("Connection name is required")
            If Server Is Nothing OrElse String.IsNullOrEmpty(Server.Name) Then
                errors.Add($"Connection '{Name}': server is required")
            End If
            If Fields.Count = 0 Then errors.Add($"Connection '{Name}': at least one field is required")
            If String.IsNullOrEmpty(Strategy) Then errors.Add($"Connection '{Name}': strategy is required")
            Return errors
        End Function
    End Class

    ' ========================================================================
    ' ConnectionBuilder
    ' ========================================================================

    Public Class ConnectionBuilder
        Private ReadOnly _name As String
        Private ReadOnly _serverBuilder As ServerBuilder
        Private ReadOnly _fields As New List(Of Field)
        Private _strategy As String = ""
        Private _schema As String = ""
        Private ReadOnly _properties As New Dictionary(Of String, String)
        Private _isSource As Boolean? = Nothing
        Private _dbType As String = ""
        Private _serverBuilt As Boolean = False

        Public Sub New(resourceName As String)
            _name = resourceName
            _serverBuilder = New ServerBuilder(resourceName)
        End Sub

        ' ---- Server type methods ----

        Public Function PostgreSQL(host As String, port As Integer, db As String, user As String, pass As String) As ConnectionBuilder
            _serverBuilder.PostgreSQL(host, port, db, user, pass)
            _dbType = "PostgreSQLServer"
            _serverBuilt = True
            Return Me
        End Function

        Public Function MySQL(host As String, port As Integer, db As String, user As String, pass As String) As ConnectionBuilder
            _serverBuilder.MySQL(host, port, db, user, pass)
            _dbType = "MySQLServer"
            _serverBuilt = True
            Return Me
        End Function

        Public Function MSSQL(host As String, port As Integer, db As String, user As String, pass As String) As ConnectionBuilder
            _serverBuilder.MSSQL(host, port, db, user, pass)
            _dbType = "MSSQLServer"
            _serverBuilt = True
            Return Me
        End Function

        Public Function SQLite(path As String) As ConnectionBuilder
            _serverBuilder.SQLite(path)
            _dbType = "SQLiteServer"
            _serverBuilt = True
            Return Me
        End Function

        Public Function LocalFile(path As String) As ConnectionBuilder
            _serverBuilder.LocalFile(path)
            _dbType = "LocalFileServer"
            _serverBuilt = True
            Return Me
        End Function

        Public Function S3(bucket As String, region As String, ak As String, sk As String) As ConnectionBuilder
            _serverBuilder.S3(bucket, region, ak, sk)
            _dbType = "S3Server"
            _serverBuilt = True
            Return Me
        End Function

        ' ---- Configuration ----

        Public Function Schema(schemaName As String) As ConnectionBuilder
            _schema = schemaName
            Return Me
        End Function

        Public Function Fields(ParamArray fieldSpecs As String()) As ConnectionBuilder
            _fields.Clear()
            For Each spec In fieldSpecs
                _fields.Add(ParseField(spec))
            Next
            Return Me
        End Function

        Public Function AddField(name As String, Optional dt As DataType = DataType.String, Optional nullable As Boolean = True) As ConnectionBuilder
            _fields.Add(New Field() With {.Name = name, .DataType = dt, .IsNullable = nullable})
            Return Me
        End Function

        Public Function AsSource() As ConnectionBuilder
            _isSource = True
            If String.IsNullOrEmpty(_strategy) Then _strategy = GetDefaultReadStrategy(_dbType)
            Return Me
        End Function

        Public Function AsTarget() As ConnectionBuilder
            _isSource = False
            If String.IsNullOrEmpty(_strategy) Then _strategy = GetDefaultWriteStrategy(_dbType)
            Return Me
        End Function

        Public Function [Property](key As String, value As String) As ConnectionBuilder
            _properties(key) = value
            Return Me
        End Function

        Public Function Build() As Connection
            If Not _serverBuilt Then
                Throw New InvalidOperationException("No server type configured.")
            End If

            Dim server = _serverBuilder.Build()
            Dim strategy = _strategy
            If String.IsNullOrEmpty(strategy) Then
                strategy = If(_isSource.GetValueOrDefault(True),
                    GetDefaultReadStrategy(_dbType),
                    GetDefaultWriteStrategy(_dbType))
            End If

            Dim props As New Dictionary(Of String, String)(_properties)
            If Not String.IsNullOrEmpty(_schema) Then props("schemaName") = _schema

            Return New Connection() With {
                .Name = _name,
                .Server = server,
                .Fields = New List(Of Field)(_fields),
                .Strategy = strategy,
                .SchemaName = _schema,
                .ResourceProperties = props,
                .DbType = _dbType
            }
        End Function

        ' ---- Field parsing ----

        Public Shared Function ParseField(spec As String) As Field
            Dim parts = spec.Split(":"c, 2)
            Dim name = parts(0).Trim()
            Dim typeName = If(parts.Length > 1, parts(1).Trim().ToLower(), "string")
            Return New Field() With {
                .Name = name,
                .DataType = ResolveDataType(typeName)
            }
        End Function

        Private Shared Function ResolveDataType(name As String) As DataType
            Select Case name
                Case "string", "str" : Return DataType.String
                Case "integer", "int" : Return DataType.Integer
                Case "double", "float" : Return DataType.Double
                Case "decimal" : Return DataType.Decimal
                Case "boolean", "bool" : Return DataType.Boolean
                Case "date" : Return DataType.Date
                Case "datetime" : Return DataType.DateTime
                Case "uuid" : Return DataType.UUID
                Case Else : Return DataType.String
            End Select
        End Function

        Private Shared Function GetDefaultReadStrategy(dbType As String) As String
            Select Case dbType
                Case "PostgreSQLServer", "MySQLServer", "MSSQLServer", "SQLiteServer", "SnowflakeServer"
                    Return ReadStrategy.JdbcSelect
                Case "LocalFileServer" : Return ReadStrategy.LocalCsv
                Case "S3Server" : Return ReadStrategy.S3Csv
                Case "GCSServer" : Return ReadStrategy.GcsCsv
                Case "BigQueryServer" : Return ReadStrategy.BigQueryQuery
                Case "SalesforceServer" : Return ReadStrategy.Salesforce
                Case Else : Return ""
            End Select
        End Function

        Private Shared Function GetDefaultWriteStrategy(dbType As String) As String
            Select Case dbType
                Case "PostgreSQLServer", "MySQLServer", "MSSQLServer", "SQLiteServer", "SnowflakeServer"
                    Return WriteStrategy.JdbcInsert
                Case "LocalFileServer" : Return WriteStrategy.LocalCsv
                Case "S3Server" : Return WriteStrategy.S3Csv
                Case "GCSServer" : Return WriteStrategy.GcsCsv
                Case "BigQueryServer" : Return WriteStrategy.BigQuery
                Case "SalesforceServer" : Return WriteStrategy.Salesforce
                Case Else : Return ""
            End Select
        End Function
    End Class

    ' ========================================================================
    ' Dittoe Facade
    ' ========================================================================

    Public Class Dittoe
        Private ReadOnly _config As DittoeConfig
        Private ReadOnly _executor As DittoeExecutor

        Public Sub New(config As DittoeConfig)
            _config = config
            _executor = New DittoeExecutor(config.JarPath)
        End Sub

        Public Function Migrate(source As Connection, target As Connection,
                                Optional pipelineName As String = Nothing,
                                Optional behaviors As List(Of Behavior) = Nothing,
                                Optional fieldMapping As String = "same_name",
                                Optional explicitMappings As Dictionary(Of String, String) = Nothing,
                                Optional timeout As Integer? = Nothing) As (Success As Boolean, Output As String)

            ' Validate connections
            Dim errors = source.Validate()
            errors.AddRange(target.Validate())
            If errors.Count > 0 Then
                Throw New DittoeConfigException(
                    "Connection validation failed:" & Environment.NewLine &
                    String.Join(Environment.NewLine, errors.Select(Function(e) $"  - {e}")))
            End If

            If String.IsNullOrEmpty(pipelineName) Then
                pipelineName = $"{source.Name}-to-{target.Name}"
            End If

            Dim srcResource As New Resource() With {
                .ResourceID = 1,
                .ResourceName = source.Name,
                .Server = source.Server,
                .Strategy = source.Strategy,
                .ResourceProperties = New Dictionary(Of String, String)(source.ResourceProperties),
                .Fields = New List(Of Field)(source.Fields)
            }

            Dim tgtResource As New Resource() With {
                .ResourceID = 2,
                .ResourceName = target.Name,
                .Server = target.Server,
                .Strategy = target.Strategy,
                .ResourceProperties = New Dictionary(Of String, String)(target.ResourceProperties),
                .Fields = New List(Of Field)(target.Fields)
            }

            Dim mappings = BuildFieldMappings(source.Fields, target.Fields, fieldMapping, explicitMappings)
            Dim behaviorList = If(behaviors, DefaultBehaviors())

            Dim pipeline As New PipelineMetadata() With {
                .Id = pipelineName,
                .ShortName = pipelineName,
                .Description = $"Migration: {source.Name} -> {target.Name}",
                .SourceResource = srcResource,
                .TargetResource = tgtResource,
                .FieldMappings = mappings,
                .Behaviors = behaviorList
            }

            Dim baseDir = _config.EnsureBaseDir()
            Return _executor.Run(pipeline, baseDir, If(timeout, _config.Timeout))
        End Function

        Public Shared Function DefaultBehaviors() As List(Of Behavior)
            Return New List(Of Behavior) From {
                New BehaviorBuilder("source-retrieval").SourceRetrieval().Build(),
                New BehaviorBuilder("target-insert-update").TargetInsertUpdate().Build(),
                New BehaviorBuilder("formatting").Formatting().Build()
            }
        End Function

        Friend Shared Function BuildFieldMappings(
                sourceFields As List(Of Field), targetFields As List(Of Field),
                mode As String, explicitMap As Dictionary(Of String, String)) As List(Of SourceToTargetMapping)

            Dim mappings As New List(Of SourceToTargetMapping)

            If mode = "explicit" AndAlso explicitMap IsNot Nothing Then
                Dim srcByName = sourceFields.ToDictionary(Function(f) f.Name)
                Dim tgtByName = targetFields.ToDictionary(Function(f) f.Name)
                For Each kvp In explicitMap
                    Dim sf As Field = Nothing, tf As Field = Nothing
                    If srcByName.TryGetValue(kvp.Key, sf) AndAlso tgtByName.TryGetValue(kvp.Value, tf) Then
                        mappings.Add(New SourceToTargetMapping() With {.SourceField = sf, .TargetField = tf})
                    End If
                Next
                Return mappings
            End If

            ' Default: same name
            Dim tgtDict = targetFields.ToDictionary(Function(f) f.Name)
            For Each sf In sourceFields
                Dim tf As Field = Nothing
                If tgtDict.TryGetValue(sf.Name, tf) Then
                    mappings.Add(New SourceToTargetMapping() With {.SourceField = sf, .TargetField = tf})
                End If
            Next
            Return mappings
        End Function
    End Class

End Namespace
