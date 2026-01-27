# DevOps Agent

> 🔐 **NEW: JWT Authentication Enabled!** This application now includes comprehensive JWT authentication. See [JWT_DOCUMENTATION_INDEX.md](JWT_DOCUMENTATION_INDEX.md) for complete documentation.

A Spring Boot application built with Java 17 and Gradle that serves as a DevOps agent to monitor AWS resources and GitHub pull requests, providing unified visibility into AWS CodePipeline status, CloudWatch alarms, and GitHub PR status.

## 🔐 Authentication

This application now requires JWT authentication for all API endpoints (except `/api/auth/**` and `/actuator/**`).

**Quick Start:**
- Default admin user: `admin` / `admin123`
- Default user: `user` / `user123`
- See [JWT_QUICKSTART.md](JWT_QUICKSTART.md) for usage

**Complete Documentation:**
- [JWT_DOCUMENTATION_INDEX.md](JWT_DOCUMENTATION_INDEX.md) - Start here
- [JWT_AUTHENTICATION_GUIDE.md](JWT_AUTHENTICATION_GUIDE.md) - Complete API reference

## Features

- 🔐 **JWT Authentication** - Secure token-based authentication
- 🔒 **Role-Based Access Control** - ADMIN and USER roles
- ✅ Monitor AWS CodePipeline status and execution history
- ✅ Retrieve CloudWatch alarms and their states
- ✅ Fetch open GitHub pull requests with their status
- ✅ Fetch security vulnerabilities from AWS Inspector2
- ✅ RESTful API endpoints for easy integration
- ✅ Built with Spring Boot 3.2.0 and Java 17
- ✅ AWS SDK v2 integration
- ✅ GitHub API integration
- ✅ Automatic fallback to dummy data when AWS credentials are not configured

## Prerequisites

- Java 17 or higher
- Gradle 8.5 or higher (included via Gradle wrapper)
- AWS account with appropriate credentials configured (for AWS features)
- AWS CLI configured or environment variables set for AWS credentials
- Optional: GitHub Personal Access Token (for higher API rate limits)

## AWS Credentials Configuration

The application uses AWS SDK's default credential provider chain. You can configure credentials using any of these methods:

1. **Environment Variables:**
   ```bash
   export AWS_ACCESS_KEY_ID=your_access_key
   export AWS_SECRET_ACCESS_KEY=your_secret_key
   export AWS_REGION=us-east-1
   ```

2. **AWS Credentials File:** `~/.aws/credentials`
   ```
   [default]
   aws_access_key_id = your_access_key
   aws_secret_access_key = your_secret_key
   ```

3. **AWS Config File:** `~/.aws/config`
   ```
   [default]
   region = us-east-1
   ```

## GitHub Configuration

The application can fetch pull requests from GitHub repositories. Configuration options:

1. **Using Environment Variables:**
   ```bash
   export GITHUB_REPOSITORY_OWNER=sdc-pune
   export GITHUB_REPOSITORY_NAME=devOps-agent
   export GITHUB_TOKEN=your_github_personal_access_token  # Optional, for higher rate limits
   ```

2. **Using Application Properties:** Edit `src/main/resources/application.properties`
   ```properties
   github.repository.owner=sdc-pune
   github.repository.name=devOps-agent
   github.token=your_token  # Optional
   ```

**Note:** The GitHub token is optional. Without it, the application uses anonymous access with lower API rate limits (60 requests/hour). With authentication, you get 5000 requests/hour.

## Building the Application

```bash
# Build without tests
./gradlew clean build -x test

# Build with tests
./gradlew clean build
```

## Running the Application

### Option 1: Using Gradle
```bash
./gradlew bootRun
```

### Option 2: Using the JAR file
```bash
java -jar build/libs/devops-agent-1.0.0.jar
```

### Option 3: Using Docker
```bash
# Build the application first
./gradlew clean build -x test

# Build Docker image
docker build -t devops-agent:1.0.0 .

# Run the container
docker run -d \
  -p 8080:8080 \
  -e AWS_ACCESS_KEY_ID=your_access_key \
  -e AWS_SECRET_ACCESS_KEY=your_secret_key \
  -e AWS_REGION=us-east-1 \
  --name devops-agent \
  devops-agent:1.0.0
```

### Option 4: Using Docker Compose
```bash
# Build the application first
./gradlew clean build -x test

# Start with docker-compose
docker-compose up -d
```

The application will start on port 8080 by default.

## Running with Different Profiles

