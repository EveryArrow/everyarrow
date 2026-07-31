/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

public class TemplateSelectByMany {

    final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=FUNCTION_IDENTIFIER)(p_fk @x:scalar(text=FK_TYPE))
       RETURNS JSON AS $$
    DECLARE
       result JSON;
    BEGIN
       SELECT json_agg(json_agg(t)) INTO result
       FROM (
           SELECT @x:scalar(text=COLUMN_NAMES)
           FROM @x:scalar(text=TABLE_IDENTIFIER)
           WHERE @x:scalar(text=FK_IDENTIFIER) = p_fk
       ) t;
       RETURN result;
    EXCEPTION
       WHEN OTHERS THEN
            RETURN json_build_object('status', 'error', 'message', SQLERRM);
    END;
    $$ LANGUAGE plpgsql;
    """;

    public static String getTemplate() {
        return new TemplateSelectByMany().template;
    }
}
