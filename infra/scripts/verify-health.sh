#!/bin/bash
#
# Health Check Verification Script
# Verifies that all health endpoints are responding correctly after deployment.
#
# Usage:
#   ./verify-health.sh [BASE_URL]
#
# Arguments:
#   BASE_URL  - Base URL of the API (default: http://localhost:8080)
#
# Exit codes:
#   0 - All health checks passed
#   1 - One or more health checks failed
#

# Use set -u -o pipefail instead of set -e to allow check_endpoint* to fail
# without immediately terminating the script. This allows FAILED aggregation
# and summary output to work correctly.
set -u -o pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${1:-http://localhost:8080}"
TIMEOUT=10
MAX_RETRIES=30
RETRY_DELAY=2

# Track failures
FAILED=0

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    FAILED=1
}

# Check a single endpoint
check_endpoint() {
    local endpoint="$1"
    local expected_status="${2:-200}"
    local description="$3"
    
    local url="${BASE_URL}${endpoint}"
    local response
    local status
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "000")
    
    if [ "$response" == "$expected_status" ]; then
        log_success "$description: $endpoint (HTTP $response)"
        return 0
    else
        log_fail "$description: $endpoint (Expected HTTP $expected_status, got $response)"
        return 1
    fi
}

# Check endpoint with JSON response validation
check_endpoint_json() {
    local endpoint="$1"
    local json_path="$2"
    local expected_value="$3"
    local description="$4"
    
    local url="${BASE_URL}${endpoint}"
    local response
    local status
    local value
    
    response=$(curl -s --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "")
    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "000")
    
    if [ "$status" != "200" ]; then
        log_fail "$description: $endpoint (HTTP $status)"
        return 1
    fi
    
    # Check if jq is available
    if command -v jq &> /dev/null; then
        value=$(echo "$response" | jq -r "$json_path" 2>/dev/null || echo "")
        if [ "$value" == "$expected_value" ]; then
            log_success "$description: $endpoint ($json_path = $value)"
            return 0
        else
            log_fail "$description: $endpoint (Expected $json_path = $expected_value, got $value)"
            return 1
        fi
    else
        # Fallback: just check HTTP status
        log_success "$description: $endpoint (HTTP $status, jq not available for JSON validation)"
        return 0
    fi
}

# Wait for service to be ready
wait_for_service() {
    local retries=0
    
    log_info "Waiting for service at $BASE_URL to be ready..."
    
    while [ $retries -lt $MAX_RETRIES ]; do
        if curl -s --max-time 2 "${BASE_URL}/api/v1/health" > /dev/null 2>&1; then
            log_success "Service is ready!"
            return 0
        fi
        
        retries=$((retries + 1))
        log_info "Attempt $retries/$MAX_RETRIES - Service not ready, waiting ${RETRY_DELAY}s..."
        sleep $RETRY_DELAY
    done
    
    log_error "Service did not become ready within $((MAX_RETRIES * RETRY_DELAY)) seconds"
    return 1
}

# Print header
echo "========================================"
echo "  Miometry Health Check Verification"
echo "========================================"
echo ""
echo "Target: $BASE_URL"
echo "Timeout: ${TIMEOUT}s per request"
echo ""

# Wait for service if --wait flag is provided
if [ "$2" == "--wait" ]; then
    wait_for_service || exit 1
    echo ""
fi

echo "--- Health Endpoints ---"

# Primary health check
check_endpoint_json "/api/v1/health" ".status" "ok" "API Health Check"

# Readiness probe (for Kubernetes)
check_endpoint "/ready" "200" "Readiness Probe" || true

# Spring Actuator health (if enabled)
check_endpoint "/actuator/health" "200" "Actuator Health" || true

echo ""
echo "--- API Endpoints Smoke Test ---"

# Check API documentation is accessible
check_endpoint "/docs" "302" "API Docs Redirect" || \
check_endpoint "/static/api-docs.html" "200" "API Docs Page" || true

# Check OpenAPI spec is accessible
check_endpoint "/api-docs/openapi.yaml" "200" "OpenAPI Spec" || true

echo ""
echo "--- Summary ---"

if [ $FAILED -eq 0 ]; then
    log_success "All health checks passed!"
    echo ""
    exit 0
else
    log_error "One or more health checks failed!"
    echo ""
    exit 1
fi
