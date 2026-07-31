Imports System
Imports System.Collections.Generic
Imports DittoeSDK

Module Program
    Private _passed As Integer = 0
    Private _total As Integer = 0

    Sub Main()
        Run("enum completeness", AddressOf TestEnumCompleteness)
        Run("strategy constants", AddressOf TestStrategyConstants)
        Run("server and resource builders", AddressOf TestServerAndResourceBuilders)
        Run("pipeline builder", AddressOf TestPipelineBuilder)
        Run("config validation", AddressOf TestConfigValidation)

        Console.WriteLine($"=== Results: {_passed}/{_total} tests passed ===")
        If _passed <> _total Then
            Environment.ExitCode = 1
        End If
    End Sub

    Private Sub Run(name As String, test As Action)
        _total += 1
        Try
            test()
            _passed += 1
            Console.WriteLine($"PASS {name}")
        Catch ex As Exception
            Console.Error.WriteLine($"FAIL {name}: {ex.Message}")
        End Try
    End Sub

    Private Sub TestEnumCompleteness()
        AssertEqual(17, [Enum].GetValues(GetType(DataType)).Length, "DataType count")
        AssertEqual(22, [Enum].GetValues(GetType(BehaviorType)).Length, "BehaviorType count")
        AssertEqual(16, [Enum].GetValues(GetType(TemplateType)).Length, "TemplateType count")
        AssertEqual(6, [Enum].GetValues(GetType(StageType)).Length, "StageType count")
    End Sub

    Private Sub TestStrategyConstants()
        AssertContains(ReadStrategy.JdbcSelect, "SelectStrategy", "JDBC read strategy")
        AssertContains(WriteStrategy.JdbcInsert, "InsertStrategy", "JDBC write strategy")
        AssertContains(ReadStrategy.Salesforce, "salesforce.readstrategy.RestAPI", "Salesforce read strategy")
        AssertContains(WriteStrategy.Salesforce, "salesforce.writestrategy.RestAPI", "Salesforce write strategy")
        AssertFalse(ReadStrategy.Salesforce.Contains("SalesforceReadStrategy"), "Salesforce read must be concrete")
        AssertFalse(WriteStrategy.Salesforce.Contains("SalesforceWriteStrategy"), "Salesforce write must be concrete")
    End Sub

    Private Sub TestServerAndResourceBuilders()
        Dim server = New ServerBuilder("pg").PostgreSQL("localhost", 5432, "db", "user", "pass").Build()
        AssertEqual(ServerType.Database, server.Type, "server type")
        AssertEqual("PostgreSQLServer", server.ConnectionDetails("databaseType"), "database type")

        Dim resource = New ResourceBuilder("products") _
            .SetServer(server) _
            .AddField("id", DataType.Integer, False) _
            .JdbcRead() _
            .Build()
        AssertEqual("products", resource.ResourceName, "resource name")
        AssertEqual(1, resource.Fields.Count, "resource field count")
        AssertEqual(ReadStrategy.JdbcSelect, resource.Strategy, "resource strategy")
    End Sub

    Private Sub TestPipelineBuilder()
        Dim server = New ServerBuilder("pg").PostgreSQL("localhost", 5432, "db", "user", "pass").Build()
        Dim source = New ResourceBuilder("products").SetServer(server).AddField("id", DataType.Integer, False).JdbcRead().Build()
        Dim target = New ResourceBuilder("products").SetServer(server).AddField("id", DataType.Integer, False).Insert().Build()

        Dim pipeline = New PipelineBuilder("ProductMigration") _
            .DatabaseToDatabase() _
            .Source(source) _
            .Target(target) _
            .MapFieldsSameName() _
            .WithDefaultBehaviors() _
            .Build()

        AssertEqual("ProductMigration", pipeline.ShortName, "pipeline short name")
        AssertEqual(TemplateType.DatabaseToDatabase, pipeline.TemplateType, "pipeline template")
        AssertEqual(1, pipeline.FieldMappings.Count, "field mapping count")
        AssertEqual(4, pipeline.Behaviors.Count, "default behavior count")
    End Sub

    Private Sub TestConfigValidation()
        Dim config = New DittoeConfig()
        Try
            config.Validate()
            Throw New Exception("Expected config validation to fail")
        Catch ex As DittoeConfigException
            AssertContains(ex.Message, "JarPath is required", "missing JAR validation")
        End Try
    End Sub

    Private Sub AssertEqual(expected As Object, actual As Object, label As String)
        If Not Object.Equals(expected, actual) Then
            Throw New Exception($"{label}: expected {expected}, got {actual}")
        End If
    End Sub

    Private Sub AssertContains(value As String, fragment As String, label As String)
        If value Is Nothing OrElse Not value.Contains(fragment) Then
            Throw New Exception($"{label}: expected to contain {fragment}")
        End If
    End Sub

    Private Sub AssertFalse(condition As Boolean, label As String)
        If condition Then
            Throw New Exception(label)
        End If
    End Sub
End Module
