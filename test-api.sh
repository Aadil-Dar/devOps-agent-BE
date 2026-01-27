#!/bin/bash

# Test script for Multi-Project DevOps Agent
# This script tests the complete workflow

set -e

BASE_URL="http://localhost:8080"
PROJECT_ID=""

echo "🚀 Testing DevOps Agent Multi-Project API"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if server is running
echo "📡 Checking if server is running..."
if curl -s "${BASE_URL}/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓ Server is running${NC}"
else
    echo -e "${RED}✗ Server is not running. Please start it with: ./gradlew bootRun${NC}"
    exit 1
fi
echo ""

# Test 1: Initialize Database
echo "1️⃣  Testing: Initialize Database"
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/admin/system/init-db")
echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "success"; then
    echo -e "${GREEN}✓ Database initialized successfully${NC}"
else
    echo -e "${YELLOW}⚠ Database may already exist${NC}"
fi
echo ""

# Test 2: Create Project
echo "2️⃣  Testing: Create Project"
CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/admin/projects/upload" \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "Test Project",
    "githubOwner": "test-org",
    "githubRepo": "test-repo",
    "awsRegion": "eu-west-1",
    "createdBy": "test-script",
    "githubToken": "ghp_test_token_12345",
    "awsAccessKey": "AKIATEST12345",
    "awsSecretKey": "testSecretKey12345"
  }')

echo "Response: $CREATE_RESPONSE"
PROJECT_ID=$(echo "$CREATE_RESPONSE" | grep -o '"projectId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$PROJECT_ID" ]; then
    echo -e "${GREEN}✓ Project created successfully${NC}"
    echo "   Project ID: $PROJECT_ID"
else
    echo -e "${RED}✗ Failed to create project${NC}"
    exit 1
fi
echo ""

# Test 3: List All Projects
echo "3️⃣  Testing: List All Projects"
LIST_RESPONSE=$(curl -s "${BASE_URL}/api/admin/projects")
echo "Response: $LIST_RESPONSE"
if echo "$LIST_RESPONSE" | grep -q "$PROJECT_ID"; then
    echo -e "${GREEN}✓ Project found in list${NC}"
else
    echo -e "${RED}✗ Project not found in list${NC}"
fi
echo ""

# Test 4: Get Project by ID
echo "4️⃣  Testing: Get Project by ID"
GET_RESPONSE=$(curl -s "${BASE_URL}/api/admin/projects/${PROJECT_ID}")
echo "Response: $GET_RESPONSE"
if echo "$GET_RESPONSE" | grep -q "Test Project"; then
    echo -e "${GREEN}✓ Project retrieved successfully${NC}"
else
    echo -e "${RED}✗ Failed to retrieve project${NC}"
fi
echo ""

# Test 5: Get Project Info (Worker API)
echo "5️⃣  Testing: Get Project Info (Worker API)"
INFO_RESPONSE=$(curl -s "${BASE_URL}/api/v1/projects/${PROJECT_ID}/info")
echo "Response: $INFO_RESPONSE"
if echo "$INFO_RESPONSE" | grep -q "test-org"; then
    echo -e "${GREEN}✓ Project info retrieved successfully${NC}"
else
    echo -e "${RED}✗ Failed to retrieve project info${NC}"
fi
echo ""

# Test 6: Validate Project
echo "6️⃣  Testing: Validate Project"
VALIDATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/admin/projects/${PROJECT_ID}/validate")
echo "Response: $VALIDATE_RESPONSE"
if echo "$VALIDATE_RESPONSE" | grep -q "valid"; then
    echo -e "${GREEN}✓ Project validation successful${NC}"
else
    echo -e "${RED}✗ Project validation failed${NC}"
fi
echo ""

# Test 7: Update Project
echo "7️⃣  Testing: Update Project"
UPDATE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/admin/projects/${PROJECT_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "Test Project Updated",
    "githubOwner": "test-org-new",
    "githubRepo": "test-repo-new",
    "awsRegion": "us-east-1"
  }')
echo "Response: $UPDATE_RESPONSE"
if echo "$UPDATE_RESPONSE" | grep -q "updated successfully"; then
    echo -e "${GREEN}✓ Project updated successfully${NC}"
else
    echo -e "${YELLOW}⚠ Update may have partial success${NC}"
fi
echo ""

# Test 8: Toggle Project (Disable)
echo "8️⃣  Testing: Toggle Project Status"
TOGGLE_RESPONSE=$(curl -s -X PATCH "${BASE_URL}/api/admin/projects/${PROJECT_ID}/toggle")
echo "Response: $TOGGLE_RESPONSE"
if echo "$TOGGLE_RESPONSE" | grep -q "enabled\|disabled"; then
    echo -e "${GREEN}✓ Project toggled successfully${NC}"
else
    echo -e "${RED}✗ Failed to toggle project${NC}"
fi
echo ""

# Test 9: Execute Operation (should fail if disabled)
echo "9️⃣  Testing: Execute Operation"
EXECUTE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/projects/${PROJECT_ID}/execute" \
  -H "Content-Type: application/json" \
  -d '{"operation": "test"}')
echo "Response: $EXECUTE_RESPONSE"
if echo "$EXECUTE_RESPONSE" | grep -q "success\|error"; then
    echo -e "${GREEN}✓ Operation executed${NC}"
else
    echo -e "${YELLOW}⚠ Operation may have issues${NC}"
fi
echo ""

# Test 10: Delete Project
echo "🔟 Testing: Delete Project"
read -p "Do you want to delete the test project? (y/n) " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/admin/projects/${PROJECT_ID}")
    echo "Response: $DELETE_RESPONSE"
    if echo "$DELETE_RESPONSE" | grep -q "deleted successfully"; then
        echo -e "${GREEN}✓ Project deleted successfully${NC}"
    else
        echo -e "${RED}✗ Failed to delete project${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Skipping deletion. Project ID: ${PROJECT_ID}${NC}"
fi
echo ""

echo "=========================================="
echo -e "${GREEN}✅ Testing completed!${NC}"
echo ""
echo "📝 Summary:"
echo "   - Base URL: $BASE_URL"
echo "   - Test Project ID: $PROJECT_ID"
echo ""
echo "💡 Tips:"
echo "   - Import postman_collection.json for manual testing"
echo "   - Check logs: tail -f logs/application.log"
echo "   - View cache stats: curl $BASE_URL/actuator/metrics/cache.gets"
echo ""

