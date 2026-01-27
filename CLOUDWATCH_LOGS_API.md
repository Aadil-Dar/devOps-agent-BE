# CloudWatch Logs API Documentation

## Overview
The CloudWatch Logs API provides access to AWS CloudWatch Logs with advanced filtering, pagination, and log parsing capabilities. This endpoint is designed to fetch and analyze logs from your AWS infrastructure.

## Endpoint

### GET `/api/logs`

Fetch raw/filtered log entries from CloudWatch.

## Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `timeRange` | string | No | `"24h"` | Time range for logs. Valid values: `"1h"`, `"6h"`, `"24h"`, `"today"` |
| `environment` | string | No | - | Filter by environment: `"prod"`, `"stage"`, `"dev"` |
| `severities` | array | No | - | Filter by severity levels: `["ERROR", "WARN", "INFO", "DEBUG"]` |
| `services` | array | No | - | Filter by service names: `["order-service", "payment-service", "user-service"]` |
| `search` | string | No | - | Text search in log messages |
| `clusterId` | string | No | - | Filter logs by cluster ID |
| `page` | int | No | `1` | Page number for pagination |
| `size` | int | No | `20` | Number of logs per page (max: 100) |

## Request Examples

### Basic Request - Last 24 hours
```bash
GET /api/logs?timeRange=24h&page=1&size=20
```

### Filter by Environment and Severity
```bash
GET /api/logs?timeRange=6h&environment=prod&severities=ERROR&severities=WARN
```

### Search for Specific Text
```bash
GET /api/logs?timeRange=1h&search=database&page=1&size=50
```

### Filter by Service
```bash
GET /api/logs?timeRange=today&services=order-service&services=payment-service
```

### Complex Filter Example
```bash
GET /api/logs?timeRange=24h&environment=prod&severities=ERROR&services=order-service&search=timeout&page=1&size=20
```

## Response Format

### Success Response (200 OK)

```typescript
interface PaginatedLogsResponse {
  logs: LogEntry[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

interface LogEntry {
  id: string;              // Unique identifier (UUID)
  timestamp: string;       // ISO 8601 format (e.g., "2024-12-11T10:30:45Z")
  severity: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG';
  service: string;         // e.g., 'order-service'
  host: string;            // e.g., 'ip-10-0-1-123'
  message: string;         // Full log message
  requestId?: string;      // Trace/Request ID if available
  parsed?: Record<string, any>;  // Parsed structured data (e.g., JSON logs)
}
```

### Example Response

```json
{
  "logs": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "timestamp": "2024-12-11T14:30:45.123Z",
      "severity": "ERROR",
      "service": "order-service",
      "host": "ip-10-0-1-123",
      "message": "ERROR Database connection timeout after 30s - requestId: req-abc123",
      "requestId": "req-abc123",
      "parsed": {
        "userId": 1234,
        "errorCode": "DB_TIMEOUT",
        "retryCount": 3
      }
    },
    {
      "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "timestamp": "2024-12-11T14:28:15.456Z",
      "severity": "WARN",
      "service": "payment-service",
      "host": "ip-10-0-2-234",
      "message": "WARN Payment gateway response delayed: 5000ms",
      "requestId": "req-def456",
      "parsed": null
    },
    {
      "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
      "timestamp": "2024-12-11T14:25:30.789Z",
      "severity": "INFO",
      "service": "user-service",
      "host": "ip-10-0-3-345",
      "message": "INFO User login successful - userId: 5678",
      "requestId": "req-ghi789",
      "parsed": {
        "userId": 5678,
        "loginMethod": "oauth",
        "ipAddress": "192.168.1.100"
      }
    }
  ],
  "total": 1247,
  "page": 1,
  "size": 20,
  "totalPages": 63
}
```

### Error Responses

#### 400 Bad Request
Invalid filter parameters (e.g., invalid timeRange value)

```json
{
  "timestamp": "2024-12-11T14:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid timeRange parameter"
}
```

#### 500 Internal Server Error
Server error while fetching logs

