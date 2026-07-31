/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.insert;

/*The following prompt was used to generate this template

Prompt:

Design a generic, efficient INSERT stored procedure for PostgreSQL that accepts a single JSON parameter and returns a JSON result. The procedure should work for any table by substituting tokens based on JDBC metadata. The key requirements are:

Input Handling:

Accept a JSON parameter (p_data) that can be a single object or an array of objects.

If the input is not an array, wrap it in a JSON array.

Enforce a maximum of 1000 rows per batch.

Set-Based Insert Using json_to_recordset:

Use a set-based approach instead of row-by-row looping.

Leverage PostgreSQL’s json_to_recordset function to convert the JSON array into a record set.

In the FROM clause, provide an alias with a record type definition:

sql
Copy
FROM json_to_recordset(v_rows) AS x(${COLUMN_TYPES})
This alias defines the column names and their data types (e.g., col1 text, col2 uuid, created_at timestamptz, updated_at timestamptz), allowing PostgreSQL to cast JSON string values into the proper types.

Column Substitution:

Use tokens such as ${TABLE_NAME}, ${TABLE_IDENTIFIER}, ${COLUMN_NAMES}, ${COLUMN_SELECT_VALUES}, and ${COLUMN_TYPES}.

${TABLE_NAME} will be replaced with the target table’s name.

${TABLE_IDENTIFIER} will be replaced with the quoted SQL relation identifier.

${COLUMN_NAMES} is a comma-separated list of columns to insert.

${COLUMN_SELECT_VALUES} provides the expressions to extract values from the JSON record (using functions like COALESCE for default timestamps).

${COLUMN_TYPES} defines the record type for json_to_recordset (including column names and PostgreSQL data types).

Output:

Return a JSON object indicating success or error.

 */

public class Template {

    final String template = """
    CREATE OR REPLACE FUNCTION @x:scalar(text=TABLE_NAME)_insert(p_data JSON)
       RETURNS JSON AS $$
       DECLARE
           v_rows JSON;
           v_count INT;
       BEGIN
           -- Wrap input in an array if it's not already an array.
           IF json_typeof(p_data) <> 'array' THEN
               v_rows := json_build_array(p_data);
           ELSE
               v_rows := p_data;
           END IF;
          
           -- Check maximum number of rows.
           SELECT json_array_length(v_rows) INTO v_count;
           IF v_count > 1000 THEN
               RETURN json_build_object('status', 'error', 'message', 'Maximum 1000 rows allowed.');
           END IF;
          
           -- Perform set-based insert using json_to_recordset.
           INSERT INTO @x:scalar(text=TABLE_IDENTIFIER) (@x:scalar(text=COLUMN_NAMES))
           SELECT @x:scalar(text=COLUMN_SELECT_VALUES)
           FROM json_to_recordset(v_rows) AS x(@x:scalar(text=COLUMN_TYPES));
          
           RETURN json_build_object('status', 'success', 'message', 'Rows inserted successfully');
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
