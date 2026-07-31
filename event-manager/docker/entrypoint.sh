#!/usr/bin/env bash
set -euo pipefail

ENGINE_HOME=${ENGINE_HOME:-/opt/everyarrow/event-manager}
ENGINE_JAR=${ENGINE_JAR:-"$ENGINE_HOME/event-engine.jar"}
PLUGIN_ROOT=${EA_PLUGIN_ROOT:-"$ENGINE_HOME/plugins"}
PLUGIN_JAR_SOURCE=${EA_PLUGIN_JAR_SOURCE:-"$ENGINE_HOME/plugin-jars"}
DEFAULT_ENV_FILE=${EA_ENV_FILE:-"$ENGINE_HOME/config/ea.env"}
LOG_ROOT=${EA_LOG_ROOT:-/var/log/everyarrow}
PLUGIN_SPEC=${EA_PLUGINS:-}
DRY_RUN=0

PASS_ARGS=()

usage() {
    cat <<'EOF'
Usage: everyarrow-event-manager [options] [-- engine-args]

Options:
  --plugins LIST       Comma-separated plugin names, jar paths, directory paths, or "all".
  --plugin VALUE       Add one plugin name, jar path, or directory path.
  --plugin-root DIR    Runtime EA_PLUGIN_ROOT directory. Defaults to /opt/everyarrow/event-manager/plugins.
  --plugin-jars DIR    Directory containing staged plugin jars. Defaults to /opt/everyarrow/event-manager/plugin-jars.
  --env-file FILE      Base ea.env file. Defaults to /opt/everyarrow/event-manager/config/ea.env.
  --dry-run            Stage plugins and print launch settings without starting Java.

Environment alternatives:
  EA_PLUGINS, EA_PLUGIN_ROOT, EA_PLUGIN_JAR_SOURCE, EA_ENV_FILE, JAVA_OPTS.
EOF
}

