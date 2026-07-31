/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.calendar;

import org.apache.commons.lang3.time.DateUtils;
import java.util.Date;
import java.text.ParseException;

public class DateTimeParser {
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    };

    public static Date parse(String str) throws ParseException {
        return parse(str, PATTERNS);
    }

    public static Date parse(String str, String pattern) throws ParseException {
        return parse(str, new String[]{pattern});
    }

    public static Date parse(String str, String[] patterns) throws ParseException {
        return DateUtils.parseDate(str,  patterns);
    }
}