```json
{
  "timestamp": "2024-12-11T14:30:00.000Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to fetch logs from CloudWatch"
}
```

## Features

### 1. **Intelligent Log Parsing**
- Automatically extracts severity levels (ERROR, WARN, INFO, DEBUG)
- Identifies service names from log messages or log group names
- Extracts request/trace IDs for distributed tracing
- Parses structured JSON logs into the `parsed` field

### 2. **Flexible Time Ranges**
- `1h` - Last 1 hour
- `6h` - Last 6 hours
- `24h` - Last 24 hours (default)
- `today` - From midnight today

### 3. **Multi-level Filtering**
- **Environment**: Filter by deployment environment
- **Severity**: Filter by log levels
- **Service**: Filter by microservice names
- **Text Search**: Full-text search in log messages
- **Cluster**: Filter by ECS cluster ID

### 4. **Pagination**
- Efficient pagination for large result sets
- Configurable page size (1-100 logs per page)
- Total count and page information included

### 5. **Log Enrichment**
- Extracts hostname/IP from log streams
- Identifies request IDs for correlation
- Parses structured logs (JSON) automatically
- ISO 8601 timestamp formatting

## Log Group Structure

The service expects CloudWatch log groups to follow this naming convention:
```
/aws/ecs/{environment}/{service-name}
```

Examples:
- `/aws/ecs/prod/order-service`
- `/aws/ecs/stage/payment-service`
- `/aws/ecs/dev/user-service`

## Performance Considerations

1. **Log Group Limits**: Fetches from top 10 most recent log streams per group
2. **Event Limits**: Maximum 100 events per log stream
3. **Timeout**: Queries have built-in timeouts to prevent hanging
4. **Caching**: Consider implementing caching for frequently accessed time ranges

## Integration with Frontend

### Loading Data
```typescript
async function loadData() {
  const response = await fetch('/api/logs?timeRange=24h&page=1&size=20');
  const data: PaginatedLogsResponse = await response.json();
  return data;
}
```

### Applying Filters
```typescript
async function applyFilters(filters: {
  timeRange: string;
  environment?: string;
  severities?: string[];
  services?: string[];
  search?: string;
  page: number;
  size: number;
}) {
  const params = new URLSearchParams();
  params.append('timeRange', filters.timeRange);
  params.append('page', filters.page.toString());
  params.append('size', filters.size.toString());
  
  if (filters.environment) params.append('environment', filters.environment);
  if (filters.search) params.append('search', filters.search);
  filters.severities?.forEach(s => params.append('severities', s));
  filters.services?.forEach(s => params.append('services', s));
  
  const response = await fetch(`/api/logs?${params.toString()}`);
  const data: PaginatedLogsResponse = await response.json();
  return data;
}
```

### Pagination
```typescript
function handlePageChange(newPage: number) {
  const currentFilters = getCurrentFilters();
  applyFilters({ ...currentFilters, page: newPage });
}
```

## Testing

### Test with cURL

```bash
# Basic test
curl -X GET "http://localhost:8080/api/logs?timeRange=1h"

# With filters
curl -X GET "http://localhost:8080/api/logs?timeRange=24h&environment=prod&severities=ERROR&severities=WARN&page=1&size=50"

# Text search
curl -X GET "http://localhost:8080/api/logs?timeRange=6h&search=timeout&page=1&size=20"
```

## Error Handling

The API handles errors gracefully:
- Invalid parameters return 400 Bad Request
- AWS service errors are logged and return 500 Internal Server Error
- Missing log groups return empty results (not an error)
- Parsing errors for individual logs don't fail the entire request

## Security Considerations

1. **AWS Credentials**: Ensure proper AWS IAM credentials are configured
2. **Required Permissions**:
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
3. **CORS**: Configured in `CorsConfig.java`
4. **Rate Limiting**: Consider implementing rate limiting for production

## Future Enhancements

- [ ] Real-time log streaming with WebSockets
- [ ] Log aggregation and statistics
- [ ] Custom log parsers per service
- [ ] Export logs to CSV/JSON
- [ ] Saved filter presets
- [ ] Alert creation from log patterns

