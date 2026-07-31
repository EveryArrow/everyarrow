/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

public class TemplateSelectOne {

    final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=FUNCTION_IDENTIFIER)(p_data JSON)
       RETURNS JSON AS $$
    DECLARE
       result JSON;
    BEGIN
       SELECT row_to_json(t) INTO result
       FROM (
           SELECT @x:scalar(text=COLUMN_NAMES)
           FROM @x:scalar(text=TABLE_IDENTIFIER)
           WHERE @x:scalar(text=WHERE_CLAUSE)
       ) t;
       RETURN result;
    EXCEPTION
       WHEN OTHERS THEN
            RETURN json_build_object('status', 'error', 'message', SQLERRM);
    END;
    $$ LANGUAGE plpgsql;
    """;

    public static String getTemplate() {
        return new TemplateSelectOne().template;
    }
}
