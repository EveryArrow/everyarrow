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

#ifndef DITTOE_HPP
#define DITTOE_HPP

#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <filesystem>
#include <cstdlib>
#include <array>
#include <memory>
#include <algorithm>
#include <cctype>
#include <utility>

namespace dittoe {

// ============================================================================
// Enums
// ============================================================================

enum class DataType {
    String, Integer, Double, Decimal, Boolean,
    Date, DateTime, Time, UUID,
    TimeWithTimeZone, DateTimeWithTimeZone,
    JSON, JSONB, Array, Number, Bytes, XML
};

enum class BehaviorType {
    Formatting, SourceRetrieval, TargetInsertUpdate,
    Transformation, Encryption, Logging, Instrumentation,
    lineageTracking, KeyGeneration, Masking,
    Deduplication, SchemaEvolution, Retry,
    SchemaValidation, DQRuleChecks, Archive,
    MicroBatch, DataScienceAlgorithms, CustomCode,
    Compression, ExternalWidget, Cache
};

enum class ServerType { Stream, API, File, Database };

enum class TemplateType {
    FileToFile, FileToDatabase, FileToStream, FileToAPI,
    DatabaseToDatabase, DatabaseToFile, DatabaseToStream, DatabaseToAPI,
    StreamToStream, StreamToFile, StreamToDatabase, StreamToAPI,
    APIToAPI, APIToFile, APIToDatabase, APIToStream
};

enum class StageType { Begin, Acquire, Process, Package, Transmit, End };
enum class ProcessExecutionType { SYNC, ASYNC, QUEUED, JOINED };
enum class DatabaseSubType { PostgreSQL, MySQL, MSSQL, SQLite, Snowflake };
enum class FileSubType { Local, S3, GCS, Azure, Dropbox, SFTP, FTP, IBM, Oracle, DigitalOcean, GoogleDrive };
enum class APISubType { BigQuery, Salesforce };

// ============================================================================
// Enum to string
// ============================================================================

inline const char* to_string(DataType v) {
    static const char* s[] = {
        "String","Integer","Double","Decimal","Boolean",
        "Date","DateTime","Time","UUID",
        "TimeWithTimeZone","DateTimeWithTimeZone",
        "JSON","JSONB","Array","Number","Bytes","XML"
    };
    return s[static_cast<int>(v)];
}

inline const char* to_string(BehaviorType v) {
    static const char* s[] = {
        "Formatting","SourceRetrieval","TargetInsertUpdate",
        "Transformation","Encryption","Logging","Instrumentation",
        "lineageTracking","KeyGeneration","Masking",
        "Deduplication","SchemaEvolution","Retry",
        "SchemaValidation","DQRuleChecks","Archive",
        "MicroBatch","DataScienceAlgorithms","CustomCode",
        "Compression","ExternalWidget","Cache"
    };
    return s[static_cast<int>(v)];
}

inline const char* to_string(ServerType v) {
    static const char* s[] = {"Stream","API","File","Database"};
    return s[static_cast<int>(v)];
}

inline const char* to_string(TemplateType v) {
    static const char* s[] = {
        "FileToFile","FileToDatabase","FileToStream","FileToAPI",
        "DatabaseToDatabase","DatabaseToFile","DatabaseToStream","DatabaseToAPI",
        "StreamToStream","StreamToFile","StreamToDatabase","StreamToAPI",
        "APIToAPI","APIToFile","APIToDatabase","APIToStream"
    };
    return s[static_cast<int>(v)];
}

inline const char* to_string(StageType v) {
    static const char* s[] = {"Begin","Acquire","Process","Package","Transmit","End"};
    return s[static_cast<int>(v)];
}

inline const char* to_string(DatabaseSubType v) {
    static const char* s[] = {"PostgreSQLServer","MySQLServer","MSSQLServer","SQLiteServer","SnowflakeServer"};
    return s[static_cast<int>(v)];
}

inline const char* to_string(FileSubType v) {
    static const char* s[] = {
        "LocalFileServer","S3Server","GCSServer","AzureServer","DropboxServer",
        "SFTPServer","FTPServer","IBMServer","OracleServer","DigitalOceanServer","GoogleDriveServer"
    };
    return s[static_cast<int>(v)];
}

inline const char* to_string(APISubType v) {
    static const char* s[] = {"BigQueryServer","SalesforceServer"};
    return s[static_cast<int>(v)];
}

// ============================================================================
// Strategy Constants
// ============================================================================

namespace strategy {
namespace read {
    inline const std::string JDBC_SELECT = "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy";
    inline const std::string LOCAL_CSV = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.CSVReadStrategy";
    inline const std::string LOCAL_JSON = "com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy.JSONReadStrategy";
    inline const std::string S3_CSV = "com.everyarrow.dittoe.io.file.servers.s3.reader.strategy.CSVReadStrategy";
    inline const std::string GCS_CSV = "com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy.CSVReadStrategy";
    inline const std::string AZURE_CSV = "com.everyarrow.dittoe.io.file.servers.azure.reader.strategy.CSVReadStrategy";
    inline const std::string DROPBOX_CSV = "com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy.CSVReadStrategy";
    inline const std::string SFTP_CSV = "com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy.CSVReadStrategy";
    inline const std::string FTP_CSV = "com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy.CSVReadStrategy";
    inline const std::string IBM_CSV = "com.everyarrow.dittoe.io.file.servers.ibm.reader.strategy.CSVReadStrategy";
    inline const std::string ORACLE_CSV = "com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy.CSVReadStrategy";
    inline const std::string DO_CSV = "com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy.CSVReadStrategy";
    inline const std::string GDRIVE_CSV = "com.everyarrow.dittoe.io.file.servers.googledrive.reader.strategy.CSVReadStrategy";
    inline const std::string BQ_QUERY = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.QueryStrategy";
    inline const std::string BQ_AVRO = "com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy.AvroStrategy";
    inline const std::string SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI";
}
namespace write {
    inline const std::string JDBC_INSERT = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy";
    inline const std::string JDBC_REPLACE = "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.ReplaceStrategy";
    inline const std::string PG_UPSERT = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy";
    inline const std::string PG_INSERT_DO_NOTHING = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertDoNothingOnConflictStrategy";
    inline const std::string PG_COPY = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyStrategy";
    inline const std::string PG_COPY_OR_AUTO_ID = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.CopyOrAutoIdStrategy";
    inline const std::string PG_INSERT_OR_UPDATE = "com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.InsertOrUpdateStrategy";
    inline const std::string MSSQL_MERGE = "com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy";
    inline const std::string SQLITE_UPSERT = "com.everyarrow.dittoe.io.database.servers.sqlite.writer.strategy.UpsertOnConflictStrategy";
    inline const std::string LOCAL_CSV = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.CSVWriteStrategy";
    inline const std::string LOCAL_JSON = "com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy.JSONWriteStrategy";
    inline const std::string S3_CSV = "com.everyarrow.dittoe.io.file.servers.s3.writer.strategy.CSVWriteStrategy";
    inline const std::string GCS_CSV = "com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy.CSVWriteStrategy";
    inline const std::string AZURE_CSV = "com.everyarrow.dittoe.io.file.servers.azure.writer.strategy.CSVWriteStrategy";
    inline const std::string DROPBOX_CSV = "com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy.CSVWriteStrategy";
    inline const std::string SFTP_CSV = "com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy.CSVWriteStrategy";
    inline const std::string FTP_CSV = "com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy.CSVWriteStrategy";
    inline const std::string IBM_CSV = "com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy.CSVWriteStrategy";
    inline const std::string ORACLE_CSV = "com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy.CSVWriteStrategy";
    inline const std::string DO_CSV = "com.everyarrow.dittoe.io.file.servers.digitalocean.writer.strategy.CSVWriteStrategy";
    inline const std::string GDRIVE_CSV = "com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy.CSVWriteStrategy";
    inline const std::string BQ_DATASTORE = "com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.DataStoreStrategy";
    inline const std::string SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI";
}
} // namespace strategy

// ============================================================================
// JSON helper (minimal, no dependency)
// ============================================================================

namespace detail {
inline std::string escape_json(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\t': out += "\\t"; break;
            default: out += c;
        }
    }
    return out;
}

