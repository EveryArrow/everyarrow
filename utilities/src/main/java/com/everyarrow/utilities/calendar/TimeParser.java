/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.calendar;

import org.apache.commons.lang3.time.DateUtils;
import java.text.ParseException;
import java.util.Date;

public class TimeParser {
    private static final String[] PATTERNS = {
            "HH:mm:ss",
            "HH:mm:ss.SSS",
            "HH:mm",
            "hh:mm:ss a",
            "hh:mm a",
            "HH:mm:ssXXX",
            "HH:mm:ss.SSSXXX",

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
