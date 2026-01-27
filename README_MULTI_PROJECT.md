# DevOps Agent - Multi-Project Backend

A Spring Boot application that supports multiple DevOps projects with isolated configurations and credentials stored securely in AWS DynamoDB and Secrets Manager.

## 🎯 Features

- ✅ **Multi-Project Support**: Manage multiple projects with separate configurations
- ✅ **Secure Credential Storage**: AWS Secrets Manager for sensitive data
- ✅ **Fast Configuration Access**: DynamoDB + Caffeine caching
- ✅ **Admin APIs**: Complete CRUD operations for project management
- ✅ **Worker APIs**: Project-specific DevOps operations
- ✅ **Auto-initialization**: Automatic DynamoDB table creation
- ✅ **RESTful APIs**: Well-documented endpoints

## 🏗️ Architecture

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

## 📋 Prerequisites

- Java 17 or higher (tested with Java 23)
- AWS Account with:
  - DynamoDB access
  - Secrets Manager access
  - Appropriate IAM permissions
- AWS CLI configured (optional, for manual testing)

## 🚀 Quick Start

### 1. Configure AWS Credentials

Set up your AWS credentials:

```bash
# Option 1: Environment variables
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=eu-west-1

# Option 2: AWS CLI (recommended)
aws configure
```

### 2. Update Configuration

Edit `src/main/resources/application.properties`:

```properties
aws.region=eu-west-1
aws.dynamodb.table-name=devops-projects
aws.secrets-manager.prefix=devops-agent/projects/
```

### 3. Build the Application

```bash
./gradlew clean build
```

### 4. Run the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### 5. Initialize Database

```bash
curl -X POST http://localhost:8080/api/admin/system/init-db
```

Response:
```json
{
  "status": "success",
  "message": "DynamoDB table initialized successfully"
}
```

## 📚 API Documentation

### System Admin APIs

#### Health Check
```bash
GET /api/admin/system/health
```

#### Initialize Database
```bash
POST /api/admin/system/init-db
```

### Project Management APIs

#### Create Project
```bash
POST /api/admin/projects/upload
Content-Type: application/json

{
  "projectName": "My Project",
  "githubOwner": "myorg",
  "githubRepo": "myrepo",
  "awsRegion": "eu-west-1",
  "createdBy": "admin",
  "githubToken": "ghp_xxxxx",
  "awsAccessKey": "AKIAXXXXX",
  "awsSecretKey": "xxxxx"
}
```

#### List All Projects
```bash
GET /api/admin/projects
```

#### Get Project by ID
```bash
GET /api/admin/projects/{projectId}
```

#### Update Project
```bash
PUT /api/admin/projects/{projectId}
Content-Type: application/json

{
  "projectName": "Updated Name",
  "githubOwner": "neworg",
  "githubRepo": "newrepo",
  "githubToken": "ghp_new_token"
}
```

#### Enable/Disable Project
```bash
PATCH /api/admin/projects/{projectId}/toggle
```

#### Validate Project
```bash
POST /api/admin/projects/{projectId}/validate
```

#### Delete Project
```bash
DELETE /api/admin/projects/{projectId}
```

### Project Operations APIs

#### Get Project Info
```bash
GET /api/v1/projects/{projectId}/info
```

#### Execute Operation
```bash
POST /api/v1/projects/{projectId}/execute
Content-Type: application/json

{
  "operation": "check-pipeline",
  "parameters": {}
}
```

#### Get GitHub Credentials
```bash
GET /api/v1/projects/{projectId}/github-credentials
```

## 📖 Usage Examples

### Example 1: Create and Use a Project

```bash
# 1. Create a project
PROJECT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "SHOMA UI",
    "githubOwner": "sdc-pune",
    "githubRepo": "shoma-ui",
    "awsRegion": "eu-west-1",
    "githubToken": "ghp_xxxxx"
  }')

# 2. Extract project ID
PROJECT_ID=$(echo $PROJECT_RESPONSE | jq -r '.projectId')
echo "Created project: $PROJECT_ID"

# 3. Get project info
curl http://localhost:8080/api/v1/projects/$PROJECT_ID/info

# 4. Execute operation
curl -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/execute \
  -H "Content-Type: application/json" \
  -d '{"operation": "check-status"}'
```

