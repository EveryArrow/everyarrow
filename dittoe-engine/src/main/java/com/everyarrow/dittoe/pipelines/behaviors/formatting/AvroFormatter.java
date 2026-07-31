package com.everyarrow.dittoe.pipelines.behaviors.formatting;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.SchemaFormatter;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.Extension;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AvroFormatter {
    public static void generateSchema(PipelineMetadata pmd, DataStoreInterface store) {
        SchemaBuilder.RecordBuilder<Schema> schemaBuilder = SchemaBuilder.record(store.getName());
        var avroFields = schemaBuilder.fields();
        var fields = pmd.getSourceResourceToProcess().getFields();

        for (Field field : fields) {
            switch (field.getDataType()) {
                case DataType.Boolean:
                    avroFields.requiredBoolean(field.getName());
                    break;

                case DataType.Integer:
                    avroFields.requiredInt(field.getName());
                    break;

                case DataType.Double:
                    avroFields.requiredDouble(field.getName());
                    break;

                case DataType.Decimal:
                    avroFields.requiredFloat(field.getName());
                    break;

                case DataType.Date:
                case DataType.Time:
                case DataType.DateTime:
                    avroFields.requiredLong(field.getName());
                    break;

                case DataType.UUID:
                case DataType.String:
                default:
                    avroFields.requiredString(field.getName());
                    break;
            }
        }

        Schema schema = avroFields.endRecord();
        Path tempDirectory = SafePathResolver.resolveAllowedPath(
                pmd.getGlobalProperties().get("temp_directory"), "temp_directory");
        try {
            Files.createDirectories(tempDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Avro temp directory", e);
        }

        String schemaFileName = SafePathResolver.safeFileName(store.getName(), "store")
                + "-schema" + Extension.AVRO;
        Path schemaFile = SafePathResolver.resolveUnderBase(tempDirectory, schemaFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(schemaFile)) {
            writer.write(SchemaFormatter.format("json/pretty", schema));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Avro schema file", e);
        }

        String formattedFileName = SafePathResolver.safeFileName(
                pmd.getTargetResourceToProcess().getResourceName(), "target")
                + "-formatted" + Extension.AVRO;
        Path formattedFile = SafePathResolver.resolveUnderBase(tempDirectory, formattedFileName);

        try (DataFileWriter<GenericRecord> dataFileWriter =
                     new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            dataFileWriter.create(schema, formattedFile.toFile());

            // Write avro formatted data
            for (int i = 0; i < store.size(); i++) {
                GenericData.Record record = new GenericData.Record(schema);
                var row = store.get(i);
                Object value;
                for (Field field : fields) {
                    value = row.get(field.getName());
                    if (value != null) {
                        switch (field.getDataType()) {
                            case DataType.Integer:
                                record.put(field.getName(), Integer.parseInt((String) value));
                                break;
                            case DataType.Decimal:
                                record.put(field.getName(), Float.parseFloat((String) value));
                                break;
                            case DataType.Double:
                                record.put(field.getName(), Double.parseDouble((String) value));
                                break;
                            case DataType.Boolean:
                                record.put(field.getName(), Boolean.parseBoolean((String) value));
                                break;
                            case DataType.Date:
                            case DataType.Time:
                            case DataType.DateTime:
                                Date date = new SimpleDateFormat("yyyy-MM-dd").parse((String) value);
                                record.put(field.getName(), date.getTime());
                                break;
                            case DataType.String:
                            default:
                                record.put(field.getName(), value);
                                break;
                        }
                    }
                }

                dataFileWriter.append(record);
            }
        } catch (IOException | ParseException | DataStoreException e) {
            throw new IllegalStateException("Failed to write Avro formatted data", e);
        }
    }
}
