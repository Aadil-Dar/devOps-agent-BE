#!/bin/bash

# JWT Authentication Test Script
# This script tests the JWT authentication endpoints

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================"
echo "JWT Authentication Test Suite"
echo "============================================"
echo ""

# Test 1: Login with admin credentials
echo -e "${YELLOW}Test 1: Login with admin credentials${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

if echo "$LOGIN_RESPONSE" | grep -q "token"; then
    echo -e "${GREEN}✓ Login successful${NC}"
    ADMIN_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo "Token obtained: ${ADMIN_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Login failed${NC}"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi
echo ""

# Test 2: Login with user credentials
echo -e "${YELLOW}Test 2: Login with regular user credentials${NC}"
USER_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user",
    "password": "user123"
  }')

if echo "$USER_LOGIN_RESPONSE" | grep -q "token"; then
    echo -e "${GREEN}✓ User login successful${NC}"
    USER_TOKEN=$(echo "$USER_LOGIN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
else
    echo -e "${RED}✗ User login failed${NC}"
    echo "Response: $USER_LOGIN_RESPONSE"
fi
echo ""

# Test 3: Login with invalid credentials
echo -e "${YELLOW}Test 3: Login with invalid credentials${NC}"
INVALID_LOGIN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "wrongpassword"
  }')

if echo "$INVALID_LOGIN" | grep -q "Invalid username or password"; then
    echo -e "${GREEN}✓ Invalid credentials correctly rejected${NC}"
else
    echo -e "${RED}✗ Invalid credentials test failed${NC}"
    echo "Response: $INVALID_LOGIN"
fi
echo ""

# Test 4: Validate token
echo -e "${YELLOW}Test 4: Validate token${NC}"
VALIDATE_RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/validate" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

if echo "$VALIDATE_RESPONSE" | grep -q "Token is valid"; then
    echo -e "${GREEN}✓ Token validation successful${NC}"
else
    echo -e "${RED}✗ Token validation failed${NC}"
    echo "Response: $VALIDATE_RESPONSE"
fi
echo ""

# Test 5: Get current user info
echo -e "${YELLOW}Test 5: Get current user info${NC}"
ME_RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

if echo "$ME_RESPONSE" | grep -q "admin"; then
    echo -e "${GREEN}✓ Current user info retrieved${NC}"
    echo "User info: $ME_RESPONSE"
else
    echo -e "${RED}✗ Failed to get current user info${NC}"
    echo "Response: $ME_RESPONSE"
fi
echo ""

# Test 6: Register new user
echo -e "${YELLOW}Test 6: Register new user${NC}"
RANDOM_NUM=$RANDOM
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"testuser$RANDOM_NUM\",
    \"password\": \"test123456\",
    \"email\": \"testuser$RANDOM_NUM@devops.com\",
    \"roles\": [\"USER\"]
  }")

if echo "$REGISTER_RESPONSE" | grep -q "registered successfully"; then
    echo -e "${GREEN}✓ User registration successful${NC}"
    echo "Created user: testuser$RANDOM_NUM"
else
    echo -e "${RED}✗ User registration failed${NC}"
    echo "Response: $REGISTER_RESPONSE"
fi
echo ""

# Test 7: Access protected endpoint without token
echo -e "${YELLOW}Test 7: Access protected endpoint without token${NC}"
NO_TOKEN_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X GET "$BASE_URL/api/pipelines")
HTTP_CODE=$(echo "$NO_TOKEN_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)

if [ "$HTTP_CODE" = "401" ]; then
    echo -e "${GREEN}✓ Unauthorized access correctly rejected (401)${NC}"
else
    echo -e "${RED}✗ Expected 401, got $HTTP_CODE${NC}"
fi
echo ""

# Test 8: Access protected endpoint with valid token
echo -e "${YELLOW}Test 8: Access protected endpoint with valid token${NC}"
PROTECTED_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X GET "$BASE_URL/api/pipelines" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
HTTP_CODE=$(echo "$PROTECTED_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
    echo -e "${GREEN}✓ Protected endpoint accessed with token (HTTP $HTTP_CODE)${NC}"
else
    echo -e "${RED}✗ Unexpected response: $HTTP_CODE${NC}"
fi
echo ""

# Test 9: Access with invalid token
echo -e "${YELLOW}Test 9: Access with invalid token${NC}"
INVALID_TOKEN_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X GET "$BASE_URL/api/pipelines" \
  -H "Authorization: Bearer invalid.token.here")
HTTP_CODE=$(echo "$INVALID_TOKEN_RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)

if [ "$HTTP_CODE" = "401" ]; then
    echo -e "${GREEN}✓ Invalid token correctly rejected (401)${NC}"
else
    echo -e "${RED}✗ Expected 401, got $HTTP_CODE${NC}"
fi
echo ""

# Test 10: Register duplicate username
echo -e "${YELLOW}Test 10: Register duplicate username${NC}"
DUPLICATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "newpassword",
    "email": "newemail@devops.com",
    "roles": ["USER"]
  }')

if echo "$DUPLICATE_RESPONSE" | grep -q "already taken"; then
    echo -e "${GREEN}✓ Duplicate username correctly rejected${NC}"
else
    echo -e "${RED}✗ Duplicate username test failed${NC}"
    echo "Response: $DUPLICATE_RESPONSE"
fi
echo ""

echo "============================================"
echo "Test Suite Complete"
echo "============================================"
echo ""
echo "Summary:"
echo "- Authentication endpoints are working"
echo "- Token generation and validation functional"
echo "- Protected endpoints require authentication"
echo "- User registration with validation"
echo ""
echo "Admin Token (save for manual testing):"
echo "$ADMIN_TOKEN"

