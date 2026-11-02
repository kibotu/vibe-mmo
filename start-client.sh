#!/bin/bash
# Start Ragnarok Online Client

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

# Cleanup function
stop_client() {
    echo ""
    echo -e "${YELLOW}Cleaning up client processes...${NC}"
    
    # Kill any Java processes running game-client
    pkill -f "game-client" 2>/dev/null || true
}

# Handle Ctrl+C and other signals
trap 'echo ""; echo -e "${YELLOW}Interrupted!${NC}"; stop_client; exit 130' INT TERM

echo -e "${GREEN}Starting Ragnarok Online Client...${NC}"
echo -e "${GRAY}Note: Multiple client instances are allowed.${NC}"
echo "Press Ctrl+C to stop the client."
echo ""

# Start Game Client - run directly to allow Ctrl+C to work
cd "$SCRIPT_DIR"
./gradlew ":game-client:run"

echo ""
echo -e "${GREEN}Client has exited.${NC}"