inline std::string map_to_json(const std::map<std::string,std::string>& m) {
    std::string out = "{";
    bool first = true;
    for (auto& [k,v] : m) {
        if (!first) out += ",";
        out += "\"" + escape_json(k) + "\":\"" + escape_json(v) + "\"";
        first = false;
    }
    out += "}";
    return out;
}
} // namespace detail

// ============================================================================
// Model classes
// ============================================================================

struct Field {
    std::string name;
    DataType dataType = DataType::String;
    bool isNullable = true;
    std::string description;
    bool hasExpression = false;
    std::string expression;

    Field() = default;
    Field(std::string fieldName, DataType fieldType = DataType::String,
          bool nullable = true)
        : name(std::move(fieldName)), dataType(fieldType), isNullable(nullable) {}

    std::string to_json() const {
        std::string j = "{\"name\":\"" + detail::escape_json(name) + "\"";
        j += ",\"dataType\":\"" + std::string(dittoe::to_string(dataType)) + "\"";
        j += ",\"isNullable\":" + std::string(isNullable ? "true" : "false");
        if (!description.empty())
            j += ",\"description\":\"" + detail::escape_json(description) + "\"";
        if (hasExpression)
            j += ",\"expression\":\"" + detail::escape_json(expression) + "\"";
        j += "}";
        return j;
    }
};

struct Server {
    int id = 0;
    std::string name;
    ServerType type = ServerType::Database;
    std::map<std::string,std::string> connectionDetails;
    std::map<std::string,std::string> additionalProperties;

    std::string to_json() const {
        std::string j = "{\"id\":" + std::to_string(id);
        j += ",\"name\":\"" + detail::escape_json(name) + "\"";
        j += ",\"type\":\"" + std::string(dittoe::to_string(type)) + "\"";
        j += ",\"connectionDetails\":" + detail::map_to_json(connectionDetails);
        j += ",\"additionalProperties\":" + detail::map_to_json(additionalProperties);
        j += "}";
        return j;
    }
};

struct Resource {
    int resourceID = 1;
    std::string resourceName;
    Server server;
    std::string strategy_;
    std::map<std::string,std::string> resourceProperties;
    std::vector<Field> fields;

    std::string to_json() const {
        std::string j = "{\"resourceID\":" + std::to_string(resourceID);
        j += ",\"resourceName\":\"" + detail::escape_json(resourceName) + "\"";
        j += ",\"server\":" + server.to_json();
        j += ",\"strategy\":\"" + detail::escape_json(strategy_) + "\"";
        j += ",\"resourceProperties\":" + detail::map_to_json(resourceProperties);
        j += ",\"fields\":[";
        for (size_t i = 0; i < fields.size(); ++i) {
            if (i) j += ",";
            j += fields[i].to_json();
        }
        j += "]}";
        return j;
    }
};

struct DQRule {
    std::string ruleName;
    std::string ruleExpression;
    std::string to_json() const {
        return "{\"ruleName\":\"" + detail::escape_json(ruleName) +
               "\",\"ruleExpression\":\"" + detail::escape_json(ruleExpression) + "\"}";
    }
};

struct Behavior {
    std::string name;
    BehaviorType behaviorType = BehaviorType::SourceRetrieval;
    std::string description;
    std::map<std::string,std::string> properties;
    bool isStaged = false;
    std::string pluginReference;
    int order = 0;
    StageType stageType = StageType::Process;

    std::string to_json() const {
        std::string j = "{\"name\":\"" + detail::escape_json(name) + "\"";
        j += ",\"behaviorType\":\"" + std::string(dittoe::to_string(behaviorType)) + "\"";
        j += ",\"description\":\"" + detail::escape_json(description) + "\"";
        j += ",\"properties\":" + detail::map_to_json(properties);
        if (isStaged) {
            j += ",\"pluginReference\":\"" + detail::escape_json(pluginReference) + "\"";
            j += ",\"order\":" + std::to_string(order);
            j += ",\"stageType\":\"" + std::string(dittoe::to_string(stageType)) + "\"";
        }
        j += "}";
        return j;
    }
};

struct SourceToTargetMapping {
    Field sourceField;
    Field targetField;
    std::string to_json() const {
        return "{\"sourceField\":" + sourceField.to_json() +
               ",\"targetField\":" + targetField.to_json() + "}";
    }
};

