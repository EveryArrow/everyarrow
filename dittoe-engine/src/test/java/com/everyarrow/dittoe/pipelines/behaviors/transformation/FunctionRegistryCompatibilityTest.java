package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FunctionRegistryCompatibilityTest {
    @Test
    void supportsCanonicalAndLegacyConsoleDateFunctionNames() {
        assertInstanceOf(LocalDateTime.class, FunctionRegistry.invoke("current_datetime", new Object[]{}));
        assertInstanceOf(LocalDateTime.class, FunctionRegistry.invoke("currentDateTime", new Object[]{}));
        assertInstanceOf(LocalDate.class, FunctionRegistry.invoke("current_date", new Object[]{}));
        assertInstanceOf(LocalDate.class, FunctionRegistry.invoke("currentDate", new Object[]{}));
        assertInstanceOf(LocalTime.class, FunctionRegistry.invoke("current_time", new Object[]{}));
        assertInstanceOf(LocalTime.class, FunctionRegistry.invoke("currentTime", new Object[]{}));

        LocalDate date = LocalDate.of(2026, 7, 19);
        assertEquals(date.plusDays(2), FunctionRegistry.invoke("date_add", new Object[]{date, 2}));
        assertEquals(date.plusDays(2), FunctionRegistry.invoke("dateAdd", new Object[]{date, 2}));
        assertEquals(date.minusDays(2), FunctionRegistry.invoke("date_sub", new Object[]{date, 2}));
        assertEquals(date.minusDays(2), FunctionRegistry.invoke("dateSub", new Object[]{date, 2}));
    }
}
