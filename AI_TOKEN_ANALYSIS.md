# AI Token Usage Analysis & GPT Cost Calculator

## 📊 Current Ollama Usage in Application

### Summary of AI Integration Points

Your application currently uses **Ollama (qwen2.5-coder:7b)** in **3 services** with **4 different use cases**:

| Service | Use Case | Frequency | Complexity |
|---------|----------|-----------|------------|
| `AiInsightsService` | Vulnerability Analysis | Per vulnerability scan | High |
| `GitHubService` | PR Commit Summary | Per PR request | Low |
| `ClusterLogsService` | Log Grouping | Per log query | High |
| `ClusterLogsService` | Log Summary | Per log query | Low |

---

## 🔍 Detailed Token Usage Analysis

### 1. AiInsightsService - Vulnerability Analysis

**Endpoint**: `/api/insights/{vulnId}/analyze`

**Input Prompt Structure**:
```
System Instructions: ~400 tokens
Vulnerability JSON Data: ~500-800 tokens (varies by vulnerability)
Total Input: ~900-1,200 tokens per request
```

**Output Response**:
```json
{
  "aiRemediationAnalysis": "...",        // ~50-100 tokens
  "estimatedTime": "...",                 // ~5 tokens
  "riskLevel": "...",                     // ~2 tokens
  "automationAvailable": true,            // ~1 token
  "recommendedRemediationSteps": [...],   // ~100-150 tokens
  "mavenDependencyUpdateSnippet": "..."   // ~50-100 tokens
}
Total Output: ~200-350 tokens per request
```

**Sample Prompt** (from code):
```java
String prompt = """
    You are a security remediation assistant.
    
    You will receive a JSON object with vulnerability details...
    Analyze the vulnerability and respond with ONLY a JSON object...
    
    {JSON RULES - ~400 tokens}
    
    Here is the vulnerability JSON to analyze:
    {VULNERABILITY_DATA - ~500-800 tokens}
    """;
```

**Token Estimate per Request**:
- **Input**: 900-1,200 tokens
- **Output**: 200-350 tokens
- **Total**: 1,100-1,550 tokens

---

### 2. GitHubService - PR Commit Summary

**Endpoint**: Internally called when fetching PR details

**Input Prompt Structure**:
```
System Instructions: ~50 tokens
Commit Messages (5-20 commits): ~100-400 tokens
Total Input: ~150-450 tokens per request
```

**Output Response**:
```
Brief suggestion (max 15 words): ~15-20 tokens
Truncated to 200 chars if longer
Total Output: ~15-30 tokens per request
```

**Sample Prompt**:
```java
String prompt = String.format(
    "Analyze the following commit messages from Pull Request #%d and provide a brief, "
    + "actionable suggestion (max 15 words) on what needs attention or improvement:\n\n"
    + "%s\n\nSuggestion:",
    prNumber,
    commitsText  // ~100-400 tokens
);
```

**Token Estimate per Request**:
- **Input**: 150-450 tokens
- **Output**: 15-30 tokens
- **Total**: 165-480 tokens

---

### 3. ClusterLogsService - Log Grouping (AI-based)

**Endpoint**: `/api/cluster-logs` (when AI grouping is triggered)

**Input Prompt Structure**:
```
Log entries (up to 100 error logs): ~2,000-5,000 tokens
System Instructions: ~100 tokens
Total Input: ~2,100-5,100 tokens per request
```

**Output Response**:
```json
[
  {
    "groupTitle": "...",      // ~10-20 tokens
    "logIndices": [0, 2, 5]   // ~5-10 tokens
  }
  // Typically 3-10 groups
]
Total Output: ~100-300 tokens per request
```

**Sample Prompt Logic**:
```java
// Builds a string with up to 100 error logs
StringBuilder logsData = new StringBuilder();
for (int i = 0; i < errorLogs.size(); i++) {
    logsData.append(String.format(
        "%d. [%s] %s | %s\n",
        i,
        log.severity(),    // ~1 token
        log.message(),     // ~20-50 tokens
        log.timestamp()    // ~3 tokens
    ));
}
// Total: ~24-54 tokens per log × 100 logs = ~2,400-5,400 tokens
```

**Token Estimate per Request**:
- **Input**: 2,100-5,100 tokens
- **Output**: 100-300 tokens
- **Total**: 2,200-5,400 tokens

---

### 4. ClusterLogsService - Log Summary

**Endpoint**: `/api/cluster-logs/summary`

**Input Prompt Structure**:
```
Log group name: ~5 tokens
Top 5 critical issues: ~200-400 tokens
System Instructions: ~50 tokens
Total Input: ~255-455 tokens per request
```

**Output Response**:
```
2-sentence summary (max 300 chars): ~50-80 tokens
Total Output: ~50-80 tokens per request
```

