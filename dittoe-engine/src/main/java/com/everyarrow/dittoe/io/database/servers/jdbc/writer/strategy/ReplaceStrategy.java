package com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class ReplaceStrategy extends JDBCWriteStrategy {
    private final Logger logger = LoggerFactory.getLogger(ReplaceStrategy.class);

    public ReplaceStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void write()
            throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        try {
            if (this.shouldTruncate()) {
                this.getLogger().info("Truncating resource {}", this.getWriter().getServer().getSafeResourceName());

                try (Connection connection = this.getWriter().getServer().getConnection();
                     Statement statement = connection.createStatement()) {
                    StringBuilder query = new StringBuilder("truncate table ")
                            .append(this.getWriter().getServer().getSafeResourceName());

                    if (this.shouldTruncateCascade())
                        query.append(" cascade");

                    statement.execute(query.toString());
                }
            }

            JDBCWriteStrategy jdbcWriteStrategy = new InsertStrategy();

            jdbcWriteStrategy.setWriter(this.getWriter());
            jdbcWriteStrategy.write();
        } catch (SQLException e) {
            throw new ExecutionException(e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    // truncate only if micro batching is not enabled or the current micro batch is not the first one
    private boolean shouldTruncate() {
        Optional<Behavior> microBatch = MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream().filter(
                behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch
        ).findFirst();

        return microBatch.map(behavior -> Integer.parseInt(behavior.getProperties().getOrDefault("_currentMicroBatch", "1")) == 1).orElse(true);
    }

    private boolean shouldTruncateCascade() {
        return Boolean.parseBoolean(this.getWriter().getServer().getResource().getResourceProperties().getOrDefault("truncateCascade", "false"));
    }
}
