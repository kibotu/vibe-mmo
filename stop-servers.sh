#!/bin/bash
# Stop all Ragnarok Online Servers

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${YELLOW}Stopping all Ragnarok Online Servers...${NC}"
echo ""

# Stop Gradle daemons first to prevent file locking issues
echo -e "${CYAN}Stopping Gradle daemons...${NC}"
"$SCRIPT_DIR/gradlew" --stop 2>/dev/null || true

# Kill processes on server ports
for port in 28080 28081 28082; do
    pid=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo -e "${CYAN}Stopping process on port $port (PID: $pid)...${NC}"
        kill -TERM $pid 2>/dev/null || true
        sleep 1
        kill -9 $pid 2>/dev/null || true
    fi
done

# Kill any Java processes running the servers
pkill -f "account-server" 2>/dev/null || true
pkill -f "character-server" 2>/dev/null || true
pkill -f "zone-server" 2>/dev/null || true

echo ""
echo -e "${GREEN}All servers stopped.${NC}"
