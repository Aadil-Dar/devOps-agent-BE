#!/bin/bash

# User Management API Test Script

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "User Management API Tests"
echo "=========================================="
echo ""

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print test results
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

echo "----------------------------------------"
echo "Test 1: Admin Login"
echo "----------------------------------------"
ADMIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@devops.com",
    "password": "admin123"
  }')

ADMIN_TOKEN=$(echo $ADMIN_RESPONSE | jq -r '.token')

if [ "$ADMIN_TOKEN" != "null" ] && [ -n "$ADMIN_TOKEN" ]; then
    print_result 0 "Admin login successful"
    echo "Token: ${ADMIN_TOKEN:0:50}..."
else
    print_result 1 "Admin login failed"
    echo "Response: $ADMIN_RESPONSE"
fi
echo ""

echo "----------------------------------------"
echo "Test 2: Register New User"
echo "----------------------------------------"
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "testuser@example.com",
    "password": "test123456"
  }')

echo "Response: $REGISTER_RESPONSE"
echo ""

echo "----------------------------------------"
echo "Test 3: New User Login (Should Fail - No Access)"
echo "----------------------------------------"
LOGIN_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "test123456"
  }')

HTTP_STATUS=$(echo "$LOGIN_RESPONSE" | grep -o "HTTP_STATUS:[0-9]*" | cut -d: -f2)
RESPONSE_BODY=$(echo "$LOGIN_RESPONSE" | sed 's/HTTP_STATUS:[0-9]*//g')

if [ "$HTTP_STATUS" == "403" ]; then
    print_result 0 "Login correctly denied (403)"
    echo "Message: $RESPONSE_BODY"
else
    print_result 1 "Expected 403 status, got $HTTP_STATUS"
    echo "Response: $RESPONSE_BODY"
fi
echo ""

echo "----------------------------------------"
echo "Test 4: Get All Users (Admin)"
echo "----------------------------------------"
USERS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

echo "$USERS_RESPONSE" | jq '.'
echo ""

# Extract testuser ID
TEST_USER_ID=$(echo "$USERS_RESPONSE" | jq -r '.[] | select(.username=="testuser") | .id')

if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
    echo "Test User ID: $TEST_USER_ID"

    echo ""
    echo "----------------------------------------"
    echo "Test 5: Enable DevOps Access for Test User"
    echo "----------------------------------------"
    TOGGLE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/users/$TEST_USER_ID/toggle-access" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "enable": true
      }')

    echo "$TOGGLE_RESPONSE" | jq '.'
    echo ""

    echo "----------------------------------------"
    echo "Test 6: New User Login (Should Succeed Now)"
    echo "----------------------------------------"
    LOGIN_RESPONSE2=$(curl -s -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d '{
        "email": "testuser@example.com",
        "password": "test123456"
      }')

    TEST_USER_TOKEN=$(echo $LOGIN_RESPONSE2 | jq -r '.token')

    if [ "$TEST_USER_TOKEN" != "null" ] && [ -n "$TEST_USER_TOKEN" ]; then
        print_result 0 "Test user login successful after enabling access"
        echo "Response: $LOGIN_RESPONSE2" | jq '.'
    else
        print_result 1 "Test user login failed"
        echo "Response: $LOGIN_RESPONSE2"
    fi
    echo ""

    echo "----------------------------------------"
    echo "Test 7: Get Current User Info"
    echo "----------------------------------------"
    ME_RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
      -H "Authorization: Bearer $TEST_USER_TOKEN")

    echo "$ME_RESPONSE" | jq '.'
    echo ""

    echo "----------------------------------------"
    echo "Test 8: Assign Project to User"
    echo "----------------------------------------"
    ASSIGN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/users/$TEST_USER_ID/assign-project" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "projectId": "test-project-123"
      }')

    # Note: This will fail if project doesn't exist, which is expected
    echo "$ASSIGN_RESPONSE" | jq '.'
    echo ""

    echo "----------------------------------------"
    echo "Test 9: Get Users by Project"
    echo "----------------------------------------"
    PROJECT_USERS=$(curl -s -X GET "$BASE_URL/api/admin/users/by-project/test-project-123" \
      -H "Authorization: Bearer $ADMIN_TOKEN")

    echo "$PROJECT_USERS" | jq '.'
    echo ""

    echo "----------------------------------------"
    echo "Test 10: Update User Details"
    echo "----------------------------------------"
    UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/admin/users/$TEST_USER_ID" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "email": "updated@example.com",
        "enableToUseDevops": true
      }')

    echo "$UPDATE_RESPONSE" | jq '.'
    echo ""
else
    echo "Could not find test user ID"
fi

echo "=========================================="
echo "Tests Complete"
echo "=========================================="
echo ""
echo "Summary:"
echo "- Admin login and authentication"
echo "- User registration"
echo "- Access control (deny without permission)"
echo "- Admin user management operations"
echo "- Project assignment"
echo "- User update operations"
echo ""
echo "Note: Some tests may fail if project doesn't exist."
echo "Use the project upload endpoint first to create projects."

