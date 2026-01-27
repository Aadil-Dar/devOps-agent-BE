# Quick Reference Card

## 🚀 Quick Start (5 Minutes)

```bash
# 1. Build
./gradlew clean build

# 2. Run
./gradlew bootRun

# 3. Initialize DB
curl -X POST http://localhost:8080/api/admin/system/init-db

# 4. Create project
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d '{"projectName":"My Project","githubOwner":"org","githubRepo":"repo","githubToken":"ghp_xxx"}'

# 5. Test
./test-api.sh
```

## 📋 Essential Commands

### Build & Run
```bash
./gradlew clean build          # Build project
./gradlew bootRun              # Run application
./gradlew test                 # Run tests
```

### API Testing
```bash
# Health check
curl http://localhost:8080/actuator/health

# List projects
curl http://localhost:8080/api/admin/projects

# Get project
curl http://localhost:8080/api/admin/projects/{projectId}

# Create project
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d @project.json
```

## 🔑 Key Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/system/init-db` | POST | Initialize database |
| `/api/admin/projects/upload` | POST | Create project |
| `/api/admin/projects` | GET | List all projects |
| `/api/admin/projects/{id}` | GET | Get project |
| `/api/admin/projects/{id}` | PUT | Update project |
| `/api/admin/projects/{id}` | DELETE | Delete project |
| `/api/v1/projects/{id}/info` | GET | Get project info |

## 📁 Project Structure

```
src/main/java/com/devops/agent/
├── config/          # AWS, DynamoDB, Secrets, Cache
├── controller/      # REST APIs
├── model/           # DTOs and entities
└── service/         # Business logic
```

## 🔧 Configuration

### application.properties
```properties
aws.region=eu-west-1
aws.dynamodb.table-name=devops-projects
aws.secrets-manager.prefix=devops-agent/projects/
spring.cache.type=caffeine
```

### Environment Variables
```bash
export AWS_REGION=eu-west-1
export AWS_ACCESS_KEY_ID=xxx
export AWS_SECRET_ACCESS_KEY=xxx
```

## 💾 Data Storage

### DynamoDB (Non-sensitive)
- projectId (PK)
- projectName
- githubOwner
- githubRepo
- awsRegion
- enabled
- timestamps

### Secrets Manager (Sensitive)
- github-token
- aws-access-key
- aws-secret-key

## 🔄 Common Workflows

### Add New Project
```bash
# 1. Prepare JSON
cat > project.json << EOF
{
  "projectName": "New Project",
  "githubOwner": "myorg",
  "githubRepo": "myrepo",
  "githubToken": "ghp_xxx",
  "awsRegion": "eu-west-1"
}
EOF

# 2. Upload
curl -X POST http://localhost:8080/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d @project.json

# 3. Get project ID from response
# 4. Use in your code
```

### Use in Controller
```java
@Autowired
private ProjectWorkerService workerService;

@GetMapping("/{projectId}/data")
public ResponseEntity<?> getData(@PathVariable String projectId) {
    FullProjectConfig config = workerService.getFullProjectConfig(projectId);
    String token = config.getGithubToken();
    // Use token...
}
```

### Update Project Credentials
```bash
curl -X PUT http://localhost:8080/api/admin/projects/{projectId} \
  -H "Content-Type: application/json" \
  -d '{"githubToken":"ghp_new_token"}'
```

## 🐛 Troubleshooting

### Build Fails
```bash
# Check Java version
java -version  # Need 17+

# Clean build
./gradlew clean build --refresh-dependencies
```

### AWS Connection Issues
```bash
# Test credentials
aws sts get-caller-identity

# Check region
echo $AWS_REGION
```

### Cache Issues
```bash
# View cache stats
curl http://localhost:8080/actuator/metrics/cache.gets

# Clear cache (toggle project)
curl -X PATCH http://localhost:8080/api/admin/projects/{id}/toggle
curl -X PATCH http://localhost:8080/api/admin/projects/{id}/toggle
```

## 📊 Monitoring

```bash
# Health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Cache stats
curl http://localhost:8080/actuator/metrics/cache.hits
curl http://localhost:8080/actuator/metrics/cache.misses
```

## 🔐 AWS Permissions Required

### DynamoDB
- dynamodb:PutItem
- dynamodb:GetItem
- dynamodb:UpdateItem
- dynamodb:DeleteItem
- dynamodb:Scan
- dynamodb:CreateTable

### Secrets Manager
- secretsmanager:CreateSecret
- secretsmanager:GetSecretValue
- secretsmanager:UpdateSecret
- secretsmanager:DeleteSecret

## 📝 Sample Project JSON

```json
{
  "projectName": "SHOMA UI",
  "githubOwner": "sdc-pune",
  "githubRepo": "shoma-ui",
  "awsRegion": "eu-west-1",
  "createdBy": "admin",
  "githubToken": "ghp_xxxxx",
  "awsAccessKey": "AKIA...",
  "awsSecretKey": "xxx..."
}
```

## 🎯 Code Snippets

### Get Project Config
```java
FullProjectConfig config = workerService.getFullProjectConfig(projectId);
```

### Get GitHub Credentials Only
```java
GitHubCredentials creds = workerService.getGitHubCredentials(projectId);
String owner = creds.owner;
String repo = creds.repo;
String token = creds.token;
```

### Get AWS Credentials Only
```java
AwsCredentials creds = workerService.getAwsCredentials(projectId);
String region = creds.region;
String accessKey = creds.accessKey;
String secretKey = creds.secretKey;
```

## 🚀 Deployment

### Local
```bash
./gradlew bootRun
```

### Docker
```bash
docker build -t devops-agent .
docker run -p 8080:8080 devops-agent
```

### AWS ECS
See `AWS_DEPLOYMENT.md`

## 📚 Documentation Files

- `README_MULTI_PROJECT.md` - Main README
- `MULTI_PROJECT_SETUP.md` - Setup guide
- `AWS_DEPLOYMENT.md` - Deployment guide
- `ARCHITECTURE_DIAGRAMS.md` - Visual diagrams
- `IMPLEMENTATION_SUMMARY.md` - What was implemented
- `postman_collection.json` - Postman collection
- `test-api.sh` - Test script

## 💡 Tips

1. Always initialize DB first: `POST /api/admin/system/init-db`
2. Use Postman collection for testing
3. Enable debug logging: `logging.level.com.devops.agent=DEBUG`
4. Check cache stats regularly
5. Use IAM roles in production (no access keys)
6. Rotate credentials regularly
7. Monitor CloudWatch logs
8. Set up alarms for errors

## 🆘 Getting Help

1. Check troubleshooting section
2. Review logs: `tail -f logs/application.log`
3. Test with curl or Postman
4. Verify AWS credentials
5. Check IAM permissions

## 📞 Quick Links

- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Projects: http://localhost:8080/api/admin/projects
- Test Script: `./test-api.sh`

---

**Keep this card handy for daily development!**

