-- Event Manager-owned PostgreSQL registry contract.
-- Safe to apply repeatedly. Application-specific DB functions may be added to
-- registry_functions separately; this file owns runtime surface reconciliation.

CREATE SCHEMA IF NOT EXISTS event_manager;

CREATE TABLE IF NOT EXISTS event_manager.registry_functions (
    service_type text PRIMARY KEY,
    database_name text NOT NULL,
    schema_name text NOT NULL,
    table_name text NOT NULL,
    function_name text NOT NULL,
    function_type text NOT NULL,
    selector text,
    selector_columns jsonb NOT NULL DEFAULT '[]'::jsonb,
    json_type text NOT NULL DEFAULT 'jsonb',
    http_method text,
    route_path text,
    annotations jsonb,
    description text,
    source text NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    function_exists boolean NOT NULL DEFAULT false,
    registry_entry_kind text NOT NULL DEFAULT 'db_function',
    handler_class text,
    target_method_name text,
    category text,
    plugin_id text,
    surface_id text,
    metadata_hash text,
    missing_from_surface boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_discovered_at timestamptz,
    last_seen_at timestamptz,
    deactivated_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Upgrade an existing registry table created before runtime surface
-- reconciliation was introduced. CREATE TABLE IF NOT EXISTS does not add
-- columns to an already-existing installation.
ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS deactivated_at timestamptz;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS registry_entry_kind text NOT NULL DEFAULT 'db_function';

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS handler_class text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS target_method_name text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS category text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS plugin_id text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS surface_id text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS metadata_hash text;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS missing_from_surface boolean NOT NULL DEFAULT false;

ALTER TABLE event_manager.registry_functions
    ADD COLUMN IF NOT EXISTS last_seen_at timestamptz;

CREATE INDEX IF NOT EXISTS registry_functions_database_schema_idx
    ON event_manager.registry_functions (database_name, schema_name);

CREATE INDEX IF NOT EXISTS registry_functions_route_idx
    ON event_manager.registry_functions (http_method, route_path)
    WHERE http_method IS NOT NULL AND route_path IS NOT NULL;

CREATE INDEX IF NOT EXISTS registry_functions_surface_kind_idx
    ON event_manager.registry_functions (surface_id, registry_entry_kind);

CREATE INDEX IF NOT EXISTS registry_functions_plugin_idx
    ON event_manager.registry_functions (plugin_id)
    WHERE plugin_id IS NOT NULL;

CREATE OR REPLACE FUNCTION event_manager.load_registry_surface(p_request jsonb)
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path = event_manager, public
AS $function$
    SELECT COALESCE(jsonb_agg(to_jsonb(registry_row) ORDER BY registry_row.service_type), '[]'::jsonb)
    FROM (
        SELECT *
        FROM event_manager.registry_functions
        WHERE is_active = true
          AND (
              NULLIF(p_request->>'surface_id', '') IS NULL
              OR NULLIF(surface_id, '') IS NULL
              OR surface_id = p_request->>'surface_id'
          )
          AND missing_from_surface = false
          AND (
              registry_entry_kind <> 'db_function'
              OR function_exists = true
          )
    ) AS registry_row;
$function$;

CREATE OR REPLACE FUNCTION event_manager.refresh_registry_surface(p_request jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = event_manager, public
AS $function$
DECLARE
    v_surface_id text := COALESCE(NULLIF(p_request->>'surface_id', ''), 'default');
    v_entries jsonb := CASE
        WHEN jsonb_typeof(p_request->'entries') = 'array' THEN p_request->'entries'
        ELSE '[]'::jsonb
    END;
    v_upserted_count integer := 0;
    v_missing_count integer := 0;
    v_missing_function_count integer := 0;
BEGIN
    WITH incoming AS (
        SELECT *
        FROM jsonb_to_recordset(v_entries) AS entry(
            service_type text,
            registry_entry_kind text,
            database_name text,
            schema_name text,
            table_name text,
            function_name text,
            function_type text,
            selector text,
            selector_columns jsonb,
            json_type text,
            http_method text,
            route_path text,
            annotations jsonb,
            description text,
            source text,
            function_exists boolean,
            handler_class text,
            target_method_name text,
            category text,
            plugin_id text,
            surface_id text,
            metadata_hash text
        )
        WHERE NULLIF(service_type, '') IS NOT NULL
    ),
    upserted AS (
        INSERT INTO event_manager.registry_functions AS existing_registry_function (
            service_type, database_name, schema_name, table_name, function_name,
            function_type, selector, selector_columns, json_type, http_method,
            route_path, annotations, description, source, is_active,
            function_exists, registry_entry_kind, handler_class, target_method_name,
            category, plugin_id, surface_id, metadata_hash, missing_from_surface,
            last_discovered_at, last_seen_at, deactivated_at, updated_at
        )
        SELECT
            service_type,
            COALESCE(NULLIF(database_name, ''), 'EveryArrow'),
            COALESCE(NULLIF(schema_name, ''), 'event_manager'),
            COALESCE(NULLIF(table_name, ''), 'registry_functions'),
            COALESCE(NULLIF(function_name, ''), NULLIF(target_method_name, ''), service_type),
            COALESCE(NULLIF(function_type, ''), COALESCE(NULLIF(registry_entry_kind, ''), 'db_function')),
            selector,
            COALESCE(selector_columns, '[]'::jsonb),
            COALESCE(NULLIF(json_type, ''), 'jsonb'),
            NULLIF(http_method, ''),
            NULLIF(route_path, ''),
            annotations,
            NULLIF(description, ''),
            COALESCE(NULLIF(source, ''), 'runtime'),
            true,
            COALESCE(function_exists, false),
            COALESCE(NULLIF(registry_entry_kind, ''), 'db_function'),
            NULLIF(handler_class, ''),
            NULLIF(target_method_name, ''),
            NULLIF(category, ''),
            NULLIF(plugin_id, ''),
            COALESCE(NULLIF(surface_id, ''), v_surface_id),
            NULLIF(metadata_hash, ''),
            false,
            now(),
            now(),
            NULL,
            now()
        FROM incoming
        ON CONFLICT (service_type) DO UPDATE
        SET database_name = EXCLUDED.database_name,
            schema_name = EXCLUDED.schema_name,
            table_name = EXCLUDED.table_name,
            function_name = EXCLUDED.function_name,
            function_type = EXCLUDED.function_type,
            selector = EXCLUDED.selector,
            selector_columns = EXCLUDED.selector_columns,
            json_type = EXCLUDED.json_type,
            http_method = EXCLUDED.http_method,
            route_path = EXCLUDED.route_path,
            annotations = EXCLUDED.annotations,
            description = EXCLUDED.description,
            source = EXCLUDED.source,
            is_active = true,
            function_exists = EXCLUDED.function_exists,
            registry_entry_kind = EXCLUDED.registry_entry_kind,
            handler_class = EXCLUDED.handler_class,
            target_method_name = EXCLUDED.target_method_name,
            category = EXCLUDED.category,
            plugin_id = EXCLUDED.plugin_id,
            surface_id = EXCLUDED.surface_id,
            metadata_hash = EXCLUDED.metadata_hash,
            missing_from_surface = false,
            last_discovered_at = now(),
            last_seen_at = now(),
            deactivated_at = NULL,
            updated_at = now()
        -- A server-owned DB function remains the durable fallback when a live
        -- local annotation declares the same service_type. Event Manager keeps
        -- both runtime candidates and selects the local Java handler without
        -- destroying the server implementation or its provenance.
        WHERE NOT (
            existing_registry_function.registry_entry_kind = 'db_function'
            AND EXCLUDED.registry_entry_kind IN ('java_handler', 'annotation_metadata')
        )
        RETURNING 1
    )
    SELECT count(*) INTO v_upserted_count FROM upserted;

    -- Plugin metadata cannot assert that a local PostgreSQL function exists.
    -- Reconcile the executable surface against the database catalog on every
    -- refresh. Remote database registries remain the responsibility of their
    -- owning connection and validation process.
    UPDATE event_manager.registry_functions AS registry_function
    SET function_exists = EXISTS (
            SELECT 1
            FROM pg_catalog.pg_proc AS procedure
            JOIN pg_catalog.pg_namespace AS namespace
              ON namespace.oid = procedure.pronamespace
            WHERE namespace.nspname = registry_function.schema_name
              AND procedure.proname = registry_function.function_name
              AND procedure.pronargs = 1
              AND procedure.proargtypes[0] IN (
                  'json'::regtype,
                  'jsonb'::regtype
              )
        ),
        updated_at = now()
    WHERE registry_function.registry_entry_kind = 'db_function'
      AND registry_function.database_name = current_database()
      AND registry_function.surface_id = v_surface_id;

    WITH current_services AS (
        SELECT NULLIF(entry->>'service_type', '') AS service_type
        FROM jsonb_array_elements(v_entries) AS entry
        WHERE NULLIF(entry->>'service_type', '') IS NOT NULL
    ),
    marked_missing AS (
        UPDATE event_manager.registry_functions AS registry_function
        SET missing_from_surface = true,
            is_active = false,
            deactivated_at = now(),
            updated_at = now()
        WHERE registry_function.surface_id = v_surface_id
          AND registry_function.missing_from_surface = false
          AND NOT EXISTS (
              SELECT 1
              FROM current_services
              WHERE current_services.service_type = registry_function.service_type
          )
        RETURNING 1
    )
    SELECT count(*) INTO v_missing_count FROM marked_missing;

    SELECT count(*)
    INTO v_missing_function_count
    FROM event_manager.registry_functions AS registry_function
    WHERE registry_function.registry_entry_kind = 'db_function'
      AND registry_function.database_name = current_database()
      AND registry_function.surface_id = v_surface_id
      AND registry_function.is_active = true
      AND registry_function.missing_from_surface = false
      AND registry_function.function_exists = false;

    RETURN jsonb_build_object(
        'status', 'success',
        'refreshed_count', v_upserted_count,
        'missing_count', v_missing_count,
        'missing_function_count', v_missing_function_count
    );
END;
$function$;