struct ResourceDependencyMapping {
    Resource resource;
    std::vector<Resource> dependencies;
    std::string to_json() const {
        std::string j = "{\"resource\":" + resource.to_json() + ",\"dependencies\":[";
        for (size_t i = 0; i < dependencies.size(); ++i) {
            if (i) j += ",";
            j += dependencies[i].to_json();
        }
        j += "]}";
        return j;
    }
};

struct PipelineMetadata {
    std::string id;
    std::string shortName;
    std::string description;
    TemplateType templateType = TemplateType::DatabaseToDatabase;
    Resource sourceResource;
    Resource targetResource;
    std::vector<SourceToTargetMapping> fieldMappings;
    std::vector<Behavior> behaviors;
    std::map<std::string,std::string> globalProperties;
    std::string createDate, createdBy, lastUpdatedBy, lastUpdatedDate;

    std::string to_json() const {
        std::string j = "{\"id\":\"" + detail::escape_json(id) + "\"";
        j += ",\"shortName\":\"" + detail::escape_json(shortName) + "\"";
        j += ",\"description\":\"" + detail::escape_json(description) + "\"";
        j += ",\"templateType\":\"" + std::string(dittoe::to_string(templateType)) + "\"";
        j += ",\"globalProperties\":" + detail::map_to_json(globalProperties);
        j += ",\"behaviors\":[";
        for (size_t i = 0; i < behaviors.size(); ++i) {
            if (i) j += ",";
            j += behaviors[i].to_json();
        }
        j += "]";
        j += ",\"sourceResourceToProcess\":" + sourceResource.to_json();
        j += ",\"targetResourceToProcess\":" + targetResource.to_json();
        j += ",\"S2TMap\":[";
        for (size_t i = 0; i < fieldMappings.size(); ++i) {
            if (i) j += ",";
            j += fieldMappings[i].to_json();
        }
        j += "]";
        if (!createDate.empty()) j += ",\"createDate\":\"" + detail::escape_json(createDate) + "\"";
        if (!createdBy.empty()) j += ",\"createdBy\":\"" + detail::escape_json(createdBy) + "\"";
        if (!lastUpdatedBy.empty()) j += ",\"lastUpdatedBy\":\"" + detail::escape_json(lastUpdatedBy) + "\"";
        if (!lastUpdatedDate.empty()) j += ",\"lastUpdatedDate\":\"" + detail::escape_json(lastUpdatedDate) + "\"";
        j += "}";
        return j;
    }

    void save(const std::string& path) const {
        std::filesystem::create_directories(std::filesystem::path(path).parent_path());
        std::ofstream f(path);
        if (!f) throw std::runtime_error("Cannot write to " + path);
        f << to_json();
    }
};

// ============================================================================
// Fluent Builders
// ============================================================================

class FieldBuilder {
    Field f_;
public:
    explicit FieldBuilder(const std::string& name) { f_.name = name; }
    FieldBuilder& dataType(DataType dt) { f_.dataType = dt; return *this; }
    FieldBuilder& string_() { return dataType(DataType::String); }
    FieldBuilder& integer() { return dataType(DataType::Integer); }
    FieldBuilder& double_() { return dataType(DataType::Double); }
    FieldBuilder& decimal() { return dataType(DataType::Decimal); }
    FieldBuilder& boolean() { return dataType(DataType::Boolean); }
    FieldBuilder& date() { return dataType(DataType::Date); }
    FieldBuilder& dateTime() { return dataType(DataType::DateTime); }
    FieldBuilder& time() { return dataType(DataType::Time); }
    FieldBuilder& uuid() { return dataType(DataType::UUID); }
    FieldBuilder& timeWithTz() { return dataType(DataType::TimeWithTimeZone); }
    FieldBuilder& dateTimeWithTz() { return dataType(DataType::DateTimeWithTimeZone); }
    FieldBuilder& json() { return dataType(DataType::JSON); }
    FieldBuilder& jsonb() { return dataType(DataType::JSONB); }
    FieldBuilder& array() { return dataType(DataType::Array); }
    FieldBuilder& number() { return dataType(DataType::Number); }
    FieldBuilder& bytes() { return dataType(DataType::Bytes); }
    FieldBuilder& xml() { return dataType(DataType::XML); }
    FieldBuilder& nullable(bool v = true) { f_.isNullable = v; return *this; }
    FieldBuilder& required() { return nullable(false); }
    FieldBuilder& description(const std::string& d) { f_.description = d; return *this; }
    FieldBuilder& expression(const std::string& e) { f_.hasExpression = true; f_.expression = e; return *this; }
    Field build() const { return f_; }
};

class ServerBuilder {
    Server s_;
public:
    explicit ServerBuilder(const std::string& name) { s_.name = name; }
    ServerBuilder& id(int v) { s_.id = v; return *this; }
    ServerBuilder& additionalProperty(const std::string& k, const std::string& v) {
        s_.additionalProperties[k] = v; return *this;
    }

    // Database (5)
    ServerBuilder& postgresql(const std::string& h, int port, const std::string& db,
                              const std::string& u, const std::string& p) {
        s_.type = ServerType::Database;
        s_.connectionDetails = {{"databaseType","PostgreSQLServer"},{"host",h},
            {"port",std::to_string(port)},{"database",db},{"user",u},{"password",p}};
        return *this;
    }
    ServerBuilder& mysql(const std::string& h, int port, const std::string& db,
                         const std::string& u, const std::string& p) {
        s_.type = ServerType::Database;
        s_.connectionDetails = {{"databaseType","MySQLServer"},{"host",h},
            {"port",std::to_string(port)},{"database",db},{"user",u},{"password",p}};
        return *this;
    }
    ServerBuilder& mssql(const std::string& h, int port, const std::string& db,
                         const std::string& u, const std::string& p) {
        s_.type = ServerType::Database;
        s_.connectionDetails = {{"databaseType","MSSQLServer"},{"host",h},
            {"port",std::to_string(port)},{"database",db},{"user",u},{"password",p}};
        return *this;
    }
    ServerBuilder& sqlite(const std::string& path) {
        s_.type = ServerType::Database;
        s_.connectionDetails = {{"databaseType","SQLiteServer"},{"path",path}};
        return *this;
    }
    ServerBuilder& snowflake(const std::string& h, int port, const std::string& db,
                             const std::string& u, const std::string& p) {
        s_.type = ServerType::Database;
        s_.connectionDetails = {{"databaseType","SnowflakeServer"},{"host",h},
            {"port",std::to_string(port)},{"database",db},{"user",u},{"password",p}};
        return *this;
    }

