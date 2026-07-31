/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

public class TemplateSelectByOtherFK {

    static final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=FUNCTION_IDENTIFIER)(params JSON)
       RETURNS JSON AS $$
    DECLARE
       result JSON;
       p_fk @x:scalar(text=FK_TYPE);
       fk_name TEXT := @x:scalar(text=FK_JSON_KEY);
    BEGIN
       p_fk := (params->>fk_name)::@x:scalar(text=FK_TYPE);
       
       SELECT json_agg(row_to_json(t)) INTO result
       FROM (
           SELECT @x:scalar(text=COLUMN_NAMES)
           FROM @x:scalar(text=TABLE_IDENTIFIER)
           WHERE @x:scalar(text=TABLE_IDENTIFIER).@x:scalar(text=FK_IDENTIFIER) = p_fk
       ) t;
       RETURN result;
    EXCEPTION
       WHEN OTHERS THEN
            RETURN json_build_object('status','error','message',SQLERRM);
    END;
    $$ LANGUAGE plpgsql;
    """;

    public static String getTemplate() {
        return new TemplateSelectByOtherFK().template;
    }
}
