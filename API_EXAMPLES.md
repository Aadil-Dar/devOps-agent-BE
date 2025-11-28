# API Examples

This file contains example API calls for the DevOps Agent application.

## Prerequisites
- Ensure the application is running (default port: 8080)
- Configure AWS credentials properly

## Health Check

### Check Application Health
```bash
curl -X GET http://localhost:8080/actuator/health
```

Expected Response:
```json
{
  "status": "UP"
}
```

### Get Application Info
```bash
curl -X GET http://localhost:8080/actuator/info
```

## Pipeline APIs

### 1. List All Pipelines
```bash
curl -X GET http://localhost:8080/api/pipelines
```

Expected Response:
```json
[
  {
    "pipelineName": "my-pipeline",
    "status": null,
    "latestExecutionId": null,
    "createdTime": "2024-01-01T10:00:00Z",
    "lastUpdatedTime": "2024-01-01T10:30:00Z"
  }
]
```

### 2. Get Pipeline Status
Replace `{pipelineName}` with your actual pipeline name.

```bash
curl -X GET http://localhost:8080/api/pipelines/my-pipeline
```

Expected Response:
```json
{
  "pipelineName": "my-pipeline",
  "status": "Succeeded",
  "latestExecutionId": "12345678-1234-1234-1234-123456789012",
  "createdTime": null,
  "lastUpdatedTime": "2024-01-01T10:30:00Z"
}
```

### 3. Get Pipeline Execution History
Get the last 10 executions (default):
```bash
curl -X GET http://localhost:8080/api/pipelines/my-pipeline/history
```

Get the last 5 executions:
```bash
curl -X GET "http://localhost:8080/api/pipelines/my-pipeline/history?maxResults=5"
```

Expected Response:
```json
[
  {
    "pipelineName": "my-pipeline",
    "status": "Succeeded",
    "latestExecutionId": "exec-123",
    "createdTime": "2024-01-01T10:00:00Z",
    "lastUpdatedTime": "2024-01-01T10:30:00Z"
  },
  {
    "pipelineName": "my-pipeline",
    "status": "Failed",
    "latestExecutionId": "exec-122",
    "createdTime": "2024-01-01T09:00:00Z",
    "lastUpdatedTime": "2024-01-01T09:15:00Z"
  }
]
```

## Alarm APIs

### 1. List All Alarms
```bash
curl -X GET http://localhost:8080/api/alarms
```

Expected Response:
```json
[
  {
    "alarmName": "my-alarm",
    "alarmArn": "arn:aws:cloudwatch:us-east-1:123456789012:alarm:my-alarm",
    "stateValue": "OK",
    "stateReason": "Threshold Crossed",
    "metricName": "CPUUtilization",
    "namespace": "AWS/EC2",
    "updatedTimestamp": "2024-01-01T10:00:00Z"
  }
]
```

### 2. Get Alarms by State

#### Get Alarms in ALARM State
```bash
curl -X GET http://localhost:8080/api/alarms/state/ALARM
```

#### Get Alarms in OK State
```bash
curl -X GET http://localhost:8080/api/alarms/state/OK
```

#### Get Alarms with Insufficient Data
```bash
curl -X GET http://localhost:8080/api/alarms/state/INSUFFICIENT_DATA
```

Expected Response:
```json
[
  {
    "alarmName": "high-cpu-alarm",
    "alarmArn": "arn:aws:cloudwatch:us-east-1:123456789012:alarm:high-cpu-alarm",
    "stateValue": "ALARM",
    "stateReason": "Threshold Crossed: 1 datapoint was greater than the threshold",
    "metricName": "CPUUtilization",
    "namespace": "AWS/EC2",
    "updatedTimestamp": "2024-01-01T10:00:00Z"
  }
]
```

### 3. Get Specific Alarm by Name
Replace `{alarmName}` with your actual alarm name.

```bash
curl -X GET http://localhost:8080/api/alarms/my-alarm
```

Expected Response:
```json
{
  "alarmName": "my-alarm",
  "alarmArn": "arn:aws:cloudwatch:us-east-1:123456789012:alarm:my-alarm",
  "stateValue": "OK",
  "stateReason": "Threshold Crossed",
  "metricName": "CPUUtilization",
  "namespace": "AWS/EC2",
  "updatedTimestamp": "2024-01-01T10:00:00Z"
}
```

## Error Responses

### Pipeline Not Found
```json
HTTP 404 Not Found
```

### Alarm Not Found
```json
HTTP 404 Not Found
```

### Invalid State Parameter
```json
HTTP 400 Bad Request
```

