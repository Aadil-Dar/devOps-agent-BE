# DevOps Agent

A Spring Boot application built with Java 21 and Gradle that serves as a DevOps agent to monitor AWS resources, specifically AWS CodePipeline status and CloudWatch alarms.

## Features

- ✅ Monitor AWS CodePipeline status and execution history
- ✅ Retrieve CloudWatch alarms and their states
- ✅ RESTful API endpoints for easy integration
- ✅ Built with Spring Boot 3.2.0 and Java 21
- ✅ AWS SDK v2 integration

## Prerequisites

- Java 21 or higher
- Gradle 8.5 or higher (included via Gradle wrapper)
- AWS account with appropriate credentials configured
- AWS CLI configured or environment variables set for AWS credentials

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

## Building the Application

```bash
# Build without tests
./gradlew clean build -x test

# Build with tests
./gradlew clean build
```

## Running the Application

```bash
# Using Gradle
./gradlew bootRun

# Using the JAR file
java -jar build/libs/devops-agent-1.0.0.jar
```

The application will start on port 8080 by default.

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

- **Java 21** - Latest LTS version of Java
- **Spring Boot 3.2.0** - Application framework
- **Gradle 8.5** - Build tool
- **AWS SDK v2** - AWS service integration
  - CodePipeline Client
  - CloudWatch Client
- **Lombok** - Reduce boilerplate code

## Project Structure

```
devops-agent/
├── src/
│   ├── main/
│   │   ├── java/com/devops/agent/
│   │   │   ├── DevOpsAgentApplication.java    # Main application class
│   │   │   ├── config/
│   │   │   │   └── AwsConfig.java              # AWS client configuration
│   │   │   ├── controller/
│   │   │   │   ├── AlarmController.java        # Alarm REST endpoints
│   │   │   │   └── PipelineController.java     # Pipeline REST endpoints
│   │   │   ├── model/
│   │   │   │   ├── AlarmResponse.java          # Alarm DTO
│   │   │   │   └── PipelineStatusResponse.java # Pipeline DTO
│   │   │   └── service/
│   │   │       ├── AlarmService.java           # CloudWatch alarm service
│   │   │       └── PipelineService.java        # CodePipeline service
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

## License

This project is open source and available under the MIT License.
