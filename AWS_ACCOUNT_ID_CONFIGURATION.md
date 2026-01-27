# AWS Account ID Configuration for AWS Inspector2

## Overview

AWS Inspector2 requires proper account configuration to fetch vulnerabilities. This document explains how the AWS Account ID is now integrated into the project configuration and how it's used in API requests.

## Changes Made

### 1. ProjectConfiguration Model Updated

Added `awsAccountId` field to the `ProjectConfiguration` model:

```java
private String awsAccountId;

@DynamoDbAttribute("awsAccountId")
public String getAwsAccountId() {
    return awsAccountId;
}
```

This field is now stored in DynamoDB alongside other project configuration data.

### 2. AWS Inspector Service Updated

The `AwsInspectorService` now properly uses the AWS Account ID in all AWS Inspector2 API requests:

#### Filter Criteria with Account ID

When fetching vulnerabilities, the service now adds an account ID filter if provided:

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
    log.debug("Adding AWS Account ID filter: {}", awsAccountId);
}

FilterCriteria filterCriteria = filterBuilder.build();
```

#### ListFindingsRequest Construction

The `ListFindingsRequest` is built with the filter criteria that includes the account ID:

```java
ListFindingsRequest.Builder requestBuilder = ListFindingsRequest.builder()
    .maxResults(100)
    .filterCriteria(filterCriteria);

if (nextToken != null) {
    requestBuilder.nextToken(nextToken);
}

ListFindingsRequest request = requestBuilder.build();
```

## How It Works

### 1. Project Configuration
When creating or updating a project configuration, include the AWS Account ID:

```json
{
  "projectId": "my-project",
  "projectName": "My Project",
  "awsRegion": "eu-west-1",
  "awsAccountId": "123456789012",
  "githubOwner": "my-org",
  "githubRepo": "my-repo",
  "enabled": true
}
```

### 2. API Requests Flow

1. **Fetch Project Configuration**: When a vulnerability request is made for a specific project, the service fetches the project configuration from DynamoDB.

2. **Create Inspector Client**: Creates an Inspector2Client using the project's AWS credentials and region.

3. **Build Filter Criteria**: Constructs filter criteria with:
   - Finding Status = "ACTIVE"
   - AWS Account ID (if provided)

4. **Make API Request**: Calls AWS Inspector2 API with the properly configured request.

5. **Filter Results**: AWS Inspector2 returns only findings for the specified account.

## Benefits

1. **Multi-Account Support**: Each project can have its own AWS Account ID, enabling true multi-account/multi-project support.

2. **Precise Filtering**: AWS Inspector2 will only return findings from the specified account, avoiding cross-account contamination.

3. **Better Performance**: Filtering at the API level reduces data transfer and processing overhead.

4. **Security**: Account isolation ensures users only see vulnerabilities from their authorized accounts.

## Error Prevention

The implementation includes several safeguards:

- **Null/Empty Check**: Account ID filter is only added if the value is not null or empty
- **Backward Compatibility**: If no account ID is provided, the service still works (falls back to credential-based account detection)
- **Logging**: Debug logs show when account ID filter is applied

## Example API Response

When an account ID is configured, AWS Inspector2 API requests will include it in the filter:

```
[DEBUG] Adding AWS Account ID filter: 123456789012
[DEBUG] Calling Inspector2 listFindings with filterCriteria={...awsAccountId=[{comparison=EQUALS, value=123456789012}]...}
```

## Troubleshooting

### No Findings Returned

If no findings are returned:

1. Verify the AWS Account ID is correct
2. Check that AWS Inspector2 is enabled in that account
3. Confirm the region matches where findings exist
4. Verify the AWS credentials have permission to access that account

### Wrong Account Findings

If you see findings from the wrong account:

1. Check the `awsAccountId` in the project configuration
2. Verify the AWS credentials belong to the correct account
3. Review CloudWatch logs for account ID filter application

## API Usage

### Get All Vulnerabilities for a Project

```bash
GET /api/vulnerabilities?projectId=my-project
```

This will:
1. Fetch project configuration (including awsAccountId)
2. Create Inspector2Client with project credentials
3. Query AWS Inspector2 with account ID filter
4. Return vulnerabilities for that specific account

### Get Specific Vulnerability

```bash
GET /api/vulnerabilities/{id}?projectId=my-project
```

This will search across all findings in the specified account for the given vulnerability ID.

## Future Enhancements

1. **Multi-Account Aggregation**: Support for viewing vulnerabilities across multiple accounts
2. **Account Validation**: Verify account ID matches the credentials
3. **Cross-Account Role Assumption**: Support for AWS STS role assumption for cross-account access

