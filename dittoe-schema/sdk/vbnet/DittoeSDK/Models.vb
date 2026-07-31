' DITTOE SDK for VB.NET - Models
' All enums, models, and strategy constants for Pipeline Metadata Documents.
' Copyright (c) 2026 EveryArrow. Licensed under Apache 2.0.

Imports System
Imports System.Collections.Generic
Imports System.IO
Imports System.Text.Json

Namespace DittoeSDK

    ' ========================================================================
    ' Enums (9)
    ' ========================================================================

    Public Enum DataType
        [String]
        [Integer]
        [Double]
        [Decimal]
        [Boolean]
        [Date]
        DateTime
        Time
        UUID
        TimeWithTimeZone
        DateTimeWithTimeZone
        JSON
        JSONB
        Array
        Number
        Bytes
        XML
    End Enum

    Public Enum BehaviorType
        Formatting
        SourceRetrieval
        TargetInsertUpdate
        Transformation
        Encryption
        Logging
        Instrumentation
        lineageTracking
        KeyGeneration
        Masking
        Deduplication
        SchemaEvolution
        Retry
        SchemaValidation
        DQRuleChecks
        Archive
        MicroBatch
        DataScienceAlgorithms
        CustomCode
        Compression
        ExternalWidget
        Cache
    End Enum

    Public Enum ServerType
        Stream
        API
        File
        Database
    End Enum

    Public Enum TemplateType
        FileToFile
        FileToDatabase
        FileToStream
        FileToAPI
        DatabaseToDatabase
        DatabaseToFile
        DatabaseToStream
        DatabaseToAPI
        StreamToStream
        StreamToFile
        StreamToDatabase
        StreamToAPI
        APIToAPI
        APIToFile
        APIToDatabase
        APIToStream
    End Enum

    Public Enum StageType
        [Begin]
        Acquire
        Process
        Package
        Transmit
        [End]
    End Enum

    Public Enum ProcessExecutionType
        SYNC
        ASYNC
        QUEUED
        JOINED
    End Enum

    Public Enum DatabaseType
        PostgreSQLServer
        MySQLServer
        MSSQLServer
        SQLiteServer
        SnowflakeServer
    End Enum

    Public Enum FileType
        LocalFileServer
        S3Server
        GCSServer
        AzureServer
        DropboxServer
        SFTPServer
        FTPServer
        IBMServer
        OracleServer
        DigitalOceanServer
        GoogleDriveServer
    End Enum

    Public Enum APIType
        BigQueryServer
        SalesforceServer
    End Enum

    ' ========================================================================
    ' Models
    ' ========================================================================

    Public Class Field
        Public Property Name As String = ""
        Public Property DataType As DataType = DataType.String
        Public Property IsNullable As Boolean = True
        Public Property Description As String = ""
        Public Property Expression As String = ""

        Public Function ToDict() As Dictionary(Of String, Object)
            Dim d As New Dictionary(Of String, Object) From {
                {"name", Name},
                {"dataType", DataType.ToString()},
                {"isNullable", IsNullable}
            }
            If Not String.IsNullOrEmpty(Description) Then d("description") = Description
            If Not String.IsNullOrEmpty(Expression) Then d("expression") = Expression
            Return d
        End Function
    End Class

    Public Class Server
        Public Property Id As Integer = 0
        Public Property Name As String = ""
        Public Property Type As ServerType = ServerType.Database
        Public Property ConnectionDetails As New Dictionary(Of String, String)
        Public Property AdditionalProperties As New Dictionary(Of String, String)

        Public Function ToDict() As Dictionary(Of String, Object)
            Return New Dictionary(Of String, Object) From {
                {"id", Id},
                {"name", Name},
                {"type", Type.ToString()},
                {"connectionDetails", ConnectionDetails},
                {"additionalProperties", AdditionalProperties}
            }
        End Function
    End Class

    Public Class Resource
        Public Property ResourceID As Integer = 1
        Public Property ResourceName As String = ""
        Public Property Server As Server = New Server()
        Public Property Strategy As String = ""
        Public Property ResourceProperties As New Dictionary(Of String, String)
        Public Property Fields As New List(Of Field)

        Public Function ToDict() As Dictionary(Of String, Object)
            Dim fieldDicts As New List(Of Dictionary(Of String, Object))
            For Each f In Fields
                fieldDicts.Add(f.ToDict())
            Next
            Return New Dictionary(Of String, Object) From {
                {"resourceID", ResourceID},
                {"resourceName", ResourceName},
                {"server", If(Server IsNot Nothing, Server.ToDict(), New Dictionary(Of String, Object))},
                {"strategy", Strategy},
                {"resourceProperties", ResourceProperties},
                {"fields", fieldDicts}
            }
        End Function
    End Class

    Public Class DQRule
        Public Property RuleName As String = ""
        Public Property RuleExpression As String = ""

        Public Function ToDict() As Dictionary(Of String, Object)
            Return New Dictionary(Of String, Object) From {
                {"ruleName", RuleName},
                {"ruleExpression", RuleExpression}
            }
        End Function
    End Class

    Public Class Behavior
        Public Property Name As String = ""
        Public Property BehaviorType As BehaviorType = BehaviorType.SourceRetrieval
        Public Property Properties As New Dictionary(Of String, String)
        Public Property Description As String = ""
        Public Property IsStaged As Boolean = False
        Public Property StageType As StageType = StageType.Process
        Public Property Order As Integer = 0
        Public Property PluginReference As String = ""

        Public Function ToDict() As Dictionary(Of String, Object)
            Dim d As New Dictionary(Of String, Object) From {
                {"name", Name},
                {"behaviorType", BehaviorType.ToString()},
                {"properties", Properties},
                {"description", Description}
            }
            If IsStaged Then
                d("stageType") = StageType.ToString()
                d("order") = Order
                d("pluginReference") = PluginReference
            End If
            Return d
        End Function
    End Class

    Public Class SourceToTargetMapping
        Public Property SourceField As Field = New Field()
        Public Property TargetField As Field = New Field()

        Public Function ToDict() As Dictionary(Of String, Object)
            Return New Dictionary(Of String, Object) From {
                {"sourceField", SourceField.ToDict()},
                {"targetField", TargetField.ToDict()}
            }
        End Function
    End Class

    Public Class PipelineMetadata
        Public Property Id As String = ""
        Public Property ShortName As String = ""
        Public Property Description As String = ""
        Public Property GlobalProperties As New Dictionary(Of String, String)
        Public Property Behaviors As New List(Of Behavior)
        Public Property CreatedBy As String = ""
        Public Property SourceResource As Resource = New Resource()
        Public Property TargetResource As Resource = New Resource()
        Public Property TemplateType As TemplateType = TemplateType.DatabaseToDatabase
        Public Property FieldMappings As New List(Of SourceToTargetMapping)

        Public Function ToDict() As Dictionary(Of String, Object)
            Dim behaviorDicts As New List(Of Dictionary(Of String, Object))
            For Each b In Behaviors
                behaviorDicts.Add(b.ToDict())
            Next
            Dim mappingDicts As New List(Of Dictionary(Of String, Object))
            For Each m In FieldMappings
                mappingDicts.Add(m.ToDict())
            Next
            Return New Dictionary(Of String, Object) From {
                {"id", Id},
                {"shortName", ShortName},
                {"description", Description},
                {"globalProperties", GlobalProperties},
                {"behaviors", behaviorDicts},
                {"sourceResourceToProcess", SourceResource.ToDict()},
                {"targetResourceToProcess", TargetResource.ToDict()},
                {"templateType", TemplateType.ToString()},
                {"S2TMap", mappingDicts}
            }
        End Function

        Public Function ToJson(Optional indent As Boolean = True) As String
            Dim options As New JsonSerializerOptions With {.WriteIndented = indent}
            Return JsonSerializer.Serialize(ToDict(), options)
        End Function

        Public Sub Save(path As String)
            Dim dir = IO.Path.GetDirectoryName(path)
            If Not String.IsNullOrEmpty(dir) Then IO.Directory.CreateDirectory(dir)
            IO.File.WriteAllText(path, ToJson())
        End Sub
    End Class

    ' ========================================================================
    ' Strategy Constants
    ' ========================================================================

    Public Class ReadStrategy
        ' Database
        Public Const JdbcSelect As String = "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy"
        ' Local file
        Public Const LocalCsv As String = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.CSVReadStrategy"
        Public Const LocalJson As String = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.JSONReadStrategy"
        ' Cloud file
        Public Const S3Csv As String = "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.CSVReadStrategy"
        Public Const GcsCsv As String = "com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy.CSVReadStrategy"
        Public Const AzureCsv As String = "com.everyarrow.dittoe.io.file.servers.azure.reader.strategy.CSVReadStrategy"
        Public Const DropboxCsv As String = "com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy.CSVReadStrategy"
        Public Const SftpCsv As String = "com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy.CSVReadStrategy"
        Public Const FtpCsv As String = "com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy.CSVReadStrategy"
        Public Const IbmCsv As String = "com.everyarrow.dittoe.io.file.servers.ibm.reader.strategy.CSVReadStrategy"
        Public Const OracleCsv As String = "com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy.CSVReadStrategy"
        Public Const DigitalOceanCsv As String = "com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy.CSVReadStrategy"
        Public Const GoogleDriveCsv As String = "com.everyarrow.dittoe.io.file.servers.googledrive.reader.strategy.CSVReadStrategy"
        ' API
        Public Const BigQueryQuery As String = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.QueryStrategy"
        Public Const BigQueryAvro As String = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.AvroStrategy"
        Public Const Salesforce As String = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI"
    End Class

    Public Class WriteStrategy
        ' Database - JDBC
        Public Const JdbcInsert As String = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy"
        Public Const JdbcReplace As String = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.ReplaceStrategy"
        ' PostgreSQL
        Public Const PgUpsert As String = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy"
        Public Const PgInsertDoNothing As String = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertDoNothingOnConflictStrategy"
        Public Const PgCopy As String = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy"
        Public Const PgCopyOrAutoId As String = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyOrAutoIdStrategy"
        Public Const PgInsertOrUpdate As String = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertOrUpdateStrategy"
        ' MSSQL
        Public Const MssqlMerge As String = "com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy"
        ' SQLite
        Public Const SqliteUpsert As String = "com.everyarrow.dittoe.io.database.servers.sqlite.writer.strategy.UpsertOnConflictStrategy"
        ' Local file
        Public Const LocalCsv As String = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.CSVWriteStrategy"
        Public Const LocalJson As String = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.JSONWriteStrategy"
        ' Cloud file
        Public Const S3Csv As String = "com.everyarrow.dittoe.io.file.servers.s3.writer.strategy.CSVWriteStrategy"
        Public Const GcsCsv As String = "com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy.CSVWriteStrategy"
        Public Const AzureCsv As String = "com.everyarrow.dittoe.io.file.servers.azure.writer.strategy.CSVWriteStrategy"
        Public Const DropboxCsv As String = "com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy.CSVWriteStrategy"
        Public Const SftpCsv As String = "com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy.CSVWriteStrategy"
        Public Const FtpCsv As String = "com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy.CSVWriteStrategy"
        Public Const IbmCsv As String = "com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy.CSVWriteStrategy"
        Public Const OracleCsv As String = "com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy.CSVWriteStrategy"
        Public Const DigitalOceanCsv As String = "com.everyarrow.dittoe.io.file.servers.digitalocean.writer.strategy.CSVWriteStrategy"
        Public Const GoogleDriveCsv As String = "com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy.CSVWriteStrategy"
        ' API
        Public Const BigQuery As String = "com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.DataStoreStrategy"
        Public Const Salesforce As String = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI"
    End Class

End Namespace