### AWS Service Error
```json
HTTP 500 Internal Server Error
```

## Vulnerability APIs

### 1. List All Vulnerabilities
```bash
curl -X GET http://localhost:8080/api/vulnerabilities
```

Expected Response:
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

### 2. Get Vulnerabilities by Severity

#### Get Critical Vulnerabilities
```bash
curl -X GET http://localhost:8080/api/vulnerabilities/severity/CRITICAL
```

#### Get High Severity Vulnerabilities
```bash
curl -X GET http://localhost:8080/api/vulnerabilities/severity/HIGH
```

#### Get Medium Severity Vulnerabilities
```bash
curl -X GET http://localhost:8080/api/vulnerabilities/severity/MEDIUM
```

Expected Response:
```json
[
  {
    "findingId": "arn:aws:securityhub:us-east-1:123456789012:subscription/...",
    "title": "S3 bucket does not have server-side encryption enabled",
    "severity": "HIGH",
    "status": "NEW",
    "description": "S3 bucket lacks default encryption",
    "resourceType": "AwsS3Bucket",
    "resourceIds": ["arn:aws:s3:::my-bucket"],
    "firstObservedAt": "2024-01-15T08:00:00Z",
    "lastObservedAt": "2024-01-15T16:00:00Z",
    "remediationText": "Enable default encryption on S3 bucket",
    "complianceStatus": "FAILED"
  }
]
```

### 3. Get Specific Vulnerability by ID
```bash
# Note: Finding IDs are typically ARNs and should be URL-encoded
curl -X GET "http://localhost:8080/api/vulnerabilities/arn:aws:securityhub:us-east-1:123456789012:subscription/..."
```

## CloudWatch Logs APIs

### 1. List All Log Groups
```bash
curl -X GET http://localhost:8080/api/logs/groups
```

Expected Response:
```json
[
  "/aws/lambda/my-function",
  "/aws/ecs/my-service",
  "/aws/apigateway/my-api"
]
```

### 2. Get Log Streams for a Log Group
```bash
# Note: Log group names with forward slashes should be URL-encoded
# /aws/lambda/my-function becomes %2Faws%2Flambda%2Fmy-function
curl -X GET "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/streams"
```

Expected Response:
```json
[
  "2024/01/15/[$LATEST]abcdef123456",
  "2024/01/15/[$LATEST]789012ghijkl",
  "2024/01/14/[$LATEST]mnopqr345678"
]
```

### 3. Get Log Summary for a Specific Stream
```bash
# Get logs from the last 24 hours (default)
curl -X GET "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/streams/2024%2F01%2F15%2F%5B%24LATEST%5Dabcdef123456/summary"

# Get logs from the last 12 hours
curl -X GET "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/streams/2024%2F01%2F15%2F%5B%24LATEST%5Dabcdef123456/summary?hours=12"
```

Expected Response:
```json
{
  "logGroupName": "/aws/lambda/my-function",
  "logStreamName": "2024/01/15/[$LATEST]abcdef123456",
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

### 4. Get Log Summary for a Log Group (Latest Stream)
```bash
# Get logs from latest stream in the last 24 hours (default)
curl -X GET "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/summary"

# Get logs from latest stream in the last 6 hours
curl -X GET "http://localhost:8080/api/logs/groups/%2Faws%2Flambda%2Fmy-function/summary?hours=6"
```

Expected Response:
```json
{
  "logGroupName": "/aws/lambda/my-function",
  "logStreamName": "2024/01/15/[$LATEST]abcdef123456",
  "totalEvents": 75,
  "startTime": "2024-01-15T16:00:00Z",
  "endTime": "2024-01-15T22:00:00Z",
  "statistics": {
    "errorCount": 2,
    "warningCount": 5,
    "infoCount": 65,
    "totalCount": 75
  },
  "events": [
    {
      "timestamp": "2024-01-15T21:45:00Z",
      "message": "INFO: Lambda function started",
      "level": "INFO"
    }
  ]
}
```

## Using with Different Profiles

### Development Profile
```bash
# Start the application
java -jar build/libs/devops-agent-1.0.0.jar --spring.profiles.active=dev

# Test endpoints
curl http://localhost:8080/api/pipelines
```

### Production Profile
```bash
# Start the application
java -jar build/libs/devops-agent-1.0.0.jar --spring.profiles.active=prod

# Test endpoints
curl http://localhost:8080/api/pipelines
```

## Integration Example

### Monitoring Script
Here's a simple bash script to monitor pipeline status:

```bash
#!/bin/bash