append_plugin_spec() {
    local value=$1
    if [[ -z "$PLUGIN_SPEC" ]]; then
        PLUGIN_SPEC=$value
    else
        PLUGIN_SPEC="$PLUGIN_SPEC,$value"
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --plugins)
            [[ $# -ge 2 ]] || { echo "--plugins requires a value" >&2; exit 64; }
            append_plugin_spec "$2"
            shift 2
            ;;
        --plugin)
            [[ $# -ge 2 ]] || { echo "--plugin requires a value" >&2; exit 64; }
            append_plugin_spec "$2"
            shift 2
            ;;
        --plugin-root)
            [[ $# -ge 2 ]] || { echo "--plugin-root requires a value" >&2; exit 64; }
            PLUGIN_ROOT=$2
            shift 2
            ;;
        --plugin-jars)
            [[ $# -ge 2 ]] || { echo "--plugin-jars requires a value" >&2; exit 64; }
            PLUGIN_JAR_SOURCE=$2
            shift 2
            ;;
        --env-file)
            [[ $# -ge 2 ]] || { echo "--env-file requires a value" >&2; exit 64; }
            DEFAULT_ENV_FILE=$2
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        --)
            shift
            PASS_ARGS+=("$@")
            break
            ;;
        *)
            PASS_ARGS+=("$1")
            shift
            ;;
    esac
done

export EA_PLUGIN_ROOT="$PLUGIN_ROOT"
export EA_LOG_ROOT="$LOG_ROOT"
mkdir -p "$EA_PLUGIN_ROOT"
mkdir -p "$EA_LOG_ROOT/event-manager"

if [[ -z "${EA_ENV_RESOURCE:-}" && -f "$DEFAULT_ENV_FILE" ]]; then
    export EA_ENV_RESOURCE="$DEFAULT_ENV_FILE"
fi

plugin_id_from_path() {
    local path=$1
    local parent
    parent=$(dirname "$path")
    if [[ "$parent" != "$PLUGIN_JAR_SOURCE" && "$parent" == "$PLUGIN_JAR_SOURCE/"* ]]; then
        basename "$parent"
        return
    fi

    local name
    name=$(basename "$path")
    name=${name%.jar}
    name=$(echo "$name" | sed -E 's/-[0-9]+(\.[0-9]+)*([.-].*)?$//')
    case "$name" in
        security-plugin) name="security" ;;
        perch-plugin) name="perch" ;;
    esac
    echo "$name"
}

stage_plugin_jar() {
    local jar=$1
    [[ -f "$jar" ]] || { echo "Plugin jar not found: $jar" >&2; exit 66; }
    local plugin_id
    plugin_id=$(plugin_id_from_path "$jar")
    local target_dir="$EA_PLUGIN_ROOT/$plugin_id"
    mkdir -p "$target_dir"
    cp "$jar" "$target_dir/"
    echo "[event-manager] staged plugin jar: $jar -> $target_dir"
}

stage_plugin_directory() {
    local directory=$1
    [[ -d "$directory" ]] || { echo "Plugin directory not found: $directory" >&2; exit 66; }
    local plugin_id
    plugin_id=$(basename "$directory")
    rm -rf "$EA_PLUGIN_ROOT/$plugin_id"
    mkdir -p "$EA_PLUGIN_ROOT"
    cp -R "$directory" "$EA_PLUGIN_ROOT/$plugin_id"
    echo "[event-manager] staged plugin directory: $directory -> $EA_PLUGIN_ROOT/$plugin_id"
}

resolve_plugin_name() {
    local name=$1
    local jar
    jar=$(find "$PLUGIN_JAR_SOURCE" -maxdepth 2 -type f -name "$name*.jar" 2>/dev/null | sort | tail -n 1 || true)
    if [[ -z "$jar" ]]; then
        echo "Plugin '$name' was not found under $PLUGIN_JAR_SOURCE" >&2
        exit 66
    fi
    stage_plugin_jar "$jar"
}

stage_plugin_value() {
    local value=$1
    value=$(echo "$value" | xargs)
    [[ -n "$value" ]] || return 0

    if [[ "$value" == "all" ]]; then
        local found=0
        while IFS= read -r jar; do
            found=1
            stage_plugin_jar "$jar"
        done < <(find "$PLUGIN_JAR_SOURCE" -maxdepth 2 -type f -name "*.jar" 2>/dev/null | sort)
        [[ "$found" -eq 1 ]] || echo "[event-manager] no plugin jars found under $PLUGIN_JAR_SOURCE"
        return 0
    fi

    if [[ -f "$value" ]]; then
        stage_plugin_jar "$value"
        return 0
    fi

    if [[ -d "$value" ]]; then
        stage_plugin_directory "$value"
        return 0
    fi

    resolve_plugin_name "$value"
}

if [[ -n "$PLUGIN_SPEC" ]]; then
    IFS=',' read -ra PLUGINS <<< "$PLUGIN_SPEC"
    for plugin in "${PLUGINS[@]}"; do
        stage_plugin_value "$plugin"
    done
fi

INTEGRITY_MANIFEST_SOURCE=${EA_PLUGIN_INTEGRITY_SOURCE:-"$PLUGIN_JAR_SOURCE/plugin-integrity.json"}
INTEGRITY_MANIFEST_TARGET=${EA_PLUGIN_INTEGRITY_MANIFEST:-"$EA_PLUGIN_ROOT/plugin-integrity.json"}
if [[ -f "$INTEGRITY_MANIFEST_SOURCE" && "$INTEGRITY_MANIFEST_SOURCE" != "$INTEGRITY_MANIFEST_TARGET" ]]; then
    mkdir -p "$(dirname "$INTEGRITY_MANIFEST_TARGET")"
    cp "$INTEGRITY_MANIFEST_SOURCE" "$INTEGRITY_MANIFEST_TARGET"
    echo "[event-manager] staged plugin integrity manifest: $INTEGRITY_MANIFEST_SOURCE -> $INTEGRITY_MANIFEST_TARGET"
fi

JAVA_ARGS=()
if [[ -n "${JAVA_OPTS:-}" ]]; then
    # Intentional shell-like splitting for conventional JAVA_OPTS usage.
    read -r -a JAVA_ARGS <<< "$JAVA_OPTS"
fi

echo "[event-manager] EA_PLUGIN_ROOT=$EA_PLUGIN_ROOT"
echo "[event-manager] EA_LOG_ROOT=$EA_LOG_ROOT (component: $EA_LOG_ROOT/event-manager)"
echo "[event-manager] EA_ENV_RESOURCE=${EA_ENV_RESOURCE:-<process environment only>}"
if [[ "$DRY_RUN" -eq 1 ]]; then
    find "$EA_PLUGIN_ROOT" -maxdepth 2 -type f -name "*.jar" -printf "[event-manager] plugin: %P\n" | sort
    exit 0
fi

exec java "${JAVA_ARGS[@]}" -jar "$ENGINE_JAR" "${PASS_ARGS[@]}"
