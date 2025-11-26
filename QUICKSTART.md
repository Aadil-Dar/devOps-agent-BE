# Quick Start Guide

This guide will help you quickly get started with the DevOps Agent application.

## Step 1: Prerequisites

Ensure you have:
- Java 21 installed
- AWS credentials configured
- Access to AWS CodePipeline and CloudWatch

## Step 2: Build the Application

```bash
./gradlew clean build -x test
```

## Step 3: Configure AWS Credentials

Set your AWS credentials as environment variables:

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=us-east-1
```

Or configure them in `~/.aws/credentials` file.

## Step 4: Start the Application

```bash
java -jar build/libs/devops-agent-1.0.0.jar
```

The application will start on http://localhost:8080

## Step 5: Test the Endpoints

### Check application health
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### List all pipelines
```bash
curl http://localhost:8080/api/pipelines
```

### Get specific pipeline status
```bash
curl http://localhost:8080/api/pipelines/your-pipeline-name
```

### Get all alarms
```bash
curl http://localhost:8080/api/alarms
```

### Get alarms in ALARM state
```bash
curl http://localhost:8080/api/alarms/state/ALARM
```

## Step 6: Customize Configuration

Edit `src/main/resources/application.properties` to customize:
- Server port
- AWS region
- Logging levels

Example:
```properties
server.port=8081
aws.region=eu-west-1
logging.level.com.devops.agent=INFO
```

Then rebuild and restart the application.

## Troubleshooting

### Issue: AWS credentials not found
**Solution:** Ensure AWS credentials are properly configured using AWS CLI or environment variables.

### Issue: Access denied to AWS resources
**Solution:** Ensure your IAM user/role has the required permissions for CodePipeline and CloudWatch (see README.md for required permissions).

### Issue: Port 8080 already in use
**Solution:** Change the port in `application.properties` or stop the process using port 8080.

## Next Steps

- Integrate the API with your monitoring dashboard
- Set up automated polling for alarm states
- Create alerts based on pipeline status
- Deploy the application to AWS EC2 or ECS
