#!/bin/bash
# Start Ragnarok Online Servers with cleanup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

# Array to store process PIDs
declare -a SERVER_PIDS=()

# Flag to prevent multiple cleanup executions
CLEANUP_EXECUTED=false

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Cleanup function
stop_servers() {
    if [ "$CLEANUP_EXECUTED" = true ]; then
        return
    fi
    CLEANUP_EXECUTED=true
    
    echo ""
    echo -e "${YELLOW}Stopping all servers...${NC}"
    
    # Stop tracked processes
    for pid in "${SERVER_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            echo -e "${CYAN}Stopping server process (PID: $pid)...${NC}"
            kill -TERM "$pid" 2>/dev/null || true
            # Wait a bit for graceful shutdown
            sleep 1
            # Force kill if still running
            kill -9 "$pid" 2>/dev/null || true
        fi
    done
    
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
    
    echo -e "${GREEN}All servers stopped.${NC}"
}

# Handle Ctrl+C and other signals
trap 'echo ""; echo -e "${YELLOW}Interrupted!${NC}"; stop_servers; exit 130' INT TERM

# Function to check if a server is already running on a port
check_server_running() {
    local port=$1
    local server_name=$2
    local pid=$(lsof -ti:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo -e "${YELLOW}$server_name is already running on port $port (PID: $pid)${NC}"
        return 0
    fi
    return 1
}

# Function to start a server in background
start_server() {
    local name=$1
    local port=$2
    local module=$3
    
    echo -e "${CYAN}Starting $name on port $port...${NC}"
    
    # Start the server in background and capture PID
    cd "$SCRIPT_DIR"
    ./gradlew ":$module:run" --no-daemon &
    local pid=$!
    SERVER_PIDS+=($pid)
    
    echo -e "${GRAY}$name starting with PID: $pid${NC}"
}

echo -e "${GREEN}Starting Ragnarok Online Servers...${NC}"
echo "Press Ctrl+C to stop all servers and exit."
echo ""

# Check for existing account and character servers (only ONE allowed)
echo -e "${YELLOW}Checking for existing servers...${NC}"

if check_server_running 28080 "Account Server"; then
    echo -e "${RED}ERROR: Account Server is already running. Only one instance is allowed.${NC}"
    echo -e "${RED}Please stop the existing instance before starting a new one.${NC}"
    exit 1
fi

if check_server_running 28081 "Character Server"; then
    echo -e "${RED}ERROR: Character Server is already running. Only one instance is allowed.${NC}"
    echo -e "${RED}Please stop the existing instance before starting a new one.${NC}"
    exit 1
fi

echo ""

# Stop all Gradle daemons to prevent conflicts
echo -e "${YELLOW}Stopping existing Gradle daemons to prevent conflicts...${NC}"
"$SCRIPT_DIR/gradlew" --stop 2>/dev/null || true
sleep 2

# Pre-build the shared module to avoid concurrent build conflicts
echo -e "${YELLOW}Pre-building shared module...${NC}"
"$SCRIPT_DIR/gradlew" :shared:build --no-daemon 2>/dev/null || echo -e "${YELLOW}Warning: Shared module build had issues, but continuing...${NC}"
echo ""

# Start Account Server (Port 28080)
start_server "Account Server" 28080 "account-server"

# Wait for account server to build and start
echo -e "${GRAY}Waiting for Account Server to build and start...${NC}"
sleep 10

# Start Character Server (Port 28081)
start_server "Character Server" 28081 "character-server"

# Wait for character server to build and start
echo -e "${GRAY}Waiting for Character Server to build and start...${NC}"
sleep 10

# Start Zone Server (Port 28082)
echo -e "${GRAY}Note: You can start additional zone servers with different ports using start-zone-server.sh${NC}"
start_server "Zone Server" 28082 "zone-server"

echo ""
echo -e "${GREEN}Servers are starting in background.${NC}"
echo -e "${GRAY}- Account Server: Port 28080 (single instance)${NC}"
echo -e "${GRAY}- Character Server: Port 28081 (single instance)${NC}"
echo -e "${GRAY}- Zone Server: Port 28082 (multiple instances allowed)${NC}"
echo ""
echo "Press Ctrl+C to stop all servers and exit."
echo ""

# Wait for user interrupt
wait
