package com.everyarrow.dittoe.io.api.servers.bigquery;

import com.everyarrow.dittoe.io.api.APIServerConnector;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import org.everyarrow.dittoe.schema.DataType;

/**
 * BigQuery-specific API contract layered on top of the generic API server abstraction.
 */
public interface BigQueryServerConnector extends APIServerConnector {
    BigQuery getBigQueryClient();

    GoogleCredentials getCredentials();

    String getSafeResourceName();

    String escapeIdentifierName(String identifierName);

    String getDataTypeCast(DataType dataType);
}
