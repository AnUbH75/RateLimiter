#!/bin/bash
echo "=== Rate Limiter Quick Test ==="

# First, flush Redis to reset state
# redis-cli FLUSHALL

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

success_count=0
blocked_count=0

echo "Making 12 rapid requests (capacity is 10)..."

for i in {1..12}; do
    http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/test)
    if [ "$http_code" = "200" ]; then
        echo -e "  Request $i: ${GREEN}✓ Allowed (200)${NC}"
        ((success_count++))
    elif [ "$http_code" = "429" ]; then
        echo -e "  Request $i: ${RED}✗ Blocked (429)${NC}"
        ((blocked_count++))
    fi
done

echo ""
echo "Allowed: $success_count | Blocked: $blocked_count"