/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.delete;

public class Template {

    final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=TABLE_NAME)_delete(p_data JSON)
       RETURNS JSON AS $$
       DECLARE
           v_data JSON;
       BEGIN
           -- Ensure the input is a JSON object
           IF json_typeof(p_data) <> 'object' THEN
               RETURN json_build_object('status', 'error', 'message', 'Input must be a JSON object.');
           END IF;
           
           @x:scalar(text=DELETE_STATEMENT)
           
           RETURN json_build_object('status', 'success', 'message', '@x:scalar(text=SUCCESS_MESSAGE)');
       EXCEPTION
           WHEN OTHERS THEN
                RETURN json_build_object('status', 'error', 'message', SQLERRM);
       END;
       $$ LANGUAGE plpgsql;
    """;

    public static String getTemplate() {
        return new Template().template;
    }
}
