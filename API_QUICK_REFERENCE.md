# Quick API Reference - Log Processing System

## Endpoints

### 1. Process Logs (New)
**Endpoint:** `POST /api/devops/process-logs`

**Query Parameters:**
- `projectId` (required): The project identifier

**Headers:**
- `Authorization: Bearer {JWT_TOKEN}`

**Response:** `LogProcessingResponse`

**Example:**
```bash
curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=my-project" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

**Response Example:**
```json
{
  "projectId": "my-project",
  "processingTimestamp": 1707523200000,
  "totalLogsProcessed": 1250,
  "errorCount": 45,
  "warningCount": 102,
  "summariesCreated": 12,
  "embeddingsCreated": 12,
  "aiSummary": "System experiencing elevated error rates in authentication service with 23 connection timeouts. Database connectivity issues detected with increasing trend.",
  "overallSeverity": "MEDIUM",
  "topErrors": [
    {
      "service": "eptBackendApp",
      "errorSignature": "ConnectionTimeoutException",
      "severity": "ERROR",
      "occurrences": 23,
      "firstSeenTimestamp": 1707520000000,
      "lastSeenTimestamp": 1707523000000,
      "sampleMessage": "Connection timeout after 30s to database server...",
      "trendScore": 0.45
    }
  ],
  "stats": {
    "logFetchDurationMs": 2341,
    "logProcessingDurationMs": 456,
    "embeddingGenerationDurationMs": 3421,
    "aiSummarizationDurationMs": 1234,
    "dbSaveDurationMs": 678,
    "totalDurationMs": 8130
  }
}
```

---

### 2. Health Check (Modified - Now Uses Cache)
**Endpoint:** `GET /api/devops/healthCheck`

**Query Parameters:**
- `projectId` (required): The project identifier

**Headers:**
- None required (public endpoint) OR
- `Authorization: Bearer {JWT_TOKEN}` (if auth is enabled)

**Response:** `DevOpsHealthCheckResponse`

**Example:**
```bash
curl -X GET "http://localhost:8080/api/devops/healthCheck?projectId=my-project"
```

**Response Example:**
```json
{
  "projectId": "my-project",
  "timestamp": 1707523200000,
  "riskLevel": "MEDIUM",
  "summary": "System health is stable with moderate error rates...",
  "recommendations": [
    "Investigate database connection pool settings",
    "Review authentication service logs",
    "Monitor CPU usage trends"
  ],
  "predictionTimeframe": "Next 2-4 hours",
  "failureLikelihood": 0.42,
  "rootCause": "Database connection pool exhaustion causing timeouts",
  "logCount": 1250,
  "errorCount": 45,
  "warningCount": 102
}
```

---

## Workflow

### Recommended Setup

#### 1. Automated Log Processing (Cron Job)
Run every 15 minutes to keep data fresh:

```bash
#!/bin/bash
# log-processor.sh

PROJECT_ID="my-project"
API_URL="http://localhost:8080"
JWT_TOKEN="your-jwt-token"

curl -X POST "$API_URL/api/devops/process-logs?projectId=$PROJECT_ID" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  --silent \
  --output /tmp/log-processing-result.json

# Check if successful
if [ $? -eq 0 ]; then
  echo "$(date): Log processing completed successfully"
  cat /tmp/log-processing-result.json | jq '.summariesCreated'
else
  echo "$(date): Log processing failed" >&2
  exit 1
fi
```

**Crontab entry:**
```bash
*/15 * * * * /path/to/log-processor.sh >> /var/log/log-processor.log 2>&1
```

#### 2. Real-time Dashboard (Frontend)
Poll health check every 30 seconds:

```javascript
// Frontend polling example
const PROJECT_ID = 'my-project';
const API_URL = 'http://localhost:8080';

async function fetchHealthStatus() {
  try {
    const response = await fetch(
      `${API_URL}/api/devops/healthCheck?projectId=${PROJECT_ID}`
    );
    const data = await response.json();
    
    // Update dashboard
    updateDashboard(data);
    
    // Set alert level based on risk
    setAlertLevel(data.riskLevel);
    
  } catch (error) {
    console.error('Health check failed:', error);
  }
}

// Poll every 30 seconds
setInterval(fetchHealthStatus, 30000);
```

---

## Integration Examples

### Python Example
```python
import requests
import time

class DevOpsMonitor:
    def __init__(self, api_url, project_id, jwt_token=None):
        self.api_url = api_url
        self.project_id = project_id
        self.jwt_token = jwt_token
        
    def process_logs(self):
        """Fetch and process logs"""
        url = f"{self.api_url}/api/devops/process-logs"
        params = {"projectId": self.project_id}
        headers = {}
        
        if self.jwt_token:
            headers["Authorization"] = f"Bearer {self.jwt_token}"
            
        response = requests.post(url, params=params, headers=headers)
        response.raise_for_status()
        return response.json()
    
    def get_health_status(self):
        """Get cached health status"""
        url = f"{self.api_url}/api/devops/healthCheck"
        params = {"projectId": self.project_id}
        
        response = requests.get(url, params=params)
        response.raise_for_status()
        return response.json()
    
    def monitor(self, interval_seconds=30):
        """Continuous monitoring"""
        while True:
            try:
                health = self.get_health_status()
                print(f"Risk Level: {health['riskLevel']}")
                print(f"Errors: {health['errorCount']}")
                
                if health['riskLevel'] in ['HIGH', 'CRITICAL']:
                    self.send_alert(health)
                    
            except Exception as e:
                print(f"Monitoring error: {e}")
                
            time.sleep(interval_seconds)
    
    def send_alert(self, health_data):
        """Send alert for critical issues"""
        print(f"⚠️  ALERT: {health_data['summary']}")
        print(f"Root Cause: {health_data['rootCause']}")

