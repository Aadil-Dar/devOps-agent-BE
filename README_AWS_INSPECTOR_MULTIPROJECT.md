# AWS Inspector Multi-Project Support - Quick Start

## Overview

The AWS Inspector API now supports **multi-project configurations**, allowing you to manage and scan vulnerabilities across multiple AWS accounts using project-specific credentials.

## Quick Start

### 1. Create a Project

```bash
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "My Production Project",
    "githubOwner": "myorganization",
    "githubRepo": "my-repo",
    "githubToken": "ghp_your_github_token",
    "awsRegion": "eu-west-1",
    "awsAccessKey": "AKIAIOSFODNN7EXAMPLE",
    "awsSecretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    "createdBy": "admin@example.com"
  }'
```

**Response:**
```json
{
  "projectId": "abc-123-def-456",
  "projectName": "My Production Project",
  "githubOwner": "myorganization",
  "githubRepo": "my-repo",
  "awsRegion": "eu-west-1",
  "enabled": true,
  "message": "Project created successfully"
}
```

### 2. Scan Vulnerabilities for the Project

```bash
# Using the projectId from above
curl "http://localhost:8080/api/vulnerabilities?projectId=abc-123-def-456"
```

### 3. Get Details of a Specific Vulnerability

```bash
curl "http://localhost:8080/api/vulnerabilities/CVE-2023-1234?projectId=abc-123-def-456"
```

## Key Features

✅ **Multi-Tenancy** - Support multiple AWS accounts simultaneously  
✅ **Secure** - Credentials stored in AWS Secrets Manager  
✅ **Backward Compatible** - Works with or without projectId  
✅ **Flexible** - Different regions per project  
✅ **Easy to Use** - Just add `?projectId={id}` to any vulnerability endpoint  

## API Endpoints

| Endpoint | Method | Parameters | Description |
|----------|--------|------------|-------------|
| `/api/vulnerabilities` | GET | `projectId` (optional) | List all vulnerabilities |
| `/api/vulnerabilities/{id}` | GET | `projectId` (optional) | Get vulnerability details |
| `/api/admin/projects/upload` | POST | Project JSON | Create new project |
| `/api/admin/projects` | GET | - | List all projects |
| `/api/admin/projects/{id}` | GET | - | Get project details |
| `/api/admin/projects/{id}` | PUT | Project JSON | Update project |
| `/api/admin/projects/{id}` | DELETE | - | Delete project |
| `/api/admin/projects/{id}/toggle` | PATCH | - | Enable/disable project |

## Testing

Run the automated test script:

```bash
./test-inspector-multiproject.sh
```

This will:
1. Test backward compatibility (without projectId)
2. Create a test project
3. Fetch vulnerabilities for that project
4. Clean up (optional)

## Architecture

```
User Request with projectId
    ↓
Controller → Service
    ↓
Fetch from DynamoDB (project config)
    ↓
Fetch from Secrets Manager (AWS credentials)
    ↓
Create project-specific Inspector2Client
    ↓
Call AWS Inspector API
    ↓
Return vulnerabilities
```

## Data Storage

### DynamoDB (Non-Sensitive)
- Project name
- GitHub owner/repo
- AWS region
- Enabled status
- Metadata

### AWS Secrets Manager (Sensitive)
- GitHub token
- AWS access key
- AWS secret key

## Example Workflows

### Workflow 1: Default Credentials (Backward Compatible)
```bash
# No projectId - uses default AWS credentials
curl http://localhost:8080/api/vulnerabilities
```

### Workflow 2: Project-Specific Credentials
```bash
# Step 1: Create project
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{...}' | jq -r '.projectId')

# Step 2: Use project credentials
curl "http://localhost:8080/api/vulnerabilities?projectId=$PROJECT_ID"
```

### Workflow 3: Multiple Projects
```bash
# Project A (Production in eu-west-1)
curl "http://localhost:8080/api/vulnerabilities?projectId=project-a-id"

# Project B (Staging in us-east-1)
curl "http://localhost:8080/api/vulnerabilities?projectId=project-b-id"

# Project C (Development in ap-southeast-1)
curl "http://localhost:8080/api/vulnerabilities?projectId=project-c-id"
```

## Security Best Practices

1. **Never commit credentials** - They're stored in AWS Secrets Manager
2. **Use IAM roles** where possible instead of access keys
3. **Rotate credentials regularly** via the update endpoint
4. **Enable only active projects** - Disable unused projects
5. **Audit logs** - All operations are logged with projectId

## Troubleshooting

### "Project not found"
- Verify the projectId exists: `GET /api/admin/projects/{projectId}`
- Check if the project was deleted

### "Project is disabled"
- Enable the project: `PATCH /api/admin/projects/{projectId}/toggle`

### "No vulnerabilities found"
- Verify AWS Inspector is enabled in the target AWS account
- Check the AWS region configuration
- Verify the AWS credentials have Inspector permissions
- Ensure findings exist in the specified region

### "Invalid credentials"
- Update the project credentials: `PUT /api/admin/projects/{projectId}`
- Verify the AWS access/secret keys are correct

## Documentation

For more details, see:
- **AWS_INSPECTOR_MULTI_PROJECT.md** - Complete user guide
- **IMPLEMENTATION_CHANGES.md** - Technical implementation details
- **AWS_INSPECTOR_ARCHITECTURE.md** - Architecture diagrams
- **IMPLEMENTATION_COMPLETE.md** - Implementation summary

## Support

For issues or questions, please:
1. Check the documentation files above
2. Review the logs for detailed error messages
3. Run the test script to verify setup
4. Contact the DevOps team

---

**Status:** ✅ Production Ready  
**Version:** 1.0.0  
**Last Updated:** December 20, 2024

