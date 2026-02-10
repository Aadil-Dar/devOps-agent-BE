# Quick Start Guide - Log Processing System

## Prerequisites
- ✅ Spring Boot application running
- ✅ Ollama installed and running
- ✅ AWS credentials configured
- ✅ DynamoDB accessible

## Step-by-Step Setup

### 1. Install Ollama Model
```bash
# Pull the embedding model (required)
ollama pull nomic-embed-text

# Verify it's installed
ollama list | grep nomic-embed-text
```

### 2. Configure Application
Edit `application.properties`:
```properties
# Add this line (if not already present)
aws.dynamodb.log-embedding-table-name=devops-log-embeddings
```

### 3. Create DynamoDB Table (Optional - auto-created)
```bash
aws dynamodb create-table \
  --table-name devops-log-embeddings \
  --attribute-definitions \
    AttributeName=projectId,AttributeType=S \
    AttributeName=embeddingId,AttributeType=S \
  --key-schema \
    AttributeName=projectId,KeyType=HASH \
    AttributeName=embeddingId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1
```

### 4. Build & Start Application
```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Or run the JAR
java -jar build/libs/devops-agent-1.0.0.jar
```

### 5. Test Log Processing
```bash
# Replace YOUR_PROJECT_ID with actual project ID
curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=YOUR_PROJECT_ID" \
  -H "Content-Type: application/json" | jq .
```

**Expected Response:**
```json
{
  "projectId": "YOUR_PROJECT_ID",
  "processingTimestamp": 1707523200000,
  "totalLogsProcessed": 150,
  "errorCount": 12,
  "warningCount": 45,
  "summariesCreated": 8,
  "embeddingsCreated": 8,
  "aiSummary": "System health is stable...",
  "overallSeverity": "LOW",
  "topErrors": [...],
  "stats": {
    "totalDurationMs": 8500
  }
}
```

### 6. Test Health Check
```bash
curl -X GET "http://localhost:8080/api/devops/healthCheck?projectId=YOUR_PROJECT_ID" | jq .
```

**Expected Response:**
```json
{
  "projectId": "YOUR_PROJECT_ID",
  "riskLevel": "LOW",
  "summary": "System is healthy...",
  "recommendations": ["..."],
  "failureLikelihood": 0.15
}
```

### 7. Verify DynamoDB
```bash
# Check log summaries
aws dynamodb scan --table-name devops-log-summaries --limit 5

# Check embeddings
aws dynamodb scan --table-name devops-log-embeddings --limit 5

# Check metrics
aws dynamodb scan --table-name devops-metric-snapshots --limit 5
```

---

## Scheduled Processing (Recommended)

### Option 1: Linux Cron Job
```bash
# Edit crontab
crontab -e

# Add this line (runs every 15 minutes)
*/15 * * * * curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=YOUR_PROJECT_ID" >> /var/log/log-processor.log 2>&1
```

### Option 2: Shell Script
```bash
#!/bin/bash
# log-processor.sh

PROJECT_ID="YOUR_PROJECT_ID"
API_URL="http://localhost:8080"

echo "$(date): Starting log processing for $PROJECT_ID"

RESPONSE=$(curl -s -X POST "$API_URL/api/devops/process-logs?projectId=$PROJECT_ID")

if [ $? -eq 0 ]; then
  SUMMARIES=$(echo $RESPONSE | jq -r '.summariesCreated')
  EMBEDDINGS=$(echo $RESPONSE | jq -r '.embeddingsCreated')
  echo "$(date): Success - Created $SUMMARIES summaries and $EMBEDDINGS embeddings"
else
  echo "$(date): Failed"
  exit 1
fi
```

Make it executable and run:
```bash
chmod +x log-processor.sh
./log-processor.sh
```

### Option 3: Spring Scheduler (In-App)
Add to your application:
```java
@Scheduled(fixedDelay = 900000) // 15 minutes
public void scheduledLogProcessing() {
    List<String> projectIds = getAllActiveProjectIds();
    for (String projectId : projectIds) {
        logProcessingService.processLogs(projectId);
    }
}
```

---

## Monitoring

### Check Application Logs
```bash
# Real-time monitoring
tail -f logs/application.log | grep -E "(Log processing|Background metric)"

# Check for errors
grep ERROR logs/application.log | tail -20
```

### Monitor Performance
Watch for these log messages:
```
INFO  - Log processing completed for project: X - 1250 logs processed, 12 summaries created
INFO  - Generated 12 embeddings in 3421ms
INFO  - Background metric processing completed for project X. Collected 145 metrics.
```

### Check DynamoDB Stats
```bash
# Count items in embeddings table
aws dynamodb describe-table --table-name devops-log-embeddings \
  | jq '.Table.ItemCount'

# Check table size
aws dynamodb describe-table --table-name devops-log-embeddings \
  | jq '.Table.TableSizeBytes'
```

---

## Troubleshooting

### Issue: "Cannot resolve method 'builder'"
**Solution:** OllamaGenerateRequest doesn't have @Builder annotation. Use constructor instead:
```java
new OllamaGenerateRequest("llama3.2", prompt, false)
```

