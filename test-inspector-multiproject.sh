#!/bin/bash
# Test script for AWS Inspector Multi-Project Support
BASE_URL="http://localhost:8080"
echo "==================================="
echo "AWS Inspector Multi-Project Tests"
echo "==================================="
echo ""
# Test 1: Get vulnerabilities without projectId (backward compatibility)
echo "Test 1: GET /api/vulnerabilities (default credentials)"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/vulnerabilities" | jq '.' || echo "API might not be running"
echo ""
echo ""
# Test 2: Create a test project
echo "Test 2: POST /api/admin/projects/upload"
echo "-------------------------------------------------------"
PROJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/projects/upload" \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "Test Inspector Project",
    "githubOwner": "testorg",
    "githubRepo": "testrepo",
    "githubToken": "ghp_test_token_123",
    "awsRegion": "eu-west-1",
    "awsAccessKey": "AKIATEST123",
    "awsSecretKey": "testSecretKey123",
    "createdBy": "test-script"
  }')
echo "$PROJECT_RESPONSE" | jq '.'
PROJECT_ID=$(echo "$PROJECT_RESPONSE" | jq -r '.projectId // empty')
if [ -z "$PROJECT_ID" ]; then
  echo "ERROR: Failed to create project"
  exit 1
fi
echo ""
echo "Created project with ID: $PROJECT_ID"
echo ""
# Test 3: Get vulnerabilities with projectId
echo "Test 3: GET /api/vulnerabilities?projectId=$PROJECT_ID"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/vulnerabilities?projectId=$PROJECT_ID" | jq '.' || echo "Project-specific API call"
echo ""
echo ""
# Test 4: Get specific vulnerability without projectId
echo "Test 4: GET /api/vulnerabilities/CVE-2023-1234 (default)"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/vulnerabilities/CVE-2023-1234" | jq '.' || echo "API call"
echo ""
echo ""
# Test 5: Get specific vulnerability with projectId
echo "Test 5: GET /api/vulnerabilities/CVE-2023-1234?projectId=$PROJECT_ID"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/vulnerabilities/CVE-2023-1234?projectId=$PROJECT_ID" | jq '.' || echo "Project-specific API call"
echo ""
echo ""
# Test 6: List all projects
echo "Test 6: GET /api/admin/projects"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/admin/projects" | jq '.'
echo ""
echo ""
# Test 7: Get project details
echo "Test 7: GET /api/admin/projects/$PROJECT_ID"
echo "-------------------------------------------------------"
curl -s -X GET "$BASE_URL/api/admin/projects/$PROJECT_ID" | jq '.'
echo ""
echo ""
# Cleanup (optional)
echo "Cleanup: DELETE /api/admin/projects/$PROJECT_ID"
echo "-------------------------------------------------------"
read -p "Do you want to delete the test project? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  curl -s -X DELETE "$BASE_URL/api/admin/projects/$PROJECT_ID" | jq '.'
  echo ""
  echo "Test project deleted"
else
  echo "Test project preserved with ID: $PROJECT_ID"
fi
echo ""
echo "==================================="
echo "Tests completed!"
echo "==================================="