    // File (11)
    ServerBuilder& localFile(const std::string& path) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","LocalFileServer"},{"path",path}};
        return *this;
    }
    ServerBuilder& s3(const std::string& bucket, const std::string& region,
                      const std::string& ak, const std::string& sk) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","S3Server"},{"bucket",bucket},
            {"region",region},{"accessKey",ak},{"secretKey",sk}};
        return *this;
    }
    ServerBuilder& gcs(const std::string& bucket, const std::string& proj,
                       const std::string& creds) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","GCSServer"},{"bucket",bucket},
            {"projectId",proj},{"credentialsFile",creds}};
        return *this;
    }
    ServerBuilder& azure(const std::string& container, const std::string& acct,
                         const std::string& key) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","AzureServer"},{"container",container},
            {"accountName",acct},{"accountKey",key}};
        return *this;
    }
    ServerBuilder& dropbox(const std::string& token) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","DropboxServer"},{"accessToken",token}};
        return *this;
    }
    ServerBuilder& sftp(const std::string& h, int port, const std::string& path,
                        const std::string& u, const std::string& p) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","SFTPServer"},{"host",h},
            {"port",std::to_string(port)},{"path",path},{"user",u},{"password",p}};
        return *this;
    }
    ServerBuilder& ftp(const std::string& h, int port, const std::string& path,
                       const std::string& u, const std::string& p) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","FTPServer"},{"host",h},
            {"port",std::to_string(port)},{"path",path},{"user",u},{"password",p}};
        return *this;
    }
    ServerBuilder& ibm(const std::string& endpoint, const std::string& apiKey,
                       const std::string& bucket) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","IBMServer"},{"endpoint",endpoint},
            {"apiKey",apiKey},{"bucket",bucket}};
        return *this;
    }
    ServerBuilder& oracleCloud(const std::string& ns, const std::string& bucket,
                               const std::string& compartment) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","OracleServer"},{"namespace",ns},
            {"bucket",bucket},{"compartmentId",compartment}};
        return *this;
    }
    ServerBuilder& digitalOcean(const std::string& space, const std::string& region,
                                const std::string& ak, const std::string& sk) {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","DigitalOceanServer"},{"space",space},
            {"region",region},{"accessKey",ak},{"secretKey",sk}};
        return *this;
    }
    ServerBuilder& googleDrive(const std::string& creds, const std::string& folder = "") {
        s_.type = ServerType::File;
        s_.connectionDetails = {{"fileType","GoogleDriveServer"},{"credentialsFile",creds},{"folderId",folder}};
        return *this;
    }

    // API (2)
    ServerBuilder& bigquery(const std::string& proj, const std::string& ds,
                            const std::string& creds) {
        s_.type = ServerType::API;
        s_.connectionDetails = {{"apiType","BigQueryServer"},{"projectId",proj},
            {"dataset",ds},{"credentialsFile",creds}};
        return *this;
    }
    ServerBuilder& salesforce(const std::string& url, const std::string& u,
                              const std::string& p, const std::string& token) {
        s_.type = ServerType::API;
        s_.connectionDetails = {{"apiType","SalesforceServer"},{"loginUrl",url},
            {"username",u},{"password",p},{"securityToken",token}};
        return *this;
    }
    Server build() const { return s_; }
};

class ResourceBuilder {
    Resource r_;
public:
    explicit ResourceBuilder(const std::string& name) { r_.resourceName = name; }
    ResourceBuilder& id(int v) { r_.resourceID = v; return *this; }
    ResourceBuilder& server(const Server& s) { r_.server = s; return *this; }
    ResourceBuilder& addField(const std::string& name, DataType dt = DataType::String,
                              bool nullable = true) {
        r_.fields.emplace_back(name, dt, nullable);
        return *this;
    }
    ResourceBuilder& addField(const Field& f) { r_.fields.push_back(f); return *this; }
    ResourceBuilder& schema(const std::string& s) { r_.resourceProperties["schema"] = s; return *this; }
    ResourceBuilder& property(const std::string& k, const std::string& v) {
        r_.resourceProperties[k] = v; return *this;
    }
    ResourceBuilder& strategy(const std::string& s) { r_.strategy_ = s; return *this; }

