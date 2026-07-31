/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.update;

public class Template {

    final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=TABLE_NAME)_update(p_data JSON)
       RETURNS JSON AS $$
       DECLARE
           v_data JSON;
           v_count INT;
       BEGIN
           IF json_typeof(p_data) <> 'array' THEN
               v_data := json_build_array(p_data);
           ELSE
               v_data := p_data;
           END IF;
           
           SELECT json_array_length(v_data) INTO v_count;
           IF v_count > 10 THEN
               RETURN json_build_object('status', 'error', 'message', 'Maximum 10 rows allowed.');
           END IF;
           
           @x:scalar(text=UPDATE_STATEMENT)
           
           @x:scalar(text=INSERT_STATEMENT)
           
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