### Issue: Embeddings not created
**Check:**
1. Ollama is running: `curl http://localhost:11434/api/tags`
2. Model is available: `ollama list | grep nomic-embed-text`
3. Application can reach Ollama: Check logs for connection errors

### Issue: "Project not found"
**Solution:** Create project in DynamoDB:
```bash
aws dynamodb put-item \
  --table-name devops-projects \
  --item '{
    "projectId": {"S": "YOUR_PROJECT_ID"},
    "enabled": {"BOOL": true},
    "awsRegion": {"S": "eu-west-1"}
  }'
```

### Issue: Slow processing
**Optimize:**
1. Increase embedding thread pool size (default: 5)
2. Adjust log fetch limits
3. Check Ollama performance
4. Monitor network latency to AWS

### Issue: "No logs found"
**This is normal** if there are no errors/warnings in the time window.
Response will show:
```json
{
  "totalLogsProcessed": 0,
  "summariesCreated": 0,
  "aiSummary": "No errors or warnings detected in the time window."
}
```

---

## Performance Tuning

### 1. Adjust Embedding Thread Pool
In `LogProcessingService.java`:
```java
private static final ExecutorService EMBEDDING_EXECUTOR = 
    Executors.newFixedThreadPool(10); // Increase from 5 to 10
```

### 2. Configure Log Fetch Limits
In `LogProcessingService.java`:
```java
List<OutputLogEvent> rawEvents = CloudWatchLogsUtil.fetchLogsFromGroup(
    client, logGroupName, startTime, endTime,
    100,   // Increase from 50 (max streams)
    20000  // Increase from 10000 (max events)
);
```

### 3. Adjust Processing Interval
For high-volume projects: Every 5 minutes
For low-volume projects: Every 30 minutes

---

## Security Best Practices

### 1. Secure JWT Tokens
```properties
jwt.secret=${JWT_SECRET:your-secret-key}
jwt.expiration=3600000
```

### 2. Protect Process Logs Endpoint
Add rate limiting:
```java
@RateLimiter(name = "processLogs", fallbackMethod = "rateLimitFallback")
@PostMapping("/process-logs")
public ResponseEntity<LogProcessingResponse> processLogs(...) {
    // ...
}
```

### 3. Validate Project Ownership
```java
// Ensure user can only access their own projects
if (!userService.hasAccessToProject(userId, projectId)) {
    throw new UnauthorizedException();
}
```

---

## Cost Optimization

### 1. Optimize Processing Schedule
- High priority: Every 10 minutes
- Medium priority: Every 30 minutes
- Low priority: Every hour

### 2. Filter Logs Early
Only fetch ERROR and WARN logs from CloudWatch:
```java
FilterLogEventsRequest request = FilterLogEventsRequest.builder()
    .logGroupName(logGroupName)
    .filterPattern("?ERROR ?WARN")
    // ...
```

### 3. Monitor Token Usage
Track AI calls and tokens:
```java
log.info("AI tokens used: {} for project {}", tokensUsed, projectId);
```

---

## Production Checklist

Before deploying to production:

- [ ] Ollama is running and accessible
- [ ] `nomic-embed-text` model is downloaded
- [ ] DynamoDB tables are created
- [ ] AWS credentials are configured
- [ ] JWT authentication is enabled
- [ ] Rate limiting is configured
- [ ] Logging is properly set up
- [ ] Monitoring dashboard is ready
- [ ] Scheduled job is configured
- [ ] Backup strategy is in place
- [ ] Error alerting is set up
- [ ] Load testing is completed

---

## Next Steps

1. **Deploy** the application
2. **Configure** scheduled processing
3. **Monitor** performance and logs
4. **Optimize** based on usage patterns
5. **Scale** as needed

---

## Support

### Documentation
- Full guide: `LOG_PROCESSING_EMBEDDINGS_GUIDE.md`
- API reference: `API_QUICK_REFERENCE.md`
- Implementation summary: `IMPLEMENTATION_SUMMARY_LOG_PROCESSING.md`

### Logging
Check these files:
- Application logs: `logs/application.log`
- Processing logs: Look for "Log processing completed"
- Error logs: Look for "ERROR" level messages

### Contact
For issues:
1. Check application logs
2. Review troubleshooting section
3. Verify configuration
4. Test with sample project

---

## Quick Commands Cheat Sheet

```bash
# Pull Ollama model
ollama pull nomic-embed-text

# Build application
./gradlew clean build

# Run application
./gradlew bootRun

# Process logs
curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=PROJECT_ID"

# Health check
curl -X GET "http://localhost:8080/api/devops/healthCheck?projectId=PROJECT_ID"

# Check DynamoDB
aws dynamodb scan --table-name devops-log-embeddings --limit 5

# Monitor logs
tail -f logs/application.log | grep "Log processing"

# Check Ollama status
curl http://localhost:11434/api/tags
```

---

## Success Indicators

You'll know it's working when you see:
1. ✅ "Log processing completed" in logs
2. ✅ "Generated N embeddings" messages
3. ✅ Items in `devops-log-embeddings` table
4. ✅ Health checks return in < 100ms
5. ✅ No ERROR level logs

---

That's it! You're now ready to use the high-performance log processing system. 🚀
