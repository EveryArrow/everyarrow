/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

public class TemplateSelectByApplication {

    static final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=FUNCTION_IDENTIFIER)(params JSON)
       RETURNS JSON AS $$
    DECLARE
       result JSON;
       p_application_id @x:scalar(text=APPLICATION_KEY_TYPE);
    BEGIN
       p_application_id := (params->>'application_id')::@x:scalar(text=APPLICATION_KEY_TYPE);
       
       SELECT json_agg(row_to_json(t)) INTO result
       FROM (
           SELECT @x:scalar(text=COLUMN_NAMES)
           FROM @x:scalar(text=TABLE_IDENTIFIER)
           WHERE @x:scalar(text=TABLE_IDENTIFIER)."application_id" = p_application_id
       ) t;
       RETURN result;
    EXCEPTION
       WHEN OTHERS THEN
            RETURN json_build_object('status','error','message',SQLERRM);
    END;
    $$ LANGUAGE plpgsql;
    """;

    public static String getTemplate() {
        return new TemplateSelectByApplication().template;
    }
}