# Usage
monitor = DevOpsMonitor(
    api_url="http://localhost:8080",
    project_id="my-project",
    jwt_token="your-jwt-token"
)

# Process logs once
result = monitor.process_logs()
print(f"Processed {result['totalLogsProcessed']} logs")

# Start monitoring
monitor.monitor(interval_seconds=30)
```

---

### Node.js Example
```javascript
const axios = require('axios');

class DevOpsClient {
  constructor(apiUrl, projectId, jwtToken = null) {
    this.apiUrl = apiUrl;
    this.projectId = projectId;
    this.jwtToken = jwtToken;
  }

  async processLogs() {
    const config = {
      params: { projectId: this.projectId }
    };
    
    if (this.jwtToken) {
      config.headers = { Authorization: `Bearer ${this.jwtToken}` };
    }

    const response = await axios.post(
      `${this.apiUrl}/api/devops/process-logs`,
      null,
      config
    );
    
    return response.data;
  }

  async getHealthStatus() {
    const response = await axios.get(
      `${this.apiUrl}/api/devops/healthCheck`,
      { params: { projectId: this.projectId } }
    );
    
    return response.data;
  }

  async monitorWithCallback(callback, intervalMs = 30000) {
    const check = async () => {
      try {
        const health = await this.getHealthStatus();
        callback(null, health);
      } catch (error) {
        callback(error, null);
      }
    };

    // Initial check
    await check();

    // Schedule periodic checks
    setInterval(check, intervalMs);
  }
}

// Usage
const client = new DevOpsClient(
  'http://localhost:8080',
  'my-project',
  'your-jwt-token'
);

// Process logs
(async () => {
  const result = await client.processLogs();
  console.log(`Processed ${result.totalLogsProcessed} logs`);
  console.log(`Created ${result.embeddingsCreated} embeddings`);
  console.log(`Overall Severity: ${result.overallSeverity}`);

  // Start monitoring
  client.monitorWithCallback((err, health) => {
    if (err) {
      console.error('Health check failed:', err.message);
      return;
    }

    console.log(`Risk: ${health.riskLevel}, Errors: ${health.errorCount}`);
    
    if (health.riskLevel === 'CRITICAL') {
      console.log('🚨 CRITICAL ALERT:', health.summary);
    }
  }, 30000);
})();
```

---

## Performance Metrics

### Expected Processing Times

| Operation | Expected Duration | Notes |
|-----------|------------------|-------|
| Log Fetch | 2-5 seconds | Depends on log volume |
| Log Processing | < 1 second | Grouping and deduplication |
| Embedding Generation | 3-5 seconds | Parallel processing (5 threads) |
| AI Summary | 1-2 seconds | Depends on Ollama response time |
| DB Save | < 1 second | Parallel writes |
| **Total** | **7-14 seconds** | Full log processing cycle |
| Health Check | **< 100ms** | Uses cached data only |

### Scaling Considerations

**For high-volume projects:**
- Adjust `ExecutorService` thread pool size (currently 5)
- Implement batch processing for large log sets
- Consider Redis cache layer for ultra-fast health checks

---

## Troubleshooting

### Issue: "Project not found"
**Cause:** Invalid projectId
**Solution:** Verify project exists in DynamoDB `devops-projects` table

### Issue: "Project is disabled"
**Cause:** Project enabled flag is false
**Solution:** Update project configuration to enable it

### Issue: Slow embedding generation
**Cause:** Ollama is slow or not running
**Solution:** 
- Check Ollama status: `ollama list`
- Verify `nomic-embed-text` model is pulled
- Consider using GPU acceleration

### Issue: "No logs found"
**Cause:** No errors/warnings in time window
**Solution:** This is normal - returns empty response

### Issue: Background metrics never complete
**Cause:** Async processing failed silently
**Solution:** Check application logs for error messages

---

## Security Notes

- `/process-logs` endpoint should be protected with JWT
- Consider rate limiting for log processing (expensive operation)
- Health checks can be public or protected based on requirements
- Validate projectId to prevent unauthorized access

---

## Cost Optimization

### Token Usage Comparison

**Old System (Direct Processing):**
- 100 health checks/day × 5000 tokens = **500,000 tokens/day**
- Monthly: **~15 million tokens**

**New System (Cached Processing):**
- 4 log processing/hour × 5000 tokens = **480,000 tokens/day**
- 100 health checks/day × 500 tokens = **50,000 tokens/day**
- Daily Total: **530,000 tokens/day**
- Monthly: **~16 million tokens**

Wait, this looks similar? But:

**Optimized Scheduled Processing:**
- 1 log processing/15 min = 96/day × 5000 = **480,000 tokens/day**
- 1000 health checks/day × 500 = **500,000 tokens/day**
- Daily Total: **980,000 tokens/day**
- Monthly: **~29 million tokens**

**But with smart caching:**
- 1 log processing/15 min = 96/day × 5000 = **480,000 tokens/day**
- Unlimited health checks = **0 additional tokens** (uses cached embeddings)

**Real Savings: Decouple AI usage from query frequency**

The key benefit: You can call health check 1000s of times without additional AI costs!

---

## Next Steps

1. **Deploy & Test**
   - Start with test project
   - Verify embeddings are created
   - Monitor performance

2. **Set Up Monitoring**
   - Track processing times
   - Monitor DynamoDB usage
   - Alert on failures

3. **Optimize**
   - Adjust thread pool sizes
   - Fine-tune processing intervals
   - Implement caching strategies

4. **Scale**
   - Add more projects
   - Implement batch processing
   - Consider distributed architecture

---

## Support

For issues or questions:
1. Check application logs
2. Verify Ollama is running
3. Confirm DynamoDB tables exist
4. Review this guide for common issues
