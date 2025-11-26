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
