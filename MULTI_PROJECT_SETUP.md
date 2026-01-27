# Multi-Project Configuration API

## Architecture Overview

This application now supports multiple projects with separate configurations and credentials.

```
┌─────────────────────────────────┐
│     Admin Panel (Upload)        │
│  - Upload config JSON file      │
│  - Fields: repo, owner, tokens  │
└─────────────┬───────────────────┘
              │
    ┌─────────▼──────────┐
    │ Spring Boot API    │
    │  /admin/projects   │
    └─────────┬──────────┘
              │
     ┌────────┼────────┐
     │                 │
┌────▼─────┐    ┌─────▼────────┐
│ DynamoDB │    │   Secrets    │
│ (Config) │    │   Manager    │
│          │    │ (Credentials)│
└────┬─────┘    └─────┬────────┘
     │                │
     └────────┬───────┘
    ┌─────────▼──────────┐
    │ Worker Service     │
    │  - Cache config    │
    │  - Fetch secrets   │
    │  - Execute DevOps  │
    └────────────────────┘
```

## Setup Instructions

### 1. AWS Prerequisites

#### DynamoDB Table
The application will auto-create the table, or you can create it manually:

```bash
aws dynamodb create-table \
    --table-name devops-projects \
    --attribute-definitions AttributeName=projectId,AttributeType=S \
    --key-schema AttributeName=projectId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region eu-west-1
```

#### IAM Permissions
Your application needs these AWS permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Scan",
        "dynamodb:Query"
      ],
      "Resource": "arn:aws:dynamodb:*:*:table/devops-projects"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:CreateSecret",
        "secretsmanager:GetSecretValue",
        "secretsmanager:UpdateSecret",
        "secretsmanager:DeleteSecret",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:devops-agent/projects/*"
    }
  ]
}
```

### 2. Application Configuration

Update `application.properties`:

```properties
# AWS Configuration
aws.region=eu-west-1

# DynamoDB
aws.dynamodb.table-name=devops-projects

# Secrets Manager
aws.secrets-manager.prefix=devops-agent/projects/

# Cache
spring.cache.type=caffeine
spring.cache.caffeine.spec=expireAfterWrite=5m,maximumSize=100
```

### 3. Initialize Database

```bash
# Start the application
./gradlew bootRun

# Initialize DynamoDB table
curl -X POST http://localhost:8080/api/admin/system/init-db
```

## API Endpoints

### Admin APIs

#### 1. Upload/Create Project
**Endpoint:** `POST /api/admin/projects/upload`

**Request Body:**
```json
{
  "projectName": "SHOMA UI",
  "githubOwner": "sdc-pune",
  "githubRepo": "shoma-ui",
  "awsRegion": "eu-west-1",
  "createdBy": "admin",
  "githubToken": "ghp_your_token_here",
  "awsAccessKey": "AKIAIOSFODNN7EXAMPLE",
  "awsSecretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}
```

**Response:**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "projectName": "SHOMA UI",
  "githubOwner": "sdc-pune",
  "githubRepo": "shoma-ui",
  "awsRegion": "eu-west-1",
  "enabled": true,
  "createdAt": 1702819200000,
  "updatedAt": 1702819200000,
  "createdBy": "admin",
  "message": "Project created successfully"
}
```

#### 2. List All Projects
**Endpoint:** `GET /api/admin/projects`

**Response:**
```json
[
  {
    "projectId": "550e8400-e29b-41d4-a716-446655440000",
    "projectName": "SHOMA UI",
    "githubOwner": "sdc-pune",
    "githubRepo": "shoma-ui",
    "awsRegion": "eu-west-1",
    "enabled": true,
    "createdAt": 1702819200000
  }
]
```

#### 3. Get Project by ID
**Endpoint:** `GET /api/admin/projects/{projectId}`

#### 4. Update Project
**Endpoint:** `PUT /api/admin/projects/{projectId}`

**Request Body:**
```json
{
  "projectName": "SHOMA UI Updated",
  "githubOwner": "sdc-pune",
  "githubRepo": "shoma-ui",
  "awsRegion": "us-east-1",
  "githubToken": "ghp_new_token_here"
}
```

#### 5. Enable/Disable Project
**Endpoint:** `PATCH /api/admin/projects/{projectId}/toggle`

#### 6. Delete Project
**Endpoint:** `DELETE /api/admin/projects/{projectId}`

#### 7. Validate Project
**Endpoint:** `POST /api/admin/projects/{projectId}/validate`

### Worker/Operation APIs

#### 1. Get Project Info
**Endpoint:** `GET /api/v1/projects/{projectId}/info`

**Response:**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "projectName": "SHOMA UI",
  "githubOwner": "sdc-pune",
  "githubRepo": "shoma-ui",
  "awsRegion": "eu-west-1",
  "enabled": true,
  "message": "Project configuration retrieved successfully"
}
```

#### 2. Execute Operation
**Endpoint:** `POST /api/v1/projects/{projectId}/execute`

**Request Body:**
```json
{
  "operation": "check-pipeline",
  "parameters": {}
}
```

## Usage Examples

### Using cURL

```bash
# 1. Initialize database
curl -X POST http://localhost:8080/api/admin/system/init-db

# 2. Create a project
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "My Project",
    "githubOwner": "myorg",
    "githubRepo": "myrepo",
    "githubToken": "ghp_token",
    "awsRegion": "eu-west-1"
  }'

# 3. List all projects
curl http://localhost:8080/api/admin/projects

# 4. Get project info (replace PROJECT_ID)
curl http://localhost:8080/api/v1/projects/PROJECT_ID/info

# 5. Disable a project
curl -X PATCH http://localhost:8080/api/admin/projects/PROJECT_ID/toggle

# 6. Delete a project
curl -X DELETE http://localhost:8080/api/admin/projects/PROJECT_ID
```

## How to Update Existing Controllers

To use multi-project configuration in your existing controllers:

```java
@RestController
public class MyController {

    @Autowired
    private ProjectWorkerService workerService;

    @GetMapping("/api/my-endpoint/{projectId}")
    public ResponseEntity<?> myEndpoint(@PathVariable String projectId) {
        // Get project-specific configuration
        FullProjectConfig config = workerService.getFullProjectConfig(projectId);
        
        // Use project-specific credentials
        String githubToken = config.getGithubToken();
        String awsRegion = config.getAwsRegion();
        
        // Your logic here...
        return ResponseEntity.ok("Success");
    }
}
```

## Security Best Practices

1. **Never log sensitive data** (tokens, keys)
2. **Use HTTPS** in production
3. **Implement authentication** for admin endpoints
4. **Rotate credentials** regularly
5. **Use AWS IAM roles** instead of access keys when possible
6. **Enable CloudWatch logs** for audit trail

## Caching

- Project configurations are cached for 5 minutes
- Cache is automatically evicted when configurations are updated
- Secrets Manager responses are also cached by AWS SDK

## Monitoring

Check cache statistics:
```bash
curl http://localhost:8080/actuator/metrics/cache.gets
curl http://localhost:8080/actuator/metrics/cache.hits
```

## Troubleshooting

### DynamoDB Connection Issues
- Verify AWS credentials
- Check IAM permissions
- Ensure correct region in `application.properties`

### Secrets Manager Issues
- Verify secret exists: `aws secretsmanager describe-secret --secret-id devops-agent/projects/PROJECT_ID`
- Check IAM permissions for Secrets Manager

### Cache Issues
- Clear cache by toggling project
- Restart application to clear all caches

