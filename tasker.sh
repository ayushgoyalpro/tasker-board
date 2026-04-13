#!/bin/bash

IMAGE="tasker"
CONTAINER="tasker"
PORT=8080
VOLUME="tasker-data"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[tasker]${NC} $1"; }
ok()   { echo -e "${GREEN}[tasker]${NC} $1"; }
warn() { echo -e "${YELLOW}[tasker]${NC} $1"; }
err()  { echo -e "${RED}[tasker]${NC} $1"; }

is_running() {
    docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER}$"
}

start() {
    if is_running; then
        warn "Tasker is already running on http://localhost:${PORT}"
        exit 0
    fi

    # Remove stopped container if it exists
    docker rm "${CONTAINER}" 2>/dev/null

    # Build if image doesn't exist
    if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
        log "Building Tasker image..."
        docker build --progress=plain -t "${IMAGE}" . || { err "Build failed"; exit 1; }
    fi

    log "Starting Tasker..."
    docker run -d \
        --name "${CONTAINER}" \
        -p "${PORT}:8080" \
        -v "${VOLUME}:/data" \
        "${IMAGE}" >/dev/null

    ok "Tasker is running on http://localhost:${PORT}"
}

stop() {
    if ! is_running; then
        warn "Tasker is not running"
        exit 0
    fi

    log "Stopping Tasker..."
    docker stop "${CONTAINER}" >/dev/null
    docker rm "${CONTAINER}" >/dev/null
    ok "Tasker stopped"
}

rebuild() {
    stop 2>/dev/null
    log "Rebuilding Tasker image..."
    docker build --progress=plain -t "${IMAGE}" . || { err "Build failed"; exit 1; }
    start
}

case "${1}" in
    start)   start ;;
    stop)    stop ;;
    rebuild) rebuild ;;
    *)
        echo "Usage: ./tasker.sh {start|stop|rebuild}"
        echo ""
        echo "  start    Build (if needed) and start Tasker"
        echo "  stop     Stop Tasker"
        echo "  rebuild  Rebuild image and restart"
        exit 1
        ;;
esac