You can run the application with different Spring profiles:

```bash
# Development profile
java -jar build/libs/devops-agent-1.0.0.jar --spring.profiles.active=dev

# Production profile
java -jar build/libs/devops-agent-1.0.0.jar --spring.profiles.active=prod
```

## API Endpoints

### Pipeline Endpoints

#### Get All Pipelines
```bash
GET /api/pipelines
```
Returns a list of all AWS CodePipelines.

**Example:**
```bash
curl http://localhost:8080/api/pipelines
```

#### Get Pipeline Status
```bash
GET /api/pipelines/{pipelineName}
```
Returns the current status of a specific pipeline.

**Example:**
```bash
curl http://localhost:8080/api/pipelines/my-pipeline
```

#### Get Pipeline Execution History
```bash
GET /api/pipelines/{pipelineName}/history?maxResults=10
```
Returns the execution history of a specific pipeline.

**Parameters:**
- `maxResults` (optional, default: 10) - Maximum number of executions to return

**Example:**
```bash
curl http://localhost:8080/api/pipelines/my-pipeline/history?maxResults=5
```

### Alarm Endpoints

#### Get All Alarms
```bash
GET /api/alarms
```
Returns all CloudWatch alarms.

**Example:**
```bash
curl http://localhost:8080/api/alarms
```

#### Get Alarms by State
```bash
GET /api/alarms/state/{state}
```
Returns alarms filtered by state.

**Valid states:**
- `OK` - The alarm is in the OK state
- `ALARM` - The alarm is in the ALARM state
- `INSUFFICIENT_DATA` - The alarm has insufficient data

**Example:**
```bash
curl http://localhost:8080/api/alarms/state/ALARM
```

#### Get Alarm by Name
```bash
GET /api/alarms/{alarmName}
```
Returns details of a specific alarm.

**Example:**
```bash
curl http://localhost:8080/api/alarms/my-alarm
```

### Security Vulnerability Endpoints

#### Get All Vulnerabilities
```bash
GET /api/vulnerabilities
```
Returns all security vulnerabilities discovered by AWS Inspector2.

**Note:** If AWS credentials are not configured or the Inspector2 API fails, the service automatically falls back to dummy data for demonstration purposes.

**Example:**
```bash
curl http://localhost:8080/api/vulnerabilities
```

**Response:**
```json
[
  {
    "id": "CVE-2025-55754-001",
    "cveId": "CVE-2025-55754",
    "title": "Remote Code Execution in Tomcat Embed Core",
    "cwe": "CWE-94",
    "severity": "CRITICAL",
    "cvssScore": 9.8,
    "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
    "currentVersion": "9.0.65",
    "fixedVersion": "9.0.70",
    "affectedProjects": 2,
    "status": "Open"
  }
]
```

#### Get Vulnerability Details by ID
```bash
GET /api/vulnerabilities/{id}
```
Returns detailed information about a specific vulnerability.

**Example:**
```bash
curl http://localhost:8080/api/vulnerabilities/CVE-2025-55754-001
```

**Response:**
```json
{
  "id": "CVE-2025-55754-001",
  "cveId": "CVE-2025-55754",
  "title": "Remote Code Execution in Tomcat Embed Core",
  "cwe": "CWE-94",
  "severity": "CRITICAL",
  "cvssScore": 9.8,
  "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
  "currentVersion": "9.0.65",
  "fixedVersion": "9.0.70",
  "affectedProjects": 2,
  "status": "Open",
  "description": "A critical remote code execution vulnerability was discovered in Apache Tomcat Embed Core. An attacker can exploit this vulnerability to execute arbitrary code on the server. This affects all versions prior to 9.0.70.",
  "publishedDate": "2025-01-15T00:00:00Z",
  "references": [
    "https://nvd.nist.gov/vuln/detail/CVE-2025-55754",
    "https://tomcat.apache.org/security-9.html"
  ]
}
```

### Pull Request Endpoints

#### Get All Open Pull Requests
```bash
GET /api/pull-requests
```
Returns all open pull requests from the configured GitHub repository.

**Example:**
```bash
curl http://localhost:8080/api/pull-requests
```

**Response:**
```json
[
  {
    "number": 123,
    "title": "Add new feature",
    "state": "OPEN",
    "status": "SUCCESS",
    "author": "username",
    "url": "https://github.com/sdc-pune/devOps-agent/pull/123",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T15:45:00Z",
    "branch": "feature-branch",
    "baseBranch": "main"
  }
]
```

