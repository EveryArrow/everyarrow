package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class TemporalValueUtils {
    private TemporalValueUtils() {
    }

    static String formatTimestampWithTimezone(String timestamp, DateTimeFormatter fromPattern,
                                              DateTimeFormatter toPattern) throws ExecutionException {
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestamp, fromPattern);
            return zonedDateTime.format(toPattern);
        } catch (DateTimeParseException e) {
            throw new ExecutionException("Could not format timestamp with timezone \"%s\" from pattern \"%s\" to pattern \"%s\""
                    .formatted(timestamp, fromPattern, toPattern), e);
        }
    }

    static String formatTimestamp(String timestamp, DateTimeFormatter fromPattern,
                                  DateTimeFormatter toPattern) throws ExecutionException {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(timestamp, fromPattern);
            return localDateTime.format(toPattern);
        } catch (DateTimeParseException e) {
            throw new ExecutionException("Could not format timestamp \"%s\" from pattern \"%s\" to pattern \"%s\""
                    .formatted(timestamp, fromPattern.toFormat(), toPattern), e);
        }
    }
}
