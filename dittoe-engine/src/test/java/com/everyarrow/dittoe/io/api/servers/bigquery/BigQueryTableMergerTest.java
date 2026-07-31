package com.everyarrow.dittoe.io.api.servers.bigquery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigQueryTableMergerTest {
    @Test
    void mergeSourceDeduplicatesRowsByPrimaryKeyBeforeMerge() {
        String source = BigQueryTableMerger.buildMergeSourceExpression(
                "`project`.`dataset`.`temp_Department`",
                List.of("DepartmentID", "GroupName"));

        assertTrue(source.contains("ROW_NUMBER() OVER (PARTITION BY `DepartmentID`, `GroupName`"));
        assertTrue(source.contains("ORDER BY TO_JSON_STRING(source)"));
        assertTrue(source.contains("WHERE `_dittoe_row_num` = 1"));
        assertTrue(source.contains("SELECT * EXCEPT (`_dittoe_row_num`)"));
        assertFalse(source.contains("USING `project`.`dataset`.`temp_Department` AS t"));
    }
}
