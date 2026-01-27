# AWS Inspector2 Request Flow with Account ID

## Complete Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. API Request                                                   │
│    GET /api/vulnerabilities?projectId=my-project                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. AwsInspectorService.getAllVulnerabilitiesForProject()        │
│    - Fetches ProjectConfiguration from DynamoDB                  │
│    - Extracts: projectId, awsRegion, awsAccountId              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. createProjectInspectorClient(projectId)                      │
│    - Gets AWS credentials from Secrets Manager                   │
│    - Creates Inspector2Client for the specific region           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. getAllVulnerabilitiesWithClient(client, awsAccountId)       │
│    - Builds FilterCriteria with:                                 │
│      • findingStatus = ACTIVE                                    │
│      • awsAccountId = "123456789012" (if provided)              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Build ListFindingsRequest                                     │
│                                                                  │
│    FilterCriteria.Builder filterBuilder =                       │
│        FilterCriteria.builder()                                  │
│            .findingStatus(List.of(                              │
│                StringFilter.builder()                            │
│                    .comparison(EQUALS)                           │
│                    .value("ACTIVE")                              │
│                    .build()                                      │
│            ));                                                   │
│                                                                  │
│    if (awsAccountId != null && !awsAccountId.isEmpty()) {      │
│        filterBuilder.awsAccountId(List.of(                      │
│            StringFilter.builder()                                │
│                .comparison(EQUALS)                               │
│                .value("123456789012")  ◄── ACCOUNT ID SET HERE │
│                .build()                                          │
│        ));                                                       │
│    }                                                             │
│                                                                  │
│    FilterCriteria filterCriteria = filterBuilder.build();      │
│                                                                  │
│    ListFindingsRequest request =                                │
│        ListFindingsRequest.builder()                            │
│            .maxResults(100)                                      │
│            .filterCriteria(filterCriteria)  ◄── FILTER APPLIED │
│            .build();                                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. AWS Inspector2 API Call                                      │
│    client.listFindings(request)                                  │
│                                                                  │
│    Request contains:                                             │
│    {                                                             │
│      "maxResults": 100,                                          │
│      "filterCriteria": {                                         │
│        "findingStatus": [                                        │
│          {                                                       │
│            "comparison": "EQUALS",                               │
│            "value": "ACTIVE"                                     │
│          }                                                       │
│        ],                                                        │
│        "awsAccountId": [        ◄── ACCOUNT ID IN REQUEST      │
│          {                                                       │
│            "comparison": "EQUALS",                               │
│            "value": "123456789012"                              │
│          }                                                       │
│        ]                                                         │
│      }                                                           │
│    }                                                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. AWS Inspector2 Response                                      │
│    - Returns ONLY findings for account 123456789012             │
│    - Filters out findings from other accounts                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. Map Findings to DTOs                                          │
│    - Convert Finding → VulnerabilitySummaryDto                  │
│    - Return list to controller                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Key Points

### ✅ Account ID is Set in FilterCriteria
The AWS Account ID is added to the `FilterCriteria` object, not directly to the `ListFindingsRequest`. This is the correct approach according to AWS SDK documentation.

### ✅ FilterCriteria is Applied to Request
```java
ListFindingsRequest.builder()
    .filterCriteria(filterCriteria)  // Contains account ID filter
    .build()
```

### ✅ AWS SDK Handles the Rest
The AWS SDK serializes the FilterCriteria (including account ID) into the API request body.

## Before vs After

### ❌ Before (Wrong)
```java
// No account ID anywhere
ListFindingsRequest request = ListFindingsRequest.builder()
    .maxResults(100)
    .filterCriteria(FilterCriteria.builder()
        .findingStatus(List.of(/* ... */))
        .build())
    .build();
// Result: Gets findings from ALL accounts or wrong account
```

### ✅ After (Correct)
```java
// Account ID in filter criteria
FilterCriteria.Builder filterBuilder = FilterCriteria.builder()
    .findingStatus(List.of(/* ... */));

if (awsAccountId != null && !awsAccountId.isEmpty()) {
    filterBuilder.awsAccountId(List.of(
        StringFilter.builder()
            .comparison(EQUALS)
            .value(awsAccountId)
            .build()
    ));
}

ListFindingsRequest request = ListFindingsRequest.builder()
    .maxResults(100)
    .filterCriteria(filterBuilder.build())
    .build();
// Result: Gets findings ONLY from specified account
```

## Configuration in DynamoDB

```json
{
  "projectId": "my-project",
  "projectName": "My Application",
  "githubOwner": "my-org",
  "githubRepo": "my-repo",
  "awsRegion": "eu-west-1",
  "awsAccountId": "123456789012",  ◄── Store this
  "enabled": true,
  "secretsPath": "devops-agent/projects/my-project",
  "createdAt": 1703203200000,
  "updatedAt": 1703203200000
}
```

## Secrets in AWS Secrets Manager

```json
{
  "aws-access-key": "AKIAIOSFODNN7EXAMPLE",
  "aws-secret-key": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
  "github-token": "ghp_xxxxxxxxxxxxxxxxxxxxx"
}
```

## API Response Example

```json
{
  "success": true,
  "data": [
    {
      "id": "abc123...",
      "cveId": "CVE-2024-12345",
      "title": "SQL Injection vulnerability",
      "severity": "HIGH",
      "cvssScore": 7.5,
      "packageName": "mysql-connector",
      "currentVersion": "8.0.20",
      "fixedVersion": "8.0.31",
      "affectedProjects": 3,
      "status": "ACTIVE"
    }
  ]
}
```

## Logging Output

```
[INFO] Fetching vulnerabilities for projectId: my-project
[INFO] Creating Inspector2Client for projectId: my-project
[DEBUG] Using project-specific AWS credentials for projectId: my-project
[INFO] Inspector2 client region: eu-west-1
[INFO] Fetching all ACTIVE vulnerabilities from AWS Inspector2
[DEBUG] Adding AWS Account ID filter: 123456789012  ◄── Confirms account ID is set
[DEBUG] Calling Inspector2 listFindings with filterCriteria={...awsAccountId=[{comparison=EQUALS, value=123456789012}]...}
[INFO] Fetched 15 ACTIVE vulnerabilities from AWS Inspector2
```

