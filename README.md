# DevOps Agent

A Spring Boot application built with Java 17 and Gradle that serves as a DevOps agent to monitor AWS resources and GitHub pull requests, providing unified visibility into AWS CodePipeline status, CloudWatch alarms, and GitHub PR status.

## Features

- ✅ Monitor AWS CodePipeline status and execution history
- ✅ Retrieve CloudWatch alarms and their states
- ✅ Fetch AWS security vulnerabilities from Security Hub
- ✅ Retrieve and summarize CloudWatch logs with statistics
- ✅ Fetch open GitHub pull requests with their status
- ✅ RESTful API endpoints for easy integration
- ✅ Built with Spring Boot 3.2.0 and Java 17
- ✅ AWS SDK v2 integration
- ✅ GitHub API integration

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

### Vulnerability Endpoints

#### Get All Vulnerabilities
```bash
GET /api/vulnerabilities
```
Returns all active security findings from AWS Security Hub.

**Example:**
```bash
curl http://localhost:8080/api/vulnerabilities
```

**Response:**
```json
[
  {
    "findingId": "arn:aws:securityhub:us-east-1:123456789012:subscription/...",
    "title": "EC2 instance has unrestricted SSH access",
    "severity": "CRITICAL",
    "status": "NEW",
    "description": "Security group allows SSH access from 0.0.0.0/0",
    "resourceType": "AwsEc2Instance",
    "resourceIds": ["i-1234567890abcdef0"],
    "firstObservedAt": "2024-01-15T10:00:00Z",
    "lastObservedAt": "2024-01-15T15:30:00Z",
    "remediationText": "Restrict SSH access to specific IP ranges",
    "complianceStatus": "FAILED"
  }
]
```

#### Get Vulnerabilities by Severity
```bash
GET /api/vulnerabilities/severity/{severity}
```
Returns vulnerabilities filtered by severity level.

**Valid severities:**
- `CRITICAL` - Critical security issues
- `HIGH` - High severity issues
- `MEDIUM` - Medium severity issues
- `LOW` - Low severity issues
- `INFORMATIONAL` - Informational findings

**Example:**
```bash
curl http://localhost:8080/api/vulnerabilities/severity/CRITICAL
```

#### Get Vulnerability by ID
```bash
GET /api/vulnerabilities/{findingId}
```
Returns details of a specific vulnerability finding.

**Example:**
```bash
curl "http://localhost:8080/api/vulnerabilities/arn:aws:securityhub:us-east-1:123456789012:subscription/..."
```

### CloudWatch Logs Endpoints

#### Get All Log Groups
```bash
GET /api/logs/groups
```
Returns a list of all CloudWatch log groups.

**Example:**
```bash
curl http://localhost:8080/api/logs/groups
```

**Response:**
```json
[
  "/aws/lambda/my-function",
  "/aws/ecs/my-service",
  "/aws/apigateway/my-api"
]
```

#### Get Log Streams
```bash
GET /api/logs/groups/{logGroupName}/streams
```
Returns the most recent log streams for a specific log group.

**Example:**
```bash
curl "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/streams"
```

**Note:** Log group names with forward slashes should be URL-encoded (e.g., `/aws/lambda/my-function` becomes `%2Faws%2Flambda%2Fmy-function`).

#### Get Log Summary for Stream
```bash
GET /api/logs/groups/{logGroupName}/streams/{logStreamName}/summary?hours=24
```
Returns a summarized view of logs with statistics for a specific log stream.

**Parameters:**
- `hours` (optional, default: 24) - Number of hours to look back

**Example:**
```bash
curl "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/streams/2024%2F01%2F15%2Fstream-1/summary?hours=12"
```

**Response:**
```json
{
  "logGroupName": "/aws/lambda/my-function",
  "logStreamName": "2024/01/15/stream-1",
  "totalEvents": 150,
  "startTime": "2024-01-15T10:00:00Z",
  "endTime": "2024-01-15T22:00:00Z",
  "statistics": {
    "errorCount": 5,
    "warningCount": 12,
    "infoCount": 120,
    "totalCount": 150
  },
  "events": [
    {
      "timestamp": "2024-01-15T15:30:00Z",
      "message": "ERROR: Connection timeout to database",
      "level": "ERROR"
    },
    {
      "timestamp": "2024-01-15T15:29:00Z",
      "message": "INFO: Processing request completed successfully",
      "level": "INFO"
    }
  ]
}
```

#### Get Log Summary for Log Group
```bash
GET /api/logs/groups/{logGroupName}/summary?hours=24
```
Returns a summarized view of logs from the most recent stream in the log group.

**Parameters:**
- `hours` (optional, default: 24) - Number of hours to look back

**Example:**
```bash
curl "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/summary?hours=6"
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
  - CloudWatch Logs Client
  - Security Hub Client
  - Inspector2 Client
- **GitHub API (Kohsuke)** - GitHub integration
- **Lombok** - Reduce boilerplate code

## Project Structure

```
devops-agent/
├── src/
│   ├── main/
│   │   ├── java/com/devops/agent/
│   │   │   ├── DevOpsAgentApplication.java    # Main application class
│   │   │   ├── config/
│   │   │   │   ├── AwsConfig.java              # AWS client configuration
│   │   │   │   └── GitHubConfig.java           # GitHub client configuration
│   │   │   ├── controller/
│   │   │   │   ├── AlarmController.java        # Alarm REST endpoints
│   │   │   │   ├── LogController.java          # CloudWatch Logs REST endpoints
│   │   │   │   ├── PipelineController.java     # Pipeline REST endpoints
│   │   │   │   ├── PullRequestController.java  # Pull Request REST endpoints
│   │   │   │   └── VulnerabilityController.java # Vulnerability REST endpoints
│   │   │   ├── model/
│   │   │   │   ├── AlarmResponse.java          # Alarm DTO
│   │   │   │   ├── LogSummaryResponse.java     # Log summary DTO
│   │   │   │   ├── PipelineStatusResponse.java # Pipeline DTO
│   │   │   │   ├── PullRequestResponse.java    # Pull Request DTO
│   │   │   │   └── VulnerabilityResponse.java  # Vulnerability DTO
│   │   │   └── service/
│   │   │       ├── AlarmService.java           # CloudWatch alarm service
│   │   │       ├── LogService.java             # CloudWatch Logs service
│   │   │       ├── PipelineService.java        # CodePipeline service
│   │   │       ├── GitHubService.java          # GitHub service
│   │   │       └── VulnerabilityService.java   # Security Hub service
│   │   └── resources/
│   │       └── application.properties          # Application configuration
│   └── test/
│       └── java/com/devops/agent/              # Test classes
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

### For CloudWatch Logs:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams",
        "logs:GetLogEvents"
      ],
      "Resource": "*"
    }
  ]
}
```

### For Security Hub:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "securityhub:GetFindings"
      ],
      "Resource": "*"
    }
  ]
}
```

**Note:** AWS Security Hub must be enabled in your AWS account to use the vulnerability endpoints. Additionally, ensure you have appropriate security standards enabled (e.g., AWS Foundational Security Best Practices, CIS AWS Foundations Benchmark) to generate findings.

## License

This project is open source and available under the MIT License.