#### Get Specific Pull Request
```bash
GET /api/pull-requests/{number}
```
Returns details of a specific pull request by number.

**Example:**
```bash
curl http://localhost:8080/api/pull-requests/123
```

### Health Check
```bash
GET /actuator/health
```
Returns the health status of the application.

**Example:**
```bash
curl http://localhost:8080/actuator/health
```

## Configuration

You can customize the application by modifying `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# AWS Configuration
aws.region=us-east-1

# GitHub Configuration
github.repository.owner=sdc-pune
github.repository.name=devOps-agent
# github.token=<optional-token-for-higher-rate-limits>

# Logging Configuration
logging.level.root=INFO
logging.level.com.devops.agent=DEBUG
```

## Response Examples

### Pipeline Status Response
```json
{
  "pipelineName": "my-pipeline",
  "status": "Succeeded",
  "latestExecutionId": "12345678-1234-1234-1234-123456789012",
  "createdTime": "2024-01-01T10:00:00Z",
  "lastUpdatedTime": "2024-01-01T10:30:00Z"
}
```

### Alarm Response
```json
{
  "alarmName": "my-alarm",
  "alarmArn": "arn:aws:cloudwatch:us-east-1:123456789012:alarm:my-alarm",
  "stateValue": "ALARM",
  "stateReason": "Threshold Crossed: 1 datapoint was greater than the threshold",
  "metricName": "CPUUtilization",
  "namespace": "AWS/EC2",
  "updatedTimestamp": "2024-01-01T10:00:00Z"
}
```

## Technology Stack

- **Java 17** - LTS version of Java
- **Spring Boot 3.2.0** - Application framework
- **Gradle 8.5** - Build tool
- **AWS SDK v2** - AWS service integration
  - CodePipeline Client
  - CloudWatch Client
  - Inspector2 Client
- **GitHub API (Kohsuke)** - GitHub integration
- **Lombok** - Reduce boilerplate code

## Project Structure

```
devops-agent/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/devops/agent/
│   │   │   │   ├── DevOpsAgentApplication.java    # Main application class
│   │   │   │   ├── config/
│   │   │   │   │   ├── AwsConfig.java              # AWS client configuration
│   │   │   │   │   └── GitHubConfig.java           # GitHub client configuration
│   │   │   │   ├── controller/
│   │   │   │   │   ├── AlarmController.java        # Alarm REST endpoints
│   │   │   │   │   ├── PipelineController.java     # Pipeline REST endpoints
│   │   │   │   │   └── PullRequestController.java  # Pull Request REST endpoints
│   │   │   │   ├── model/
│   │   │   │   │   ├── AlarmResponse.java          # Alarm DTO
│   │   │   │   │   ├── PipelineStatusResponse.java # Pipeline DTO
│   │   │   │   │   └── PullRequestResponse.java    # Pull Request DTO
│   │   │   │   └── service/
│   │   │   │       ├── AlarmService.java           # CloudWatch alarm service
│   │   │   │       ├── PipelineService.java        # CodePipeline service
│   │   │   │       └── GitHubService.java          # GitHub service
│   │   │   └── com/example/security/
│   │   │       ├── model/
│   │   │       │   ├── VulnerabilitySummaryDto.java # Vulnerability summary DTO
│   │   │       │   └── VulnerabilityDetailDto.java  # Vulnerability detail DTO
│   │   │       └── service/
│   │   │           └── AwsInspectorService.java     # AWS Inspector2 service
│   │   └── resources/
│   │       └── application.properties          # Application configuration
│   └── test/
│       └── java/                               # Test classes
├── build.gradle                                 # Gradle build configuration
├── settings.gradle                              # Gradle settings
└── README.md                                    # This file
```

## IAM Permissions Required

The AWS credentials used must have the following permissions:

### For CodePipeline:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "codepipeline:ListPipelines",
        "codepipeline:GetPipelineState",
        "codepipeline:ListPipelineExecutions"
      ],
      "Resource": "*"
    }
  ]
}
```

### For CloudWatch:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "cloudwatch:DescribeAlarms"
      ],
      "Resource": "*"
    }
  ]
}
```

### For Inspector2:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "inspector2:ListFindings"
      ],
      "Resource": "*"
    }
  ]
}
```

**Note:** The AwsInspectorService automatically falls back to dummy data if AWS credentials are not configured or if the Inspector2 API fails, allowing the application to run in development/demo mode without AWS access.

## License

This project is open source and available under the MIT License.
