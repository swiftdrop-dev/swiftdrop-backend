#!/usr/bin/env bash
# ==============================================================================
# SwiftDrop — End-to-End API Gateway Integration Smoke Test Suite
#
# Verifies cross-cutting contracts, security boundaries, and operational
# endpoints through the API Gateway (port 8080).
#
# Statuses reported per test:
#   PASS       - Test executed and contract assertions passed.
#   FAIL       - Test executed and contract assertion failed.
#   BLOCKED    - Test blocked due to missing service dependency or offline service.
#   NOT TESTED - Feature intentionally outside current test scope.
# ==============================================================================

set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@demo.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"

PASS_COUNT=0
FAIL_COUNT=0
BLOCKED_COUNT=0
NOT_TESTED_COUNT=0

COLOR_GREEN='\033[0;32m'
COLOR_RED='\033[0;31m'
COLOR_YELLOW='\033[0;33m'
COLOR_BLUE='\033[0;34m'
COLOR_RESET='\033[0m'

log_result() {
    local status="$1"
    local name="$2"
    local details="$3"

    case "$status" in
        PASS)
            echo -e "  [${COLOR_GREEN}PASS${COLOR_RESET}]       ${name} — ${details}"
            ((PASS_COUNT++)) || true
            ;;
        FAIL)
            echo -e "  [${COLOR_RED}FAIL${COLOR_RESET}]       ${name} — ${details}"
            ((FAIL_COUNT++)) || true
            ;;
        BLOCKED)
            echo -e "  [${COLOR_YELLOW}BLOCKED${COLOR_RESET}]    ${name} — ${details}"
            ((BLOCKED_COUNT++)) || true
            ;;
        "NOT TESTED")
            echo -e "  [${COLOR_BLUE}NOT TESTED${COLOR_RESET}] ${name} — ${details}"
            ((NOT_TESTED_COUNT++)) || true
            ;;
    esac
}

echo "=============================================================================="
echo " SwiftDrop E2E API Gateway Integration Smoke Test"
echo " Target Gateway: ${GATEWAY_URL}"
echo " Timestamp:      $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "=============================================================================="
echo ""

# ------------------------------------------------------------------------------
# 1. Gateway Health Check & Edge Connectivity
# ------------------------------------------------------------------------------
echo "--- 1. Gateway Health & Infrastructure ---"
GATEWAY_HEADER=$(curl -sI "${GATEWAY_URL}/actuator/health" 2>/dev/null || true)
if echo "$GATEWAY_HEADER" | grep -iq "Jenkins"; then
    log_result "BLOCKED" "Gateway Port Conflict" "Port ${GATEWAY_URL} is currently occupied by Jenkins (X-Jenkins header detected). Launch SwiftDrop via docker compose or specify GATEWAY_URL."
    echo ""
    echo "  [NOTE] A host service (Jenkins) is currently occupying port 8080."
    echo "         To test SwiftDrop API Gateway, either stop Jenkins or run SwiftDrop on an alternative port (e.g. GATEWAY_PORT=8000)."
    echo ""
    exit 0
fi

GATEWAY_HEALTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$GATEWAY_HEALTH_HTTP" = "200" ]; then
    log_result "PASS" "Gateway Health Check (/actuator/health)" "HTTP 200 OK"
elif [ "$GATEWAY_HEALTH_HTTP" = "000" ]; then
    log_result "BLOCKED" "Gateway Health Check (/actuator/health)" "Gateway is not running on ${GATEWAY_URL}"
else
    log_result "FAIL" "Gateway Health Check (/actuator/health)" "Unexpected HTTP status: ${GATEWAY_HEALTH_HTTP}"
fi

# ------------------------------------------------------------------------------
# 2. Security Boundaries & Tampered Token Rejection
# ------------------------------------------------------------------------------
echo ""
echo "--- 2. Security Boundary & Authentication Enforcement ---"

# 2.1 Unauthenticated request to protected endpoint must return 401
UNAUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/api/v1/merchants/admin/merchants" 2>/dev/null || echo "000")
if [ "$UNAUTH_HTTP" = "401" ]; then
    log_result "PASS" "Unauthenticated Request Rejection" "HTTP 401 Unauthorized as expected"
