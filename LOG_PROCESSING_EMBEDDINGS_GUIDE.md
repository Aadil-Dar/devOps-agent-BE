# Log Processing & Embedding System - Implementation Guide

## Overview

This implementation segregates the DevOps Assist system into two distinct operations:

### 1. **Log Processing API** (`/api/devops/process-logs`)
- Fetches logs from AWS CloudWatch
- Filters for errors and warnings
- Groups and deduplicates logs
- Creates embeddings using Ollama `nomic-embed-text` model
- Generates AI summaries
- Saves to DynamoDB for fast retrieval
- Triggers async metric collection in background

### 2. **Health Check API** (`/api/devops/healthCheck`)
- Reads cached log summaries from DynamoDB
- Reads cached metrics from DynamoDB
- Performs predictions and risk analysis
- No AWS API calls (uses cached data)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Request                          │
└─────────────────────────────────────────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
         ┌──────▼──────┐         ┌───────▼───────┐
         │ /process-logs│         │ /healthCheck  │
         └──────┬──────┘         └───────┬───────┘
                │                         │
    ┌───────────┴────────────┐           │
    │                        │           │
┌───▼────────┐    ┌─────────▼──────┐    │
│  Fetch     │    │  Async Metrics  │    │
│  Logs from │    │  Collection     │    │
│  CloudWatch│    │  (Background)   │    │
└───┬────────┘    └─────────┬──────┘    │
    │                       │            │
┌───▼────────┐              │            │
│  Filter &  │              │            │
│  Group     │              │            │
└───┬────────┘              │            │
    │                       │            │
┌───▼────────┐              │            │
│  Generate  │              │            │
│  Embeddings│              │            │
│  (Ollama)  │              │            │
└───┬────────┘              │            │
    │                       │            │
┌───▼────────┐              │            │
│  AI        │              │            │
│  Summary   │              │            │
└───┬────────┘              │            │
    │                       │            │
    └───────┬───────────────┘            │
            │                            │
       ┌────▼────────┐          ┌────────▼─────────┐
       │  DynamoDB   │◄─────────│  Read Cached     │
       │  Storage    │          │  Data            │
       │             │          │                  │
       │ - LogSummary│          │  - LogSummary    │
       │ - Embeddings│          │  - Metrics       │
       │ - Metrics   │          │                  │
       └─────────────┘          └──────────────────┘
                                         │
                                ┌────────▼─────────┐
                                │  Predictions &   │
                                │  Risk Analysis   │
                                └──────────────────┘
