package com.everyarrow.dittoe.pipelines.behaviors.formatting;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import org.everyarrow.dittoe.schema.PipelineMetadata;

public class ProtoBuffFormatter {
    public static void generateProtoFile(PipelineMetadata pmd, DataStoreInterface store) throws DataStoreException {
//        Path tempDirectory = Path.of(pmd.getGlobalProperties().get("temp_directory"));
//        File protoSchemaFile = tempDirectory.resolve(store.getName() + "-schema.proto").toFile();

        throw new DataStoreException("Unimplemented");
    }
}