    // DB read
    ResourceBuilder& jdbcRead() { return strategy(strategy::read::JDBC_SELECT); }
    // DB write
    ResourceBuilder& insert() { return strategy(strategy::write::JDBC_INSERT); }
    ResourceBuilder& replace() { return strategy(strategy::write::JDBC_REPLACE); }
    ResourceBuilder& pgUpsert(const std::string& c) { r_.resourceProperties["upsertConflictConstraint"]=c; return strategy(strategy::write::PG_UPSERT); }
    ResourceBuilder& pgInsertDoNothing(const std::string& c) { r_.resourceProperties["upsertConflictConstraint"]=c; return strategy(strategy::write::PG_INSERT_DO_NOTHING); }
    ResourceBuilder& pgCopy() { return strategy(strategy::write::PG_COPY); }
    ResourceBuilder& pgCopyOrAutoId() { return strategy(strategy::write::PG_COPY_OR_AUTO_ID); }
    ResourceBuilder& pgInsertOrUpdate() { return strategy(strategy::write::PG_INSERT_OR_UPDATE); }
    ResourceBuilder& mssqlMerge() { return strategy(strategy::write::MSSQL_MERGE); }
    ResourceBuilder& sqliteUpsert(const std::string& c) { r_.resourceProperties["upsertConflictConstraint"]=c; return strategy(strategy::write::SQLITE_UPSERT); }
    // File read/write
    ResourceBuilder& csvRead() { return strategy(strategy::read::LOCAL_CSV); }
    ResourceBuilder& jsonRead() { return strategy(strategy::read::LOCAL_JSON); }
    ResourceBuilder& csvWrite() { return strategy(strategy::write::LOCAL_CSV); }
    ResourceBuilder& jsonWrite() { return strategy(strategy::write::LOCAL_JSON); }
    ResourceBuilder& s3CsvRead() { return strategy(strategy::read::S3_CSV); }
    ResourceBuilder& s3CsvWrite() { return strategy(strategy::write::S3_CSV); }
    ResourceBuilder& gcsCsvRead() { return strategy(strategy::read::GCS_CSV); }
    ResourceBuilder& gcsCsvWrite() { return strategy(strategy::write::GCS_CSV); }
    ResourceBuilder& azureCsvRead() { return strategy(strategy::read::AZURE_CSV); }
    ResourceBuilder& azureCsvWrite() { return strategy(strategy::write::AZURE_CSV); }
    ResourceBuilder& dropboxCsvRead() { return strategy(strategy::read::DROPBOX_CSV); }
    ResourceBuilder& dropboxCsvWrite() { return strategy(strategy::write::DROPBOX_CSV); }
    ResourceBuilder& sftpCsvRead() { return strategy(strategy::read::SFTP_CSV); }
    ResourceBuilder& sftpCsvWrite() { return strategy(strategy::write::SFTP_CSV); }
    ResourceBuilder& ftpCsvRead() { return strategy(strategy::read::FTP_CSV); }
    ResourceBuilder& ftpCsvWrite() { return strategy(strategy::write::FTP_CSV); }
    ResourceBuilder& ibmCsvRead() { return strategy(strategy::read::IBM_CSV); }
    ResourceBuilder& ibmCsvWrite() { return strategy(strategy::write::IBM_CSV); }
    ResourceBuilder& oracleCsvRead() { return strategy(strategy::read::ORACLE_CSV); }
    ResourceBuilder& oracleCsvWrite() { return strategy(strategy::write::ORACLE_CSV); }
    ResourceBuilder& digitalOceanCsvRead() { return strategy(strategy::read::DO_CSV); }
    ResourceBuilder& digitalOceanCsvWrite() { return strategy(strategy::write::DO_CSV); }
    ResourceBuilder& googleDriveCsvRead() { return strategy(strategy::read::GDRIVE_CSV); }
    ResourceBuilder& googleDriveCsvWrite() { return strategy(strategy::write::GDRIVE_CSV); }
    // API
    ResourceBuilder& bigqueryQueryRead() { return strategy(strategy::read::BQ_QUERY); }
    ResourceBuilder& bigqueryAvroRead() { return strategy(strategy::read::BQ_AVRO); }
    ResourceBuilder& bigqueryWrite() { return strategy(strategy::write::BQ_DATASTORE); }
    ResourceBuilder& salesforceRead() { return strategy(strategy::read::SALESFORCE); }
    ResourceBuilder& salesforceWrite() { return strategy(strategy::write::SALESFORCE); }

    Resource build() const { return r_; }
};

class BehaviorBuilder {
    Behavior b_;
public:
    explicit BehaviorBuilder(const std::string& name = "") { b_.name = name; }
    BehaviorBuilder& type(BehaviorType bt) { b_.behaviorType = bt; return *this; }
    BehaviorBuilder& description(const std::string& d) { b_.description = d; return *this; }
    BehaviorBuilder& property(const std::string& k, const std::string& v) { b_.properties[k]=v; return *this; }
    BehaviorBuilder& errorThreshold(int t) { return property("errorCountThreshold", std::to_string(t)); }
    BehaviorBuilder& staged(StageType s, int ord = 0, const std::string& plugin = "") {
        b_.isStaged = true; b_.stageType = s; b_.order = ord; b_.pluginReference = plugin;
        return *this;
    }

    // All 22 types
    BehaviorBuilder& formatting() { b_.behaviorType = BehaviorType::Formatting; if(b_.name.empty()) b_.name="Formatting"; return *this; }
    BehaviorBuilder& sourceRetrieval() { b_.behaviorType = BehaviorType::SourceRetrieval; if(b_.name.empty()) b_.name="SourceRetrieval"; return *this; }
    BehaviorBuilder& targetInsertUpdate() { b_.behaviorType = BehaviorType::TargetInsertUpdate; if(b_.name.empty()) b_.name="TargetInsertUpdate"; return *this; }
    BehaviorBuilder& transformation() { b_.behaviorType = BehaviorType::Transformation; if(b_.name.empty()) b_.name="Transformation"; return *this; }
    BehaviorBuilder& encryption() { b_.behaviorType = BehaviorType::Encryption; if(b_.name.empty()) b_.name="Encryption"; return *this; }
    BehaviorBuilder& logging() { b_.behaviorType = BehaviorType::Logging; if(b_.name.empty()) b_.name="Logging"; return *this; }
    BehaviorBuilder& instrumentation() { b_.behaviorType = BehaviorType::Instrumentation; if(b_.name.empty()) b_.name="Instrumentation"; return *this; }
    BehaviorBuilder& lineageTracking() { b_.behaviorType = BehaviorType::lineageTracking; if(b_.name.empty()) b_.name="lineageTracking"; return *this; }
    BehaviorBuilder& keyGeneration() { b_.behaviorType = BehaviorType::KeyGeneration; if(b_.name.empty()) b_.name="KeyGeneration"; return *this; }
    BehaviorBuilder& masking() { b_.behaviorType = BehaviorType::Masking; if(b_.name.empty()) b_.name="Masking"; return *this; }
    BehaviorBuilder& deduplication() { b_.behaviorType = BehaviorType::Deduplication; if(b_.name.empty()) b_.name="Deduplication"; return *this; }
    BehaviorBuilder& schemaEvolution() { b_.behaviorType = BehaviorType::SchemaEvolution; if(b_.name.empty()) b_.name="SchemaEvolution"; return *this; }
    BehaviorBuilder& retry() { b_.behaviorType = BehaviorType::Retry; if(b_.name.empty()) b_.name="Retry"; return *this; }
    BehaviorBuilder& schemaValidation() { b_.behaviorType = BehaviorType::SchemaValidation; if(b_.name.empty()) b_.name="SchemaValidation"; return *this; }
    BehaviorBuilder& dqRuleChecks() { b_.behaviorType = BehaviorType::DQRuleChecks; if(b_.name.empty()) b_.name="DQRuleChecks"; return *this; }
    BehaviorBuilder& archive() { b_.behaviorType = BehaviorType::Archive; if(b_.name.empty()) b_.name="Archive"; return *this; }
    BehaviorBuilder& microBatch() { b_.behaviorType = BehaviorType::MicroBatch; if(b_.name.empty()) b_.name="MicroBatch"; return *this; }
    BehaviorBuilder& dataScienceAlgorithms() { b_.behaviorType = BehaviorType::DataScienceAlgorithms; if(b_.name.empty()) b_.name="DataScienceAlgorithms"; return *this; }
    BehaviorBuilder& customCode() { b_.behaviorType = BehaviorType::CustomCode; if(b_.name.empty()) b_.name="CustomCode"; return *this; }
    BehaviorBuilder& compression() { b_.behaviorType = BehaviorType::Compression; if(b_.name.empty()) b_.name="Compression"; return *this; }
    BehaviorBuilder& externalWidget() { b_.behaviorType = BehaviorType::ExternalWidget; if(b_.name.empty()) b_.name="ExternalWidget"; return *this; }
    BehaviorBuilder& cache() { b_.behaviorType = BehaviorType::Cache; if(b_.name.empty()) b_.name="Cache"; return *this; }

