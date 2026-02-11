#!/bin/sh

# Build + run helper for the examples module.
#
# Usage:
#   ./run.sh ClassName [args...]
#   ./run.sh --agent ClassName "agentArgsString"
#
# Examples:
#   ./run.sh QuickStart greet --name=World --count=1
#   ./run.sh --agent AgentCli "start,interval=1ms"

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
EXAMPLES_DIR="$SCRIPT_DIR"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
JAR="$EXAMPLES_DIR/target/femtocli-examples.jar"

usage() {
  cat <<'EOF'
Usage:
  ./run.sh ClassName [args...]
  ./run.sh --agent ClassName "agentArgsString"

Runs an example from the examples module via examples/target/femtocli-examples.jar.
If the jar doesn't exist yet, it will be built first.

ClassName can be fully-qualified (me.bechberger.femtocli.examples.QuickStart)
or short (QuickStart), in which case the examples package is added automatically.
EOF
}

MODE="normal"
if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "${1:-}" = "--agent" ]; then
  MODE="agent"
  shift
fi

if [ $# -lt 1 ]; then
  usage >&2
  exit 2
fi

CLS="$1"
shift

if echo "$CLS" | grep -q '\.'; then
  FQCN="$CLS"
else
  FQCN="me.bechberger.femtocli.examples.$CLS"
fi

# Build jar if needed
if [ ! -f "$JAR" ]; then
  (cd "$EXAMPLES_DIR" && mvn -q package)
fi

if [ "$MODE" = "agent" ]; then
  if [ $# -ne 1 ]; then
    echo "ERROR: --agent mode expects a single agent-args string" >&2
    usage >&2
    exit 2
  fi
  AGENT_ARGS="$1"
  # AgentCli detects a single argv element and uses agent mode.
  exec java -cp "$JAR" "$FQCN" "$AGENT_ARGS"
fi

exec java -cp "$JAR" "$FQCN" "$@"