**Sample Prompt**:
```java
var summaryInput = new StringBuilder(500);
summaryInput.append("Log Group: ").append(LOG_GROUP_NAME).append("\n");
summaryInput.append("Critical issues:\n");

for (int i = 0; i < Math.min(5, logs.size()); i++) {
    var log = logs.get(i);
    summaryInput.append("%d. [%s] %s (count: %d)\n".formatted(
        i + 1, log.getSeverity(), log.getTitle(), log.getCount()
    ));
}

var prompt = "Analyze these logs and provide a brief 2-sentence summary:\n\n%s\n\nSummary:".formatted(summaryInput);
```

**Token Estimate per Request**:
- **Input**: 255-455 tokens
- **Output**: 50-80 tokens
- **Total**: 305-535 tokens

---

## 💰 Cost Calculation for GPT-3.5-Turbo

### Pricing Model
```
Model: gpt-3.5-turbo
Input:  $1.50 per 1M tokens
Output: $2.00 per 1M tokens
```

### Per-Request Costs

| Use Case | Input Tokens | Output Tokens | Input Cost | Output Cost | Total Cost |
|----------|--------------|---------------|------------|-------------|------------|
| **Vulnerability Analysis** | 900-1,200 | 200-350 | $0.00135-$0.0018 | $0.0004-$0.0007 | **$0.00175-$0.00250** |
| **PR Commit Summary** | 150-450 | 15-30 | $0.000225-$0.000675 | $0.00003-$0.00006 | **$0.000255-$0.000735** |
| **Log Grouping** | 2,100-5,100 | 100-300 | $0.00315-$0.00765 | $0.0002-$0.0006 | **$0.00335-$0.00825** |
| **Log Summary** | 255-455 | 50-80 | $0.000383-$0.000683 | $0.0001-$0.00016 | **$0.000483-$0.000843** |

---

## 📈 Monthly Cost Estimation (Normal Load)

### Assumptions for "Normal Load"

Let's assume moderate usage for a DevOps team:

| Use Case | Requests/Day | Requests/Month (30 days) |
|----------|--------------|--------------------------|
| Vulnerability Analysis | 20 scans | 600 |
| PR Commit Summary | 50 PRs | 1,500 |
| Log Grouping | 10 queries | 300 |
| Log Summary | 20 summaries | 600 |

### Monthly Cost Breakdown

#### 1. Vulnerability Analysis
- **Requests**: 600/month
- **Cost per request**: $0.00175 - $0.00250
- **Monthly cost**: **$1.05 - $1.50**

#### 2. PR Commit Summary
- **Requests**: 1,500/month
- **Cost per request**: $0.000255 - $0.000735
- **Monthly cost**: **$0.38 - $1.10**

#### 3. Log Grouping
- **Requests**: 300/month
- **Cost per request**: $0.00335 - $0.00825
- **Monthly cost**: **$1.01 - $2.48**

#### 4. Log Summary
- **Requests**: 600/month
- **Cost per request**: $0.000483 - $0.000843
- **Monthly cost**: **$0.29 - $0.51**

### **Total Monthly Cost (Normal Load)**

| Scenario | Monthly Cost |
|----------|--------------|
| **Minimum** (low token usage) | **$2.73** |
| **Average** (typical usage) | **$3.80** |
| **Maximum** (high token usage) | **$5.59** |

---

## 📊 Cost Comparison: Different Load Scenarios

### Scenario 1: Low Usage (Small Team)
- 10 vuln scans/day
- 20 PRs/day
- 5 log queries/day
- **Monthly Cost**: **$1.50 - $2.50**

### Scenario 2: Normal Usage (Medium Team)
- 20 vuln scans/day
- 50 PRs/day
- 10 log queries/day
- **Monthly Cost**: **$2.73 - $5.59** ✅ **Default Estimate**

### Scenario 3: High Usage (Large Team)
- 50 vuln scans/day
- 100 PRs/day
- 30 log queries/day
- **Monthly Cost**: **$7.00 - $14.00**

### Scenario 4: Very High Usage (Enterprise)
- 100 vuln scans/day
- 200 PRs/day
- 50 log queries/day
- **Monthly Cost**: **$14.00 - $28.00**

---

## 🎯 Recommended Monthly Budget

Based on the analysis:

| Team Size | API Calls/Month | Recommended Budget |
|-----------|-----------------|-------------------|
| **Small** (2-5 devs) | ~1,500 | **$3.00/month** |
| **Medium** (5-15 devs) | ~4,000 | **$6.00/month** |
| **Large** (15-50 devs) | ~10,000 | **$15.00/month** |
| **Enterprise** (50+ devs) | ~25,000 | **$30.00/month** |

### **Your Normal Load Estimate**: **$4.00 - $6.00/month**

---

## 💡 Cost Optimization Tips

