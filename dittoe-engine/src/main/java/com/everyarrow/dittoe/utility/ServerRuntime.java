package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.Server;

final class ServerRuntime {
    private ServerRuntime() {
    }

    static Server generateServerFromNamespace(String nameSpace) {
        try {
            if (nameSpace == null || !nameSpace.startsWith("com.everyarrow.dittoe.")) {
                throw new RuntimeException("Server class is outside the engine package: " + nameSpace);
            }
            Class<?> clazz = Class.forName(nameSpace);
            return (Server) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException | java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException("Error creating server instance for class: " + nameSpace, e);
        }
    }

    static Resource getResourceFromServer(Server server) {
        if (MetadataRepositoryAPI.getPipelineMetadata().getSourceResourceToProcess().getServer().equals(server)) {
            return MetadataRepositoryAPI.getPipelineMetadata().getSourceResourceToProcess();
        } else if (MetadataRepositoryAPI.getPipelineMetadata().getTargetResourceToProcess().getServer().equals(server)) {
            return MetadataRepositoryAPI.getPipelineMetadata().getTargetResourceToProcess();
        }
        throw new RuntimeException("Could not find the specified server in PipelineMetadata. Server: " + server);
    }
}