### Example 2: Import from Postman

Import the included `postman_collection.json` file into Postman for easy testing.

## 🔧 Integrating with Existing Controllers

Update your existing controllers to use multi-project configuration:

```java
@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    @Autowired
    private ProjectWorkerService workerService;

    @GetMapping("/{projectId}/status")
    public ResponseEntity<?> getPipelineStatus(@PathVariable String projectId) {
        // Get project-specific configuration
        FullProjectConfig config = workerService.getFullProjectConfig(projectId);
        
        // Use project-specific AWS credentials
        String awsRegion = config.getAwsRegion();
        String awsAccessKey = config.getAwsAccessKey();
        
        // Your existing logic here...
        return ResponseEntity.ok(status);
    }
}
```

## 🗂️ Project Structure

```
src/main/java/com/devops/agent/
├── config/                      # Configuration classes
│   ├── AwsConfig.java          # AWS service clients
│   ├── CacheConfig.java        # Caffeine cache setup
│   ├── DynamoDbConfig.java     # DynamoDB configuration
│   └── SecretsManagerConfig.java
├── controller/                  # REST controllers
│   ├── AdminProjectController.java       # Project CRUD APIs
│   ├── ProjectOperationsController.java  # Worker APIs
│   └── SystemAdminController.java        # System admin APIs
├── model/                      # Data models
│   ├── FullProjectConfig.java
│   ├── ProjectConfiguration.java
│   ├── ProjectResponse.java
│   └── ProjectUploadRequest.java
└── service/                    # Business logic
    ├── DynamoDbInitService.java
    ├── ProjectConfigurationService.java
    ├── ProjectWorkerService.java
    └── SecretsManagerService.java
```

## 🔒 Security

### AWS IAM Permissions Required

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
        "dynamodb:Query",
        "dynamodb:CreateTable",
        "dynamodb:DescribeTable"
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

### Best Practices

1. ✅ Never commit credentials to git
2. ✅ Use IAM roles when deploying to EC2/ECS
3. ✅ Enable encryption at rest for DynamoDB
4. ✅ Rotate credentials regularly
5. ✅ Use HTTPS in production
6. ✅ Implement authentication for admin endpoints

## 🐛 Troubleshooting

### Build Issues

```bash
# Clean build
./gradlew clean build --refresh-dependencies

# Check Java version
java -version  # Should be Java 17+
```

### AWS Connection Issues

```bash
# Test AWS credentials
aws sts get-caller-identity

# Test DynamoDB access
aws dynamodb list-tables --region eu-west-1

# Test Secrets Manager access
aws secretsmanager list-secrets --region eu-west-1
```

### Cache Issues

```bash
# View cache statistics
curl http://localhost:8080/actuator/metrics/cache.gets
curl http://localhost:8080/actuator/metrics/cache.hits

# Clear cache by toggling a project
curl -X PATCH http://localhost:8080/api/admin/projects/{projectId}/toggle
```

## 📊 Monitoring

The application exposes Spring Boot Actuator endpoints:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Cache statistics
curl http://localhost:8080/actuator/metrics/cache.gets
```

## 🚀 Deployment

### Deploy to AWS ECS

See [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md) for detailed deployment instructions.

### Environment Variables

```bash
# Required
AWS_REGION=eu-west-1
AWS_DYNAMODB_TABLE_NAME=devops-projects
AWS_SECRETS_MANAGER_PREFIX=devops-agent/projects/

# Optional
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

## 📝 Additional Documentation

- [MULTI_PROJECT_SETUP.md](MULTI_PROJECT_SETUP.md) - Detailed setup guide
- [API_EXAMPLES.md](API_EXAMPLES.md) - More API examples
- [postman_collection.json](postman_collection.json) - Postman collection

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 License

This project is proprietary software.

## 📞 Support

For issues or questions:
- Check the troubleshooting section
- Review the API documentation
- Contact the DevOps team

---

**Version:** 1.0.0  
**Last Updated:** December 2024  
**Status:** ✅ Production Ready