### 1. Cache AI Responses
```java
@Cacheable(value = "aiAnalysis", key = "#vulnerabilityId")
public AiInsightResponse analyzeVulnerability(String vulnerabilityId) {
    // Cache results for 24 hours
}
```
**Potential Savings**: 30-50% reduction

### 2. Batch Processing
- Group multiple vulnerability analyses into one request
- **Potential Savings**: 20-30% reduction

### 3. Use Shorter Prompts
- Remove verbose instructions
- Use more concise JSON examples
- **Potential Savings**: 10-15% reduction

### 4. Implement Request Throttling
```java
@RateLimiter(name = "aiService", fallbackMethod = "fallbackAnalysis")
public AiInsightResponse analyzeVulnerability(...) {
    // Limit to 10 requests/minute
}
```

### 5. Use GPT-3.5-Turbo-Instruct (Cheaper)
- Input: $1.50/1M → **$0.50/1M** (67% cheaper)
- Output: $2.00/1M → **$1.00/1M** (50% cheaper)
- **Monthly cost**: **$1.00 - $2.00** instead of $4-$6

---

## 📝 Token Calculation Formula

### General Formula
```
Cost = (Input_Tokens × Input_Price_per_M / 1,000,000) 
     + (Output_Tokens × Output_Price_per_M / 1,000,000)
```

### Example Calculation
**Vulnerability Analysis**:
```
Input:  1,000 tokens × $1.50/1M = $0.0015
Output: 300 tokens × $2.00/1M = $0.0006
Total:  $0.0021 per request

Monthly (600 requests): $0.0021 × 600 = $1.26
```

---

## 🔧 Implementation: Add Token Tracking

To track actual token usage in your application:

### 1. Update Request Model
```java
@Data
public class OllamaGenerateRequest {
    private String model;
    private String prompt;
    private boolean stream;
    
    // Add token tracking
    private Integer maxTokens = 1000;
}
```

### 2. Update Response Model
```java
@Data
public class OllamaGenerateResponse {
    private String response;
    
    // Add token metrics
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
```

### 3. Add Logging
```java
log.info("AI Request - Input tokens: {}, Output tokens: {}, Total cost: ${}",
    promptTokens, completionTokens, calculateCost(promptTokens, completionTokens));
```

---

## 📊 Real-Time Cost Monitoring

### Create Cost Tracking Service
```java
@Service
public class AiCostTrackingService {
    
    private static final double INPUT_COST_PER_TOKEN = 1.50 / 1_000_000;
    private static final double OUTPUT_COST_PER_TOKEN = 2.00 / 1_000_000;
    
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    
    public void trackUsage(int inputTokens, int outputTokens) {
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
    }
    
    public double getTotalCost() {
        return (totalInputTokens.get() * INPUT_COST_PER_TOKEN)
             + (totalOutputTokens.get() * OUTPUT_COST_PER_TOKEN);
    }
    
    public CostReport getMonthlyReport() {
        return CostReport.builder()
            .totalInputTokens(totalInputTokens.get())
            .totalOutputTokens(totalOutputTokens.get())
            .totalCost(getTotalCost())
            .build();
    }
}
```

---

## 🚀 Migration Path: Ollama → GPT-3.5-Turbo

### Option 1: Direct OpenAI API
```java
@Configuration
public class OpenAiConfig {
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Bean
    public WebClient openAiWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }
}
```

### Option 2: Azure OpenAI (Recommended for Enterprise)
```java
@Bean
public WebClient azureOpenAiWebClient(WebClient.Builder builder) {
    return builder
        .baseUrl("https://{your-resource}.openai.azure.com")
        .defaultHeader("api-key", apiKey)
        .build();
}
```

---

## 📋 Summary

### Key Takeaways

1. **Current Usage**: 4 AI endpoints across 3 services
2. **Token Usage**: 165 - 5,400 tokens per request (varies by use case)
3. **Cost per Request**: $0.00026 - $0.00825
4. **Monthly Cost (Normal Load)**: **$3.00 - $6.00**
5. **Annual Cost**: **$36 - $72**

### Recommendations

✅ **Budget**: Allocate **$5-10/month** for normal usage  
✅ **Monitoring**: Implement token tracking immediately  
✅ **Optimization**: Add caching for 30-50% savings  
✅ **Scaling**: Can handle up to 10,000 requests/month for <$15  

### Next Steps

1. ✅ Review token estimates (see above)
2. ⬜ Implement token tracking in responses
3. ⬜ Add cost monitoring dashboard
4. ⬜ Set up budget alerts (AWS Cost Explorer)
5. ⬜ Migrate from Ollama to GPT-3.5-Turbo

---

**Bottom Line**: Your application will cost approximately **$4-6 per month** for GPT-3.5-Turbo on normal load, which is very affordable for the AI-powered features you're providing!

