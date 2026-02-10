# CloudWatch Logs Fetching Improvement

## Problem
`DevOpsInsightService.fetchCloudWatchLogs()` was returning **0 logs** consistently, even though the CloudWatch log group `/ecs/eptBackendApp` contained logs and `CloudWatchLogsService` was able to fetch them successfully.

## Root Cause
The original implementation in `DevOpsInsightService` used `FilterLogEventsRequest` with a filter pattern (`?ERROR ?WARN ?Exception ?Timeout ?5xx`), which:
1. May not match all log formats
2. Has limitations on result size per request
3. Was not reliably paginating through results

In contrast, `CloudWatchLogsService` used a more reliable approach:
- **DescribeLogStreams**: Get list of log streams in the log group
- **GetLogEvents**: Fetch actual log events from each stream
- Proper pagination through log streams and events

## Solution
Created a **shared utility class** `CloudWatchLogsUtil` that both services now use for consistent and reliable log fetching.

### Files Created
- `src/main/java/com/devops/agent/util/CloudWatchLogsUtil.java`

### Files Modified
1. `src/main/java/com/devops/agent/service/DevOpsInsightService.java`
   - Imported `CloudWatchLogsUtil`
   - Replaced `fetchCloudWatchLogs()` to use `CloudWatchLogsUtil.fetchLogsFromGroup()`
   - Now fetches from up to 50 log streams, max 10k events per stream
   - Filters logs in-memory for errors, warnings, exceptions
   - Extended default time window to 12 hours

2. `src/main/java/com/devops/agent/service/CloudWatchLogsService.java`
   - Imported `CloudWatchLogsUtil`
   - Replaced `fetchLogsFromGroup()` to use shared utility
   - Maintains existing functionality with cleaner code

## Key Features of CloudWatchLogsUtil

### 1. `fetchLogsFromGroup()`
- Discovers log streams using `DescribeLogStreams` (ordered by last event time)
- Fetches events from each stream using `GetLogEvents`
- Supports pagination through streams and events
- Configurable limits for streams and events
- Skips streams outside the time range for efficiency

### 2. `fetchLogsWithFilter()` (optional)
- Alternative approach using `FilterLogEvents` for specific filter patterns
- Includes full pagination support

### 3. Private constructor
- Prevents instantiation (utility class pattern)

## Benefits

1. **Reliability**: Uses AWS SDK's recommended approach for fetching logs
2. **Consistency**: Both services use the same log-fetching logic
3. **Maintainability**: Changes to log-fetching logic only need to be made once
4. **Performance**: 
   - Fetches from multiple streams in parallel-capable structure
   - Skips streams outside time range
   - Configurable limits prevent over-fetching

## Testing Recommendations

1. **Test DevOps Health Check**:
   ```bash
   curl -X POST http://localhost:8080/api/devops-insights/health-check?projectId=YOUR_PROJECT_ID
   ```
   - Should now return logs in the response
   - Check `logCount`, `errorCount`, `warningCount` fields

2. **Test CloudWatch Logs API**:
   ```bash
   curl http://localhost:8080/api/logs?projectId=YOUR_PROJECT_ID&timeRange=12h
   ```
   - Should continue to work as before

3. **Verify Log Groups**:
   - Ensure `/ecs/eptBackendApp` exists in AWS CloudWatch Logs
   - Verify project has correct AWS credentials and region configured
   - Check that log streams have recent activity

## Configuration

Default log group is now `/ecs/eptBackendApp`. The service will:
1. Use project-configured log groups if available
2. Auto-discover log groups matching `/ecs/*eptBackendApp*`
3. Fall back to default if none found

Default time window for health checks: **12 hours** (was 1 hour)

## Next Steps

1. Monitor logs for "Fetched X logs from log group" messages
2. If still getting 0 logs, verify:
   - AWS credentials are correct for the project
   - AWS region matches where logs are stored
   - Log group name exactly matches `/ecs/eptBackendApp`
   - Logs exist within the 12-hour window
3. Consider adding metrics/monitoring for log fetch success rates
