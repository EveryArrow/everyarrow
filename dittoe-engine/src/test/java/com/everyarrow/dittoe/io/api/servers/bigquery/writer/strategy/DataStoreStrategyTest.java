package com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy;

import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataStoreStrategyTest {
    @Test
    void mapsSourceColumnNamesToBigQueryTargetColumnNames() {
        List<SourceToTargetMapping> mappings = List.of(
                new SourceToTargetMapping(
                        new Field("creditcardid", DataType.Integer),
                        new Field("CreditCardID", DataType.Integer)),
                new SourceToTargetMapping(
                        new Field("cardtype", DataType.String),
                        new Field("CardType", DataType.String)));

        JsonObject row = DataStoreStrategy.toJsonRow(
                Map.of("creditcardid", 42, "cardtype", "Vista"),
                mappings);

        assertEquals("42", row.get("CreditCardID").getAsString());
        assertEquals("Vista", row.get("CardType").getAsString());
    }

    @Test
    void encodesBinaryColumnsForBigQueryBytesFields() {
        byte[] photo = { 0x01, 0x23, (byte) 0xff };
        List<SourceToTargetMapping> mappings = List.of(
                new SourceToTargetMapping(
                        new Field("thumbnailphoto", DataType.Bytes),
                        new Field("ThumbNailPhoto", DataType.Bytes)));

        JsonObject row = DataStoreStrategy.toJsonRow(Map.of("thumbnailphoto", photo), mappings);

        assertEquals(Base64.getEncoder().encodeToString(photo), row.get("ThumbNailPhoto").getAsString());
    }
}