PIPELINE_NAME="my-pipeline"
API_URL="http://localhost:8080"

# Check pipeline status
STATUS=$(curl -s "${API_URL}/api/pipelines/${PIPELINE_NAME}" | jq -r '.status')

echo "Pipeline: ${PIPELINE_NAME}"
echo "Status: ${STATUS}"

if [ "${STATUS}" = "Failed" ]; then
    echo "Pipeline failed! Sending alert..."
    # Add your alert logic here
fi
```

### Alarm Monitoring Script
```bash
#!/bin/bash

API_URL="http://localhost:8080"

# Check for alarms in ALARM state
ALARM_COUNT=$(curl -s "${API_URL}/api/alarms/state/ALARM" | jq '. | length')

echo "Active Alarms: ${ALARM_COUNT}"

if [ ${ALARM_COUNT} -gt 0 ]; then
    echo "Found ${ALARM_COUNT} active alarm(s)!"
    curl -s "${API_URL}/api/alarms/state/ALARM" | jq '.[] | .alarmName'
fi
```

### Vulnerability Monitoring Script
```bash
#!/bin/bash

API_URL="http://localhost:8080"

# Check for critical vulnerabilities
CRITICAL_COUNT=$(curl -s "${API_URL}/api/vulnerabilities/severity/CRITICAL" | jq '. | length')

echo "Critical Vulnerabilities: ${CRITICAL_COUNT}"

if [ ${CRITICAL_COUNT} -gt 0 ]; then
    echo "Found ${CRITICAL_COUNT} critical vulnerability(ies)!"
    curl -s "${API_URL}/api/vulnerabilities/severity/CRITICAL" | jq '.[] | {title, resourceType, resourceIds}'
fi

# Check for high severity vulnerabilities
HIGH_COUNT=$(curl -s "${API_URL}/api/vulnerabilities/severity/HIGH" | jq '. | length')

echo "High Severity Vulnerabilities: ${HIGH_COUNT}"
```

### CloudWatch Logs Error Monitoring Script
```bash
#!/bin/bash

API_URL="http://localhost:8080"
LOG_GROUP="/aws/lambda/my-function"
# URL encode the log group name
ENCODED_LOG_GROUP=$(echo "$LOG_GROUP" | sed 's/\//%2F/g')

# Get log summary
SUMMARY=$(curl -s "${API_URL}/api/logs/groups/${ENCODED_LOG_GROUP}/summary?hours=1")

ERROR_COUNT=$(echo "$SUMMARY" | jq -r '.statistics.errorCount')
WARNING_COUNT=$(echo "$SUMMARY" | jq -r '.statistics.warningCount')

echo "Log Group: ${LOG_GROUP}"
echo "Errors (last hour): ${ERROR_COUNT}"
echo "Warnings (last hour): ${WARNING_COUNT}"

if [ "${ERROR_COUNT}" -gt 0 ]; then
    echo "Errors detected! Recent error messages:"
    echo "$SUMMARY" | jq -r '.events[] | select(.level == "ERROR") | .message'
fi
```

### Comprehensive Health Check Script
```bash
#!/bin/bash

API_URL="http://localhost:8080"

echo "=== DevOps Agent Health Check ==="
echo

# Check application health
echo "1. Application Health:"
curl -s "${API_URL}/actuator/health" | jq '.'
echo

# Check for failed pipelines
echo "2. Pipeline Status:"
PIPELINES=$(curl -s "${API_URL}/api/pipelines")
PIPELINE_COUNT=$(echo "$PIPELINES" | jq '. | length')
echo "Total Pipelines: ${PIPELINE_COUNT}"
echo

# Check for active alarms
echo "3. Active Alarms:"
ALARM_COUNT=$(curl -s "${API_URL}/api/alarms/state/ALARM" | jq '. | length')
echo "Alarms in ALARM state: ${ALARM_COUNT}"
echo

# Check for critical vulnerabilities
echo "4. Security Vulnerabilities:"
CRITICAL_VULN=$(curl -s "${API_URL}/api/vulnerabilities/severity/CRITICAL" | jq '. | length')
HIGH_VULN=$(curl -s "${API_URL}/api/vulnerabilities/severity/HIGH" | jq '. | length')
echo "Critical: ${CRITICAL_VULN}"
echo "High: ${HIGH_VULN}"
echo

# Overall status
if [ ${ALARM_COUNT} -gt 0 ] || [ ${CRITICAL_VULN} -gt 0 ]; then
    echo "⚠️  WARNING: Action required!"
    exit 1
else
    echo "✅ All systems operational"
    exit 0
fi
```
