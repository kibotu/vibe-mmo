#!/bin/bash
# Start an additional Zone Server instance

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default port
PORT=${1:-28082}

# Cleanup function
stop_zone_server() {
    echo ""
    echo -e "${YELLOW}Stopping zone server...${NC}"
    
    # Kill process on the specific port
    pid=$(lsof -ti:$PORT 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo -e "${CYAN}Stopping process on port $PORT (PID: $pid)...${NC}"
        kill -TERM $pid 2>/dev/null || true
        sleep 1
        kill -9 $pid 2>/dev/null || true
    fi
}

# Handle Ctrl+C and other signals
trap 'echo ""; echo -e "${YELLOW}Interrupted!${NC}"; stop_zone_server; exit 130' INT TERM

echo -e "${GREEN}Starting Zone Server on port $PORT...${NC}"
echo -e "${GRAY}Usage: $0 [port]${NC}"
echo -e "${GRAY}Default port is 28082${NC}"
echo "Press Ctrl+C to stop the server."
echo ""

# Check if port is already in use
pid=$(lsof -ti:$PORT 2>/dev/null || true)
if [ -n "$pid" ]; then
    echo -e "${YELLOW}Warning: Port $PORT is already in use (PID: $pid)${NC}"
    echo -e "${YELLOW}You may want to use a different port.${NC}"
    echo ""
fi

# Start Zone Server with custom port if specified
cd "$SCRIPT_DIR"
if [ "$PORT" != "28082" ]; then
    ZONE_PORT=$PORT ./gradlew ":zone-server:run" --no-daemon
else
    ./gradlew ":zone-server:run" --no-daemon
fi

echo ""
echo -e "${GREEN}Zone server has exited.${NC}"
