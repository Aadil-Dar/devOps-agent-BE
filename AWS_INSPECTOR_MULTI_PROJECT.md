# AWS Inspector Multi-Project Support

## Overview

The AWS Inspector functionality has been enhanced to support multi-project configurations. Now, instead of using a single set of AWS credentials, the system can fetch project-specific AWS credentials from DynamoDB and AWS Secrets Manager based on the `projectId` parameter.

## Architecture

### Data Storage
1. **DynamoDB** - Stores non-sensitive project configuration:
   - Project name
   - GitHub owner/repo
   - AWS region
   - Enabled/disabled status
   - Metadata

2. **AWS Secrets Manager** - Stores sensitive credentials:
   - GitHub token
   - AWS access key
   - AWS secret key

### Flow
```
1. API Request with projectId
   ↓
2. Fetch Project Config from DynamoDB
   ↓
3. Fetch Credentials from Secrets Manager
   ↓
4. Create project-specific Inspector2Client
   ↓
5. Call AWS Inspector API
   ↓
6. Return vulnerabilities
```

## API Endpoints

### 1. Get All Vulnerabilities

**Endpoint:** `GET /api/vulnerabilities`

**Query Parameters:**
- `projectId` (optional) - The project ID to fetch vulnerabilities for

**Examples:**

```bash
# Using default credentials (backward compatible)
curl http://localhost:8080/api/vulnerabilities

# Using project-specific credentials
curl http://localhost:8080/api/vulnerabilities?projectId=123e4567-e89b-12d3-a456-426614174000
```

**Response:**
```json
[
  {
    "id": "CVE-2023-1234",
    "title": "Critical Vulnerability",
    "severity": "CRITICAL",
    "status": "ACTIVE",
    "description": "Description of vulnerability",
    "firstObservedAt": "2024-01-15T10:30:00Z"
  }
]
```

### 2. Get Vulnerability by ID

**Endpoint:** `GET /api/vulnerabilities/{id}`

**Path Parameters:**
- `id` - The vulnerability ID or ARN

**Query Parameters:**
- `projectId` (optional) - The project ID to fetch the vulnerability for

**Examples:**

```bash
# Using default credentials
curl http://localhost:8080/api/vulnerabilities/CVE-2023-1234

# Using project-specific credentials
curl http://localhost:8080/api/vulnerabilities/CVE-2023-1234?projectId=123e4567-e89b-12d3-a456-426614174000
```

**Response:**
```json
{
  "id": "CVE-2023-1234",
  "findingArn": "arn:aws:inspector2:...",
  "title": "Critical Vulnerability",
  "severity": "CRITICAL",
  "status": "ACTIVE",
  "description": "Detailed description",
  "recommendation": "Update to version X.Y.Z",
  "affectedResources": ["resource-1", "resource-2"],
  "cvssScore": 9.8,
  "references": ["https://example.com/cve-2023-1234"],
  "firstObservedAt": "2024-01-15T10:30:00Z",
  "lastObservedAt": "2024-01-20T15:45:00Z"
}
```

## Setting Up a New Project

### Step 1: Upload Project Configuration

Use the Admin API to create a new project:

```bash
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "My Project",
    "githubOwner": "myorg",
    "githubRepo": "myrepo",
    "githubToken": "ghp_xxxxxxxxxxxxx",
    "awsRegion": "eu-west-1",
    "awsAccessKey": "AKIAXXXXXXXXXXXXX",
    "awsSecretKey": "xxxxxxxxxxxxxxxxxxxxxx",
    "createdBy": "admin"
  }'
```

**Response:**
```json
{
  "projectId": "123e4567-e89b-12d3-a456-426614174000",
  "projectName": "My Project",
  "githubOwner": "myorg",
  "githubRepo": "myrepo",
  "awsRegion": "eu-west-1",
  "enabled": true,
  "message": "Project created successfully"
}
```

### Step 2: Fetch Vulnerabilities for the Project

```bash
curl http://localhost:8080/api/vulnerabilities?projectId=123e4567-e89b-12d3-a456-426614174000
```

## Backward Compatibility

The system maintains backward compatibility:
- If `projectId` is **not provided**, the system uses the **default AWS credentials** configured in `application.properties`
- If `projectId` is **provided**, the system uses **project-specific credentials** from DynamoDB and Secrets Manager

## Service Layer Changes

### AwsInspectorService

New methods added:
- `getAllVulnerabilitiesForProject(String projectId)` - Fetch vulnerabilities for a specific project
- `getVulnerabilityByIdForProject(String projectId, String id)` - Fetch a specific vulnerability for a project
- `createProjectInspectorClient(String projectId)` - Create Inspector2Client with project-specific credentials

The service now:
1. Retrieves project configuration from DynamoDB
2. Retrieves credentials from AWS Secrets Manager
3. Creates a project-specific `Inspector2Client` with the appropriate region and credentials
4. Uses try-with-resources to automatically close the client after use

## Controller Layer Changes

### AwsInspectorController

Updated endpoints to accept optional `projectId` query parameter:
- `GET /api/vulnerabilities?projectId={projectId}`
- `GET /api/vulnerabilities/{id}?projectId={projectId}`

## Security Considerations

1. **Credentials Storage**: Sensitive credentials are stored in AWS Secrets Manager, not in DynamoDB
2. **Access Control**: Implement proper authentication/authorization before allowing access to project-specific vulnerabilities
3. **Audit Logging**: All requests are logged with the projectId for audit purposes
4. **Resource Cleanup**: Inspector2Client instances are automatically closed using try-with-resources

## Error Handling

The system handles various error scenarios:
- **Project not found**: Returns 404 Not Found
- **Project disabled**: Throws RuntimeException with clear message
- **Invalid credentials**: Falls back to dummy data (for development)
- **AWS API errors**: Logs error and returns appropriate HTTP status

## Testing

### Test with Default Credentials
```bash
curl http://localhost:8080/api/vulnerabilities
```

### Test with Project-Specific Credentials
```bash
# First, create a project
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{...}' | jq -r '.projectId')

# Then, fetch vulnerabilities for that project
curl "http://localhost:8080/api/vulnerabilities?projectId=$PROJECT_ID"
```

## Configuration

### Required AWS Permissions

The AWS credentials (either default or project-specific) need the following permissions:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "inspector2:ListFindings",
        "inspector2:ListFindingAggregations"
      ],
      "Resource": "*"
    }
  ]
}
```

### Application Properties

```properties
# Default AWS region (used when projectId is not provided)
aws.region=eu-west-1

# Secrets Manager prefix
aws.secrets-manager.prefix=devops-agent/projects/
```

## Future Enhancements

1. Add caching for Inspector2Client instances
2. Implement connection pooling for better performance
3. Add metrics/monitoring for project-specific API calls
4. Implement rate limiting per project
5. Add support for cross-account AWS Inspector access

