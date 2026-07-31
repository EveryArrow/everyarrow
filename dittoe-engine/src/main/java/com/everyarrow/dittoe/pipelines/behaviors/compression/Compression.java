package com.everyarrow.dittoe.pipelines.behaviors.compression;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.FileType;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class Compression extends StagedBehaviorBase {

    public Compression(String name, String description, Map<String, String> properties, PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name,
                BehaviorType.Compression, properties, description, Integer.MAX_VALUE - 1, // Using a max_value-1 order here so that we always compress first
                StageType.Package, pipelineMetadata, dataStore));
    }

    @Override
    public void exec() throws BehaviorException {
        try {
            String tempDirectory = this.getPipelineMetadata().getGlobalProperties().get("temp_directory");
            String fileExtension = ".".concat(FileType.getFileType(this.getPipelineMetadata().getTargetResourceToProcess().getResourceProperties()).name().toLowerCase());
            String resourceName = this.getPipelineMetadata().getTargetResourceToProcess().getResourceName();
            String fileName = resourceName.concat("-formatted").concat(fileExtension);
            Path inputFile = SafePathResolver.resolveUnderBase(tempDirectory, fileName);
            Path outputFile = SafePathResolver.resolveUnderBase(tempDirectory, resourceName.concat("-compressed").concat(fileExtension));

            FileInputStream fileInputStream = new FileInputStream(inputFile.toFile());
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile.toFile());
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = fileInputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, len);
            }
            gzipOutputStream.close();
            fileInputStream.close();
            fileOutputStream.close();
        } catch (Exception e) {
            throw new BehaviorException(e);
        }

    }
}
