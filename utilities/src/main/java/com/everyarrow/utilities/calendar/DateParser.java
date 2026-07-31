/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.calendar;

import org.apache.commons.lang3.time.DateUtils;
import java.text.ParseException;
import java.util.Date;

public class DateParser {

    private static final String[] PATTERNS = {
        "yyyy-MM-dd",
        "MM/dd/yyyy",
        "dd-MM-yyyy"
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
