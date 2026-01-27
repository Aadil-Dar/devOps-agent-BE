# AWS Inspector2 Request Configuration - Fix Summary

## Problem
The `ListFindingsRequest` was being built without properly setting the AWS Account ID, which could cause:
- No findings returned
- Findings from wrong accounts
- AWS API errors

## Solution

### 1. Added AWS Account ID to Project Configuration

**File**: `ProjectConfiguration.java`

Added new field:
```java
private String awsAccountId;
```

### 2. Updated AWS Inspector Service to Use Account ID

**File**: `AwsInspectorService.java`

#### Key Changes:

**a) Filter Criteria Builder**
Now properly constructs filter with account ID:

```java
FilterCriteria.Builder filterBuilder = FilterCriteria.builder()
    .findingStatus(List.of(
        StringFilter.builder()
            .comparison(StringComparison.EQUALS)
            .value("ACTIVE")
            .build()
    ));

// Add account ID filter if provided
if (awsAccountId != null && !awsAccountId.isEmpty()) {
    filterBuilder.awsAccountId(List.of(
        StringFilter.builder()
            .comparison(StringComparison.EQUALS)
            .value(awsAccountId)
            .build()
    ));
}

FilterCriteria filterCriteria = filterBuilder.build();
```

**b) Request Builder**
The request builder now uses the filter criteria with account ID:

```java
ListFindingsRequest.Builder requestBuilder = ListFindingsRequest.builder()
    .maxResults(100)
    .filterCriteria(filterCriteria);  // <-- Contains account ID filter

if (nextToken != null) {
    requestBuilder.nextToken(nextToken);
}

ListFindingsRequest request = requestBuilder.build();  // <-- Now includes account ID
```

**c) Method Signatures Updated**
All methods now pass account ID through the call chain:

```java
// Public method for project-specific queries
public List<VulnerabilitySummaryDto> getAllVulnerabilitiesForProject(String projectId) {
    ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
        .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
    
    try (Inspector2Client client = createProjectInspectorClient(projectId)) {
        return getAllVulnerabilitiesWithClient(client, config.getAwsAccountId());  // <-- Pass account ID
    }
}

// Private implementation method
private List<VulnerabilitySummaryDto> getAllVulnerabilitiesWithClient(
    Inspector2Client client, 
    String awsAccountId) {  // <-- Receives account ID
    // ... uses awsAccountId in filter
}
```

## Where Account ID is Set

The AWS Account ID is configured at **three levels**:

### 1. In Filter Criteria (API Level)
```java
filterBuilder.awsAccountId(List.of(
    StringFilter.builder()
        .comparison(StringComparison.EQUALS)
        .value(awsAccountId)  // <-- Here
        .build()
));
```

### 2. In Request Builder (Builder Level)
```java
ListFindingsRequest.Builder requestBuilder = ListFindingsRequest.builder()
    .filterCriteria(filterCriteria);  // <-- Filter already contains account ID
```

### 3. In Final Request (Request Level)
```java
ListFindingsRequest request = requestBuilder.build();  // <-- Final request has account ID in filter
```

## How to Configure

### Step 1: Store Account ID in DynamoDB

When creating a project configuration:

```json
{
  "projectId": "my-project",
  "projectName": "My Project",
  "awsRegion": "eu-west-1",
  "awsAccountId": "123456789012",  // <-- Add this
  "enabled": true
}
```

### Step 2: Service Automatically Uses It

The service will automatically:
1. Fetch project configuration from DynamoDB
2. Extract `awsAccountId`
3. Include it in the AWS Inspector2 API request filter

## Verification

You can verify the account ID is being used by checking the logs:

```
[DEBUG] Adding AWS Account ID filter: 123456789012
[DEBUG] Calling Inspector2 listFindings with filterCriteria={...awsAccountId=[{comparison=EQUALS, value=123456789012}]...}
```

## Testing

### Test with Postman

1. Create a project configuration with `awsAccountId`
2. Call the vulnerabilities endpoint:
   ```
   GET /api/vulnerabilities?projectId=my-project
   ```
3. Check logs to confirm account ID filter is applied

### Test with curl

```bash
curl -X GET "http://localhost:8080/api/vulnerabilities?projectId=my-project" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## What Was the Issue?

Before the fix:
```java
// Account ID was NOT being set anywhere in the request
FilterCriteria filterCriteria = FilterCriteria.builder()
    .findingStatus(List.of(/* ... */))
    .build();  // <-- No account ID

ListFindingsRequest request = ListFindingsRequest.builder()
    .filterCriteria(filterCriteria)
    .build();  // <-- Missing account ID
```

After the fix:
```java
// Account ID IS set in the filter criteria
FilterCriteria.Builder filterBuilder = FilterCriteria.builder()
    .findingStatus(List.of(/* ... */));

if (awsAccountId != null && !awsAccountId.isEmpty()) {
    filterBuilder.awsAccountId(List.of(/* ... */));  // <-- Account ID added
}

FilterCriteria filterCriteria = filterBuilder.build();

ListFindingsRequest request = ListFindingsRequest.builder()
    .filterCriteria(filterCriteria)  // <-- Now contains account ID
    .build();
```

## Summary

✅ **Account ID is now set in the FilterCriteria**  
✅ **FilterCriteria is passed to ListFindingsRequest.Builder**  
✅ **Request is built with proper account ID filter**  
✅ **AWS Inspector2 API receives the account ID**  

The account ID is NOT set directly on the `ListFindingsRequest`, but rather in the **FilterCriteria** which is then used by the request builder. This is the correct way according to AWS SDK documentation.

