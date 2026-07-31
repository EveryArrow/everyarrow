package com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy;

import com.google.cloud.bigquery.storage.v1.AvroRows;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;
import java.util.function.Consumer;

public class AvroRowsReader {
    private final DatumReader<GenericRecord> datumReader;

    // Decoder object will be reused to avoid re-allocation and too much garbage collection.
    private BinaryDecoder decoder = null;

    // GenericRecord object will be reused.
    private GenericRecord row = null;

    public AvroRowsReader(org.apache.avro.Schema schema) {
        datumReader = new GenericDatumReader<>(schema);
    }

    /**
     * Sample method for processing AVRO rows which only validates decoding.
     *
     * @param avroRows object returned from the ReadRowsResponse.
     */
    public void processRows(AvroRows avroRows, Consumer<GenericRecord> rowConsumer) throws IOException {
        decoder = DecoderFactory.get()
                .binaryDecoder(avroRows.getSerializedBinaryRows().toByteArray(), decoder);

        while (!decoder.isEnd()) {
            // Reusing object row
            row = datumReader.read(row, decoder);
            rowConsumer.accept(row);
        }
    }
}