    Behavior build() const { return b_; }
};

class PipelineBuilder {
    PipelineMetadata p_;
public:
    explicit PipelineBuilder(const std::string& shortName) {
        p_.shortName = shortName;
        p_.id = "pipeline-" + shortName;
    }
    PipelineBuilder& id(const std::string& v) { p_.id = v; return *this; }
    PipelineBuilder& description(const std::string& d) { p_.description = d; return *this; }
    PipelineBuilder& createdBy(const std::string& v) { p_.createdBy = v; return *this; }
    PipelineBuilder& createDate(const std::string& v) { p_.createDate = v; return *this; }

    // All 16 template types
    PipelineBuilder& templateType(TemplateType tt) { p_.templateType = tt; return *this; }
    PipelineBuilder& fileToFile() { return templateType(TemplateType::FileToFile); }
    PipelineBuilder& fileToDatabase() { return templateType(TemplateType::FileToDatabase); }
    PipelineBuilder& fileToStream() { return templateType(TemplateType::FileToStream); }
    PipelineBuilder& fileToApi() { return templateType(TemplateType::FileToAPI); }
    PipelineBuilder& databaseToDatabase() { return templateType(TemplateType::DatabaseToDatabase); }
    PipelineBuilder& databaseToFile() { return templateType(TemplateType::DatabaseToFile); }
    PipelineBuilder& databaseToStream() { return templateType(TemplateType::DatabaseToStream); }
    PipelineBuilder& databaseToApi() { return templateType(TemplateType::DatabaseToAPI); }
    PipelineBuilder& streamToStream() { return templateType(TemplateType::StreamToStream); }
    PipelineBuilder& streamToFile() { return templateType(TemplateType::StreamToFile); }
    PipelineBuilder& streamToDatabase() { return templateType(TemplateType::StreamToDatabase); }
    PipelineBuilder& streamToApi() { return templateType(TemplateType::StreamToAPI); }
    PipelineBuilder& apiToApi() { return templateType(TemplateType::APIToAPI); }
    PipelineBuilder& apiToFile() { return templateType(TemplateType::APIToFile); }
    PipelineBuilder& apiToDatabase() { return templateType(TemplateType::APIToDatabase); }
    PipelineBuilder& apiToStream() { return templateType(TemplateType::APIToStream); }

    PipelineBuilder& source(const Resource& r) { p_.sourceResource = r; return *this; }
    PipelineBuilder& target(const Resource& r) { p_.targetResource = r; return *this; }
    PipelineBuilder& mapField(const Field& s, const Field& t) { p_.fieldMappings.push_back({s,t}); return *this; }
    PipelineBuilder& mapFieldsSameName() {
        for (auto& sf : p_.sourceResource.fields)
            for (auto& tf : p_.targetResource.fields)
                if (sf.name == tf.name) { p_.fieldMappings.push_back({sf, tf}); break; }
        return *this;
    }
    PipelineBuilder& behavior(const Behavior& b) { p_.behaviors.push_back(b); return *this; }
    PipelineBuilder& withDefaultBehaviors() {
        p_.behaviors = {
            BehaviorBuilder().sourceRetrieval().errorThreshold(1).build(),
            BehaviorBuilder().targetInsertUpdate().errorThreshold(1).build(),
            BehaviorBuilder().instrumentation().build(),
            BehaviorBuilder().transformation().build(),
        };
        return *this;
    }
    PipelineBuilder& globalProperty(const std::string& k, const std::string& v) {
        p_.globalProperties[k] = v; return *this;
    }
    PipelineMetadata build() const { return p_; }
};

// ============================================================================
// Executor
// ============================================================================

inline bool isValidPipelineName(const std::string& name) {
    if (name.empty()) return false;
    if (!std::isalnum(static_cast<unsigned char>(name[0]))) return false;
    return std::all_of(name.begin(), name.end(), [](unsigned char c) {
        return std::isalnum(c) || c == '_' || c == '-' || c == '.';
    });
}

inline bool isSafeExecutorPath(const std::string& path) {
    if (path.empty()) return false;
    return std::all_of(path.begin(), path.end(), [](unsigned char c) {
        return !std::iscntrl(c) && c != '\'';
    });
}

inline std::string shellQuote(const std::string& value) {
    if (!isSafeExecutorPath(value)) {
        throw std::invalid_argument("Unsafe executor command argument");
    }
    return "'" + value + "'";
}

struct ExecutionResult {
    bool success = false;
    int returnCode = -1;
    std::string stdoutStr;
    std::string stderrStr;
};

inline ExecutionResult execute(const std::string& jarPath, PipelineMetadata& pipeline,
                                const std::string& baseDir, int /*timeout*/ = 300) {
    ExecutionResult result;
    if (!isValidPipelineName(pipeline.shortName)) {
        result.stderrStr = "Invalid pipeline name. Use letters, digits, underscore, hyphen, or dot, starting with a letter or digit.";
        return result;
    }
    if (!isSafeExecutorPath(jarPath) || !isSafeExecutorPath(baseDir)) {
        result.stderrStr = "Invalid executor path. Paths must be non-empty and must not contain control characters or single quotes.";
        return result;
    }

    auto pmdDir = baseDir + "/pipelines/" + pipeline.shortName;
    std::filesystem::create_directories(pmdDir);
    pipeline.save(pmdDir + "/pmd.json");

    auto pipelinesDir = baseDir + "/pipelines";
    std::string cmd = "BASE_DIR=" + shellQuote(pipelinesDir) + " java -jar " +
                      shellQuote(jarPath) + " -p " + shellQuote(pipeline.shortName) + " 2>&1";
    FILE* fp = popen(cmd.c_str(), "r");
    if (!fp) { result.stderrStr = "popen failed"; return result; }
    char buf[4096];
    while (fgets(buf, sizeof(buf), fp)) result.stdoutStr += buf;
    result.returnCode = pclose(fp);
    result.success = (result.returnCode == 0);
    return result;
}

} // namespace dittoe