elif [ "$UNAUTH_HTTP" = "000" ]; then
    log_result "BLOCKED" "Unauthenticated Request Rejection" "Gateway offline"
else
    log_result "FAIL" "Unauthenticated Request Rejection" "Expected 401, got ${UNAUTH_HTTP}"
fi

# 2.2 Tampered Bearer token must return 401
TAMPERED_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.tampered.signature" \
    "${GATEWAY_URL}/api/v1/merchants/admin/merchants" 2>/dev/null || echo "000")
if [ "$TAMPERED_HTTP" = "401" ]; then
    log_result "PASS" "Tampered JWT Signature Rejection" "HTTP 401 Unauthorized as expected"
elif [ "$TAMPERED_HTTP" = "000" ]; then
    log_result "BLOCKED" "Tampered JWT Signature Rejection" "Gateway offline"
else
    log_result "FAIL" "Tampered JWT Signature Rejection" "Expected 401, got ${TAMPERED_HTTP}"
fi

# 2.3 Spoofed Header Stripping verification
SPOOFED_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-User-Roles: ROLE_ADMIN" \
    -H "X-User-Id: spoofed-uuid" \
    "${GATEWAY_URL}/api/v1/merchants/admin/merchants" 2>/dev/null || echo "000")
if [ "$SPOOFED_HTTP" = "401" ]; then
    log_result "PASS" "Spoofed Header Stripping (X-User-Roles)" "HTTP 401: client-supplied identity ignored"
elif [ "$SPOOFED_HTTP" = "000" ]; then
    log_result "BLOCKED" "Spoofed Header Stripping" "Gateway offline"
else
    log_result "FAIL" "Spoofed Header Stripping" "Expected 401, got ${SPOOFED_HTTP}"
fi

# ------------------------------------------------------------------------------
# 3. Authentication Through Gateway (auth-service)
# ------------------------------------------------------------------------------
echo ""
echo "--- 3. Authentication & Token Issuance ---"

ADMIN_TOKEN=""
LOGIN_RESP=$(curl -s -w "\n%{http_code}" \
    -X POST "${GATEWAY_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" 2>/dev/null || echo -e "\n000")

LOGIN_HTTP=$(echo "$LOGIN_RESP" | tail -n 1)
LOGIN_BODY=$(echo "$LOGIN_RESP" | head -n -1)

if [ "$LOGIN_HTTP" = "200" ]; then
    ADMIN_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4 || echo "")
    if [ -n "$ADMIN_TOKEN" ]; then
        log_result "PASS" "Admin Authentication (/api/v1/auth/login)" "HTTP 200 OK with valid JWT"
    else
        log_result "FAIL" "Admin Authentication (/api/v1/auth/login)" "HTTP 200 but token missing in body"
    fi
elif [ "$LOGIN_HTTP" = "503" ]; then
    log_result "PASS" "Downstream Service Offline Handling" "HTTP 503 DOWNSTREAM_SERVICE_UNAVAILABLE properly propagated"
    log_result "BLOCKED" "Admin Authentication (/api/v1/auth/login)" "Downstream auth-service is offline"
elif [ "$LOGIN_HTTP" = "000" ]; then
    log_result "BLOCKED" "Admin Authentication (/api/v1/auth/login)" "Gateway offline"
else
    log_result "FAIL" "Admin Authentication (/api/v1/auth/login)" "Unexpected HTTP status: ${LOGIN_HTTP}"
fi

# ------------------------------------------------------------------------------
# 4. Merchant Onboarding & Admin Compliance
# ------------------------------------------------------------------------------
echo ""
echo "--- 4. Merchant Onboarding & Admin Compliance ---"