```

---

## New Components

### 1. **Models**

#### `LogEmbedding.java`
- DynamoDB entity for storing embeddings
- 768-dimensional vectors from `nomic-embed-text`
- Enables fast similarity search
- Linked to `LogSummary` via `summaryId`

#### `LogProcessingResponse.java`
- Response model for log processing API
- Contains processing statistics
- Top errors with severity
- AI-generated summary

#### `OllamaEmbedRequest.java` & `OllamaEmbedResponse.java`
- Request/response for Ollama embedding API

### 2. **Services**

#### `LogProcessingService.java`
**Key Features:**
- Fetches logs from CloudWatch with error filtering
- Groups and deduplicates logs by error signature
- Generates embeddings using Ollama in parallel (multithreading)
- Creates AI summaries using Ollama `llama3.2`
- Saves to DynamoDB (log summaries + embeddings)
- **Performance:** Uses `ExecutorService` with 5 threads for embedding generation

**Methods:**
- `processLogs(String projectId)` - Main entry point
- `fetchCloudWatchLogs()` - Fetch from AWS
- `processAndGroupLogs()` - Grouping and deduplication
- `generateEmbeddings()` - Parallel embedding generation
- `generateAiSummary()` - AI summarization
- `saveToDatabase()` - Parallel DB writes

#### `MetricProcessingService.java`
**Key Features:**
- Async service using `@Async` annotation
- Discovers running EC2 instances
- Fetches metrics (CPU, Network) in parallel
- Saves to DynamoDB in background
- Non-blocking - doesn't slow down log processing

**Methods:**
- `processMetricsAsync(String projectId)` - Returns `CompletableFuture<Integer>`
- `fetchCloudWatchMetrics()` - Parallel metric fetching
- `discoverRunningInstances()` - EC2 discovery

### 3. **Controller Updates**

#### `DevOpsInsightController.java`
**New Endpoint:**
```java
POST /api/devops/process-logs?projectId={projectId}
```

**Response:**
```json
{
  "projectId": "my-project",
  "processingTimestamp": 1707523200000,
  "totalLogsProcessed": 1250,
  "errorCount": 45,
  "warningCount": 102,
  "summariesCreated": 12,
  "embeddingsCreated": 12,
  "aiSummary": "System shows elevated error rates in authentication service...",
  "overallSeverity": "MEDIUM",
  "topErrors": [
    {
      "service": "eptBackendApp",
      "errorSignature": "ConnectionTimeoutException",
      "severity": "ERROR",
      "occurrences": 23,
      "firstSeenTimestamp": 1707520000000,
      "lastSeenTimestamp": 1707523000000,
      "sampleMessage": "Connection timeout after 30s...",
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

## DynamoDB Tables

### New Table: `devops-log-embeddings`

**Schema:**
```
Partition Key: projectId (String)
Sort Key: embeddingId (String) - format: "summaryId#emb"
```

**Attributes:**
- `embedding` - List<Double> (768 dimensions)
- `summaryId` - String (reference to LogSummary)
- `errorSignature` - String
- `severity` - String
- `timestamp` - Number
- `occurrences` - Number
- `summaryText` - String (text used for embedding)

### Configuration

Add to `application.properties`:
```properties
aws.dynamodb.log-embedding-table-name=devops-log-embeddings
```

---

## Performance Optimizations

### 1. **Multithreading**
- **Embedding Generation:** 5 parallel threads via `ExecutorService`
- **Metric Collection:** Parallel fetching per instance+metric
- **Database Writes:** Parallel writes for summaries and embeddings

### 2. **Token Efficiency**
- Only send grouped summaries to AI (not raw logs)
- Embeddings created once and cached
- AI summaries limited to top 10 critical errors
- Health checks use cached data (zero AWS API calls)

### 3. **Fast Retrieval**
- All data pre-processed and stored in DynamoDB
- Embeddings enable similarity search
- No real-time AWS API calls during health checks

### 4. **Async Processing**
- Metrics collection runs in background
- Non-blocking response to frontend
- Uses Spring's `@Async` with `CompletableFuture`

---

## Usage Flow

### Step 1: Schedule Log Processing
Call this periodically (e.g., every 15 minutes):
```bash
curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=my-project" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Step 2: Get Real-time Health Status
Call this anytime for instant predictions:
```bash
curl -X GET "http://localhost:8080/api/devops/healthCheck?projectId=my-project"
```

---

## Token Savings

### Before (Old System)
- Every health check: Fetch logs + analyze → **~5000 tokens**
- 100 health checks/day → **500K tokens/day**

### After (New System)
- Process logs once: **~5000 tokens**
- Health checks: Use cached data → **~500 tokens each**
- 1 log processing + 100 health checks → **55K tokens/day**

**Savings: ~90% reduction in AI token usage**

---

## Ollama Configuration

### Required Models

1. **For Embeddings:**
```bash
ollama pull nomic-embed-text
```

2. **For Summaries (already in use):**
```bash
ollama pull llama3.2
```

### Ollama Endpoints Used

- `POST /api/embeddings` - Generate embeddings
- `POST /api/generate` - Generate text summaries

---

## Recommended Workflow

### For Production:

1. **Scheduled Job (Every 15 min):**
   - Call `/process-logs` endpoint
   - Fetches latest logs
   - Updates embeddings
   - Refreshes cache

2. **Real-time Dashboard:**
   - Call `/healthCheck` endpoint
   - Instant response from cache
   - No performance impact

3. **Manual Triggers:**
   - After deployments: Call `/process-logs`
   - Before critical operations: Call `/healthCheck`

---

## Error Handling

### Log Processing Failures
- Service continues even if some log groups fail
- Individual embedding failures don't stop the process
- Metric collection failures logged but don't block response

### Graceful Degradation
- If Ollama is down: Returns basic summary without AI
- If no logs found: Returns safe empty response
- If DynamoDB unavailable: Throws exception (requires fixing)

---

## Monitoring

### Key Metrics to Track
1. Log processing duration (`stats.totalDurationMs`)
2. Number of embeddings created
3. Background metric collection success rate
4. DynamoDB write throughput
5. Ollama response times

### Logs to Watch
```
INFO  - Log processing completed for project: my-project - 1250 logs processed, 12 summaries created
INFO  - Background metric processing completed for project my-project. Collected 145 metrics.
INFO  - Generated 12 embeddings in 3421ms
```

---

## Future Enhancements

1. **Similarity Search:**
   - Use embeddings for finding similar errors
   - Historical pattern matching

2. **Anomaly Detection:**
   - Compare current embeddings with historical baseline
   - Detect new/unusual error patterns

3. **Auto-remediation:**
   - Link error signatures to runbooks
   - Suggest fixes based on similar past incidents

4. **Cost Dashboard:**
   - Track token usage per project
   - Monitor embedding storage costs

---

## Testing

### Test Log Processing
```bash
curl -X POST "http://localhost:8080/api/devops/process-logs?projectId=test-project" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" | jq .
```

### Test Health Check
```bash
curl -X GET "http://localhost:8080/api/devops/healthCheck?projectId=test-project" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" | jq .
```

### Verify DynamoDB Tables
```bash
aws dynamodb describe-table --table-name devops-log-embeddings
aws dynamodb scan --table-name devops-log-embeddings --limit 5
```

---

## Summary

✅ **Segregated Concerns:** Log processing vs Health checks
✅ **High Performance:** Multithreading for embeddings and metrics
✅ **Token Efficient:** ~90% reduction in AI token usage
✅ **Fast Retrieval:** All data cached in DynamoDB
✅ **Scalable:** Async processing, parallel operations
✅ **Production Ready:** Error handling, monitoring, graceful degradation

This implementation provides a robust, high-performance system that scales well and minimizes operational costs while providing real-time insights.