// ============================================================================
// Config
// ============================================================================

namespace dittoe {

struct DittoeConfig {
    std::string jarPath;
    std::string baseDir = "./pipelines";
    std::string javaPath = "java";
    std::vector<std::string> javaOpts;
    int timeout = 300;

    static DittoeConfig fromEnv() {
        DittoeConfig c;
        auto getVar = [](const char* name, const char* def = "") -> std::string {
            const char* v = std::getenv(name);
            return v ? v : def;
        };
        c.jarPath = getVar("DITTOE_JAR");
        c.baseDir = getVar("DITTOE_BASE_DIR", "./pipelines");
        c.javaPath = getVar("DITTOE_JAVA", "java");
        c.timeout = 300;
        auto tStr = getVar("DITTOE_TIMEOUT", "300");
        try { c.timeout = std::stoi(tStr); } catch (...) {}

        auto optsStr = getVar("DITTOE_JAVA_OPTS");
        if (!optsStr.empty()) {
            std::string token;
            std::istringstream ss(optsStr);
            while (std::getline(ss, token, ',')) {
                auto start = token.find_first_not_of(' ');
                auto end = token.find_last_not_of(' ');
                if (start != std::string::npos)
                    c.javaOpts.push_back(token.substr(start, end - start + 1));
            }
        }
        return c;
    }

    std::vector<std::string> validate() const {
        std::vector<std::string> errors;
        if (jarPath.empty()) errors.push_back("JarPath is required (set DITTOE_JAR)");
        else if (!std::filesystem::exists(jarPath)) errors.push_back("JAR file not found: " + jarPath);
        if (javaPath.empty()) errors.push_back("JavaPath is required");
        if (baseDir.empty()) errors.push_back("BaseDir is required");
        if (timeout <= 0) errors.push_back("Timeout must be positive, got: " + std::to_string(timeout));
        return errors;
    }

    std::string ensureBaseDir() const {
        auto abs = std::filesystem::absolute(baseDir).string();
        std::filesystem::create_directories(abs);
        return abs;
    }
};

// ============================================================================
// Facade: Connection + ConnectionBuilder + migrate
// ============================================================================

struct Connection {
    std::string name;
    Server server;
    std::vector<Field> fields;
    std::string strategy_;
    std::string schemaName;
    std::map<std::string, std::string> resourceProperties;
    std::string dbType;

    std::vector<std::string> validate() const {
        std::vector<std::string> errors;
        if (name.empty()) errors.push_back("Connection name is required");
        if (server.name.empty()) errors.push_back("Connection '" + name + "': server is required");
        if (fields.empty()) errors.push_back("Connection '" + name + "': at least one field is required");
        if (strategy_.empty()) errors.push_back("Connection '" + name + "': strategy is required");
        return errors;
    }
};

namespace detail {
inline DataType resolveDataType(const std::string& t) {
    if (t == "string" || t == "str") return DataType::String;
    if (t == "integer" || t == "int") return DataType::Integer;
    if (t == "double" || t == "float") return DataType::Double;
    if (t == "decimal") return DataType::Decimal;
    if (t == "boolean" || t == "bool") return DataType::Boolean;
    if (t == "date") return DataType::Date;
    if (t == "datetime") return DataType::DateTime;
    if (t == "uuid") return DataType::UUID;
    return DataType::String;
}
inline std::string defaultReadStrategy(const std::string& dbType) {
    if (dbType == "PostgreSQLServer" || dbType == "MySQLServer" || dbType == "MSSQLServer" ||
        dbType == "SQLiteServer" || dbType == "SnowflakeServer") return strategy::read::JDBC_SELECT;
    if (dbType == "LocalFileServer") return strategy::read::LOCAL_CSV;
    if (dbType == "S3Server") return strategy::read::S3_CSV;
    if (dbType == "GCSServer") return strategy::read::GCS_CSV;
    if (dbType == "BigQueryServer") return strategy::read::BQ_QUERY;
    if (dbType == "SalesforceServer") return strategy::read::SALESFORCE;
    return "";
}
inline std::string defaultWriteStrategy(const std::string& dbType) {
    if (dbType == "PostgreSQLServer" || dbType == "MySQLServer" || dbType == "MSSQLServer" ||
        dbType == "SQLiteServer" || dbType == "SnowflakeServer") return strategy::write::JDBC_INSERT;
    if (dbType == "LocalFileServer") return strategy::write::LOCAL_CSV;
    if (dbType == "S3Server") return strategy::write::S3_CSV;
    if (dbType == "GCSServer") return strategy::write::GCS_CSV;
    if (dbType == "BigQueryServer") return strategy::write::BQ_DATASTORE;
    if (dbType == "SalesforceServer") return strategy::write::SALESFORCE;
    return "";
}
} // namespace detail

inline Field parseField(const std::string& spec) {
    Field f;
    auto pos = spec.find(':');
    if (pos == std::string::npos) { f.name = spec; f.dataType = DataType::String; }
    else {
        f.name = spec.substr(0, pos);
        auto t = spec.substr(pos + 1);
        // lowercase
        std::string lower;
        for (char c : t) lower += static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        f.dataType = detail::resolveDataType(lower);
    }
    return f;
}

class ConnectionBuilder {
    std::string name_;
    ServerBuilder sb_;
    std::vector<Field> fields_;
    std::string strategy_;
    std::string schema_;
    std::map<std::string, std::string> props_;
    std::string dbType_;
    bool serverBuilt_ = false;
    bool isSource_ = true;
public:
    explicit ConnectionBuilder(const std::string& name) : name_(name), sb_(name) {}