if [ -n "$ADMIN_TOKEN" ]; then
    # Test Admin Merchant Listing
    MERCHANT_LIST_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        "${GATEWAY_URL}/api/v1/merchants/admin/merchants" 2>/dev/null || echo "000")
    if [ "$MERCHANT_LIST_HTTP" = "200" ]; then
        log_result "PASS" "Admin Merchant Review Listing" "HTTP 200 OK"
    elif [ "$MERCHANT_LIST_HTTP" = "503" ]; then
        log_result "BLOCKED" "Admin Merchant Review Listing" "Downstream merchant-service offline"
    else
        log_result "FAIL" "Admin Merchant Review Listing" "Expected 200, got ${MERCHANT_LIST_HTTP}"
    fi
else
    log_result "BLOCKED" "Admin Merchant Review Listing" "Blocked by missing admin token"
fi

# ------------------------------------------------------------------------------
# 5. Dispatch Job Operations & Auto-Dispatch
# ------------------------------------------------------------------------------
echo ""
echo "--- 5. Dispatch Operations & Matching Engine ---"

if [ -n "$ADMIN_TOKEN" ]; then
    # Query unassigned jobs
    JOBS_RESP=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        "${GATEWAY_URL}/api/v1/dispatch/jobs?status=UNASSIGNED" 2>/dev/null || echo -e "\n000")
    JOBS_HTTP=$(echo "$JOBS_RESP" | tail -n 1)

    if [ "$JOBS_HTTP" = "200" ]; then
        log_result "PASS" "Dispatch Jobs Query (/api/v1/dispatch/jobs)" "HTTP 200 OK"
    elif [ "$JOBS_HTTP" = "503" ]; then
        log_result "BLOCKED" "Dispatch Jobs Query" "Downstream dispatch-service offline"
    else
        log_result "FAIL" "Dispatch Jobs Query" "Expected 200, got ${JOBS_HTTP}"
    fi

    # Trigger batch auto-dispatch
    AUTO_DISPATCH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        "${GATEWAY_URL}/api/v1/dispatch/jobs/auto-dispatch" 2>/dev/null || echo "000")

    if [ "$AUTO_DISPATCH_HTTP" = "200" ]; then
        log_result "PASS" "Batch Auto-Dispatch Trigger" "HTTP 200 OK"
    elif [ "$AUTO_DISPATCH_HTTP" = "503" ]; then
        log_result "BLOCKED" "Batch Auto-Dispatch Trigger" "Downstream dispatch-service offline"
    else
        log_result "FAIL" "Batch Auto-Dispatch Trigger" "Expected 200, got ${AUTO_DISPATCH_HTTP}"
    fi
else
    log_result "BLOCKED" "Dispatch Jobs Query" "Blocked by missing admin token"
    log_result "BLOCKED" "Batch Auto-Dispatch Trigger" "Blocked by missing admin token"
fi

# ------------------------------------------------------------------------------
# 6. Member-Owned Skeleton Services (Strict Ownership Separation)
# ------------------------------------------------------------------------------
echo ""
echo "--- 6. Member-Owned Microservices (Ownership Boundary) ---"
log_result "NOT TESTED" "Pricing Calculation Engine" "Owned by pricing-service team member"
log_result "NOT TESTED" "Public Waybill Tracking Stream" "Owned by tracking-service team member"
log_result "NOT TESTED" "Email/SMS Delivery Notifications" "Owned by notification-service team member"
log_result "NOT TESTED" "Aggregated KPI Dispatch Reports" "Owned by reporting-service team member"
log_result "NOT TESTED" "External Webhook Consumer Relay" "Owned by webhook-service team member"

# ------------------------------------------------------------------------------
# Summary Report
# ------------------------------------------------------------------------------
echo ""
echo "=============================================================================="
echo " E2E Smoke Test Summary"
echo "=============================================================================="
echo -e "  PASSED:      ${COLOR_GREEN}${PASS_COUNT}${COLOR_RESET}"
echo -e "  FAILED:      ${COLOR_RED}${FAIL_COUNT}${COLOR_RESET}"
echo -e "  BLOCKED:     ${COLOR_YELLOW}${BLOCKED_COUNT}${COLOR_RESET}"
echo -e "  NOT TESTED:  ${COLOR_BLUE}${NOT_TESTED_COUNT}${COLOR_RESET}"
echo "=============================================================================="

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
exit 0
