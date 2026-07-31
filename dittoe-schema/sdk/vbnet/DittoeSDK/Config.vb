' DITTOE SDK for VB.NET - Configuration
' Centralizes runtime settings with environment variable support and validation.

Imports System
Imports System.Collections.Generic
Imports System.IO

Namespace DittoeSDK

    ''' <summary>
    ''' Exception thrown when DITTOE configuration validation fails.
    ''' </summary>
    Public Class DittoeConfigException
        Inherits Exception

        Public Sub New(message As String)
            MyBase.New(message)
        End Sub
    End Class

    ''' <summary>
    ''' Central configuration for DITTOE SDK.
    ''' 
    ''' Environment variables:
    '''   DITTOE_JAR      - Path to DITTOE JAR (required)
    '''   DITTOE_BASE_DIR - Base directory (default: ./pipelines)
    '''   DITTOE_JAVA     - Java executable (default: java)
    '''   DITTOE_JAVA_OPTS - Comma-separated JVM options
    '''   DITTOE_TIMEOUT  - Timeout in seconds (default: 300)
    ''' </summary>
    Public Class DittoeConfig
        Public Property JarPath As String = ""
        Public Property BaseDir As String = "./pipelines"
        Public Property JavaPath As String = "java"
        Public Property JavaOpts As New List(Of String)
        Public Property Timeout As Integer = 300

        Public Shared Function FromEnv() As DittoeConfig
            Dim javaOptsStr = Environment.GetEnvironmentVariable("DITTOE_JAVA_OPTS")
            Dim javaOpts As New List(Of String)
            If Not String.IsNullOrEmpty(javaOptsStr) Then
                For Each opt In javaOptsStr.Split(","c)
                    Dim trimmed = opt.Trim()
                    If Not String.IsNullOrEmpty(trimmed) Then javaOpts.Add(trimmed)
                Next
            End If

            Dim timeoutStr = If(Environment.GetEnvironmentVariable("DITTOE_TIMEOUT"), "300")
            Dim timeoutVal As Integer
            If Not Integer.TryParse(timeoutStr, timeoutVal) Then timeoutVal = 300

            Return New DittoeConfig() With {
                .JarPath = If(Environment.GetEnvironmentVariable("DITTOE_JAR"), ""),
                .BaseDir = If(Environment.GetEnvironmentVariable("DITTOE_BASE_DIR"), "./pipelines"),
                .JavaPath = If(Environment.GetEnvironmentVariable("DITTOE_JAVA"), "java"),
                .JavaOpts = javaOpts,
                .Timeout = timeoutVal
            }
        End Function

        Public Function Validate() As List(Of String)
            Dim errors As New List(Of String)

            If String.IsNullOrEmpty(JarPath) Then
                errors.Add("JarPath is required (set DITTOE_JAR)")
            ElseIf Not File.Exists(JarPath) Then
                errors.Add($"JAR file not found: {JarPath}")
            End If

            If String.IsNullOrEmpty(JavaPath) Then errors.Add("JavaPath is required")
            If String.IsNullOrEmpty(BaseDir) Then errors.Add("BaseDir is required")
            If Timeout <= 0 Then errors.Add($"Timeout must be positive, got: {Timeout}")

            If errors.Count > 0 Then
                Throw New DittoeConfigException(
                    "Configuration validation failed:" & Environment.NewLine &
                    String.Join(Environment.NewLine, errors.ConvertAll(Function(e) $"  - {e}")))
            End If

            Return errors
        End Function

        Public Function EnsureBaseDir() As String
            Dim absPath = Path.GetFullPath(BaseDir)
            Directory.CreateDirectory(absPath)
            Return absPath
        End Function
    End Class

End Namespace