    ConnectionBuilder& postgresql(const std::string& h, int port, const std::string& db,
                                   const std::string& u, const std::string& p) {
        sb_.postgresql(h, port, db, u, p); dbType_ = "PostgreSQLServer"; serverBuilt_ = true; return *this;
    }
    ConnectionBuilder& mysql(const std::string& h, int port, const std::string& db,
                              const std::string& u, const std::string& p) {
        sb_.mysql(h, port, db, u, p); dbType_ = "MySQLServer"; serverBuilt_ = true; return *this;
    }
    ConnectionBuilder& mssql(const std::string& h, int port, const std::string& db,
                              const std::string& u, const std::string& p) {
        sb_.mssql(h, port, db, u, p); dbType_ = "MSSQLServer"; serverBuilt_ = true; return *this;
    }
    ConnectionBuilder& sqlite(const std::string& path) {
        sb_.sqlite(path); dbType_ = "SQLiteServer"; serverBuilt_ = true; return *this;
    }
    ConnectionBuilder& localFile(const std::string& path) {
        sb_.localFile(path); dbType_ = "LocalFileServer"; serverBuilt_ = true; return *this;
    }
    ConnectionBuilder& s3(const std::string& bucket, const std::string& region,
                           const std::string& ak, const std::string& sk) {
        sb_.s3(bucket, region, ak, sk); dbType_ = "S3Server"; serverBuilt_ = true; return *this;
    }

    ConnectionBuilder& schema(const std::string& s) { schema_ = s; return *this; }
    ConnectionBuilder& addField(const std::string& name, DataType dt = DataType::String, bool nullable = true) {
        fields_.emplace_back(name, dt, nullable); return *this;
    }
    ConnectionBuilder& fields(std::initializer_list<std::string> specs) {
        fields_.clear();
        for (auto& s : specs) fields_.push_back(parseField(s));
        return *this;
    }
    ConnectionBuilder& asSource() {
        isSource_ = true;
        if (strategy_.empty()) strategy_ = detail::defaultReadStrategy(dbType_);
        return *this;
    }
    ConnectionBuilder& asTarget() {
        isSource_ = false;
        if (strategy_.empty()) strategy_ = detail::defaultWriteStrategy(dbType_);
        return *this;
    }
    ConnectionBuilder& property(const std::string& k, const std::string& v) { props_[k] = v; return *this; }

    Connection build() const {
        if (!serverBuilt_) throw std::runtime_error("No server type configured");
        Connection c;
        c.name = name_;
        c.server = sb_.build();
        c.fields = fields_;
        c.strategy_ = strategy_.empty() ? (isSource_ ? detail::defaultReadStrategy(dbType_) : detail::defaultWriteStrategy(dbType_)) : strategy_;
        c.schemaName = schema_;
        c.resourceProperties = props_;
        if (!schema_.empty()) c.resourceProperties["schema"] = schema_;
        c.dbType = dbType_;
        return c;
    }
};

inline std::vector<Behavior> defaultBehaviors() {
    return {
        BehaviorBuilder("source-retrieval").sourceRetrieval().errorThreshold(1).build(),
        BehaviorBuilder("target-insert-update").targetInsertUpdate().errorThreshold(1).build(),
        BehaviorBuilder("instrumentation").instrumentation().build(),
        BehaviorBuilder("transformation").transformation().build(),
    };
}

inline std::vector<SourceToTargetMapping> buildFieldMappings(
    const std::vector<Field>& src, const std::vector<Field>& tgt,
    const std::string& mode = "same_name",
    const std::map<std::string, std::string>& explicitMap = {}) {

    std::vector<SourceToTargetMapping> out;
    if (mode == "explicit" && !explicitMap.empty()) {
        std::map<std::string, const Field*> srcByName, tgtByName;
        for (auto& f : src) srcByName[f.name] = &f;
        for (auto& f : tgt) tgtByName[f.name] = &f;
        for (auto& [sk, tv] : explicitMap) {
            auto si = srcByName.find(sk);
            auto ti = tgtByName.find(tv);
            if (si != srcByName.end() && ti != tgtByName.end())
                out.push_back({*si->second, *ti->second});
        }
        return out;
    }
    // same_name
    std::map<std::string, const Field*> tgtByName;
    for (auto& f : tgt) tgtByName[f.name] = &f;
    for (auto& sf : src) {
        auto it = tgtByName.find(sf.name);
        if (it != tgtByName.end()) out.push_back({sf, *it->second});
    }
    return out;
}

inline ExecutionResult migrate(const DittoeConfig& cfg, Connection& source, Connection& target,
                                const std::string& pipelineName = "",
                                std::vector<Behavior> behaviors = {},
                                const std::string& fieldMapping = "same_name",
                                const std::map<std::string, std::string>& explicitMap = {},
                                int timeout = 0) {
    auto srcErrors = source.validate();
    auto tgtErrors = target.validate();
    srcErrors.insert(srcErrors.end(), tgtErrors.begin(), tgtErrors.end());
    if (!srcErrors.empty()) {
        std::string msg = "Validation failed:";
        for (auto& e : srcErrors) msg += "\n  - " + e;
        throw std::runtime_error(msg);
    }

    std::string name = pipelineName.empty() ? (source.name + "-to-" + target.name) : pipelineName;
    if (behaviors.empty()) behaviors = defaultBehaviors();

    Resource srcRes; srcRes.resourceID = 1; srcRes.resourceName = source.name;
    srcRes.server = source.server; srcRes.strategy_ = source.strategy_;
    srcRes.resourceProperties = source.resourceProperties; srcRes.fields = source.fields;

    Resource tgtRes; tgtRes.resourceID = 2; tgtRes.resourceName = target.name;
    tgtRes.server = target.server; tgtRes.strategy_ = target.strategy_;
    tgtRes.resourceProperties = target.resourceProperties; tgtRes.fields = target.fields;

    auto mappings = buildFieldMappings(source.fields, target.fields, fieldMapping, explicitMap);

    PipelineMetadata pipeline;
    pipeline.id = name; pipeline.shortName = name;
    pipeline.description = "Migration: " + source.name + " -> " + target.name;
    pipeline.sourceResource = srcRes; pipeline.targetResource = tgtRes;
    pipeline.fieldMappings = mappings; pipeline.behaviors = behaviors;

    auto baseDir = cfg.ensureBaseDir();
    return dittoe::execute(cfg.jarPath, pipeline, baseDir, timeout > 0 ? timeout : cfg.timeout);
}

} // namespace dittoe

#endif // DITTOE_HPP
