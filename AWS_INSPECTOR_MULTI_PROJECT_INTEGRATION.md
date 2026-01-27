# AWS Inspector Service Integration with Project Configuration

## Overview

The `AwsInspectorService` has been fully integrated with the multi-project architecture where:
- **Project configurations** (non-sensitive data) are stored in **DynamoDB**
- **Credentials** (sensitive data) are stored in **AWS Secrets Manager**
- Each project can have its own **AWS Region** and **AWS Account ID**

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       API Request                                │
│  GET /api/vulnerabilities?projectId=my-project                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              AwsInspectorService                                 │
│  .getAllVulnerabilitiesForProject(projectId)                    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────┴──────────────────┐
        │                                      │
        ▼                                      ▼
┌──────────────────┐                  ┌──────────────────┐
│   DynamoDB       │                  │ Secrets Manager  │
│                  │                  │                  │
│ ProjectConfig    │                  │ Project Secrets  │
│ Table            │                  │                  │
│                  │                  │                  │
│ - projectId      │                  │ - aws-access-key │
│ - projectName    │                  │ - aws-secret-key │
│ - githubOwner    │                  │ - github-token   │
│ - githubRepo     │                  │                  │
│ - awsRegion ─────┼──────────┐       │                  │
│ - awsAccountId ──┼────┐     │       │                  │
│ - enabled        │    │     │       │                  │
│ - secretsPath    │    │     │       │                  │
└──────────────────┘    │     │       └──────────────────┘
                        │     │
                        │     │
                        ▼     ▼
            ┌────────────────────────────┐
            │  Create Inspector2Client   │
            │                            │
            │  - Region: awsRegion       │
            │  - Credentials: from       │
            │    Secrets Manager         │
            └──────────┬─────────────────┘
                       │
                       ▼
            ┌────────────────────────────┐
            │  Build ListFindingsRequest │
            │                            │
            │  FilterCriteria:           │
            │    - findingStatus=ACTIVE  │
            │    - awsAccountId ◄────────┼── From DynamoDB
            └──────────┬─────────────────┘
                       │
                       ▼
            ┌────────────────────────────┐
            │   AWS Inspector2 API       │
            │   client.listFindings()    │
            │                            │
            │   Returns vulnerabilities  │
            │   for specific account     │
            └────────────────────────────┘
```

## Key Components

### 1. AwsInspectorService

The service now properly fetches project-specific configuration and credentials:

#### Method: `createProjectInspectorClient(projectId)`

```java
private Inspector2Client createProjectInspectorClient(String projectId) {
    // 1. Get project configuration from DynamoDB
    ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

    // 2. Check if project is enabled
    if (!Boolean.TRUE.equals(config.getEnabled())) {
        throw new RuntimeException("Project is disabled: " + projectId);
    }

    // 3. Get credentials from Secrets Manager
    Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
    String awsAccessKey = secrets.get("aws-access-key");
    String awsSecretKey = secrets.get("aws-secret-key");

    // 4. Build Inspector2Client with project-specific credentials and region
    Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

    if (awsAccessKey != null && awsSecretKey != null) {
        // Use project-specific credentials
        AwsBasicCredentials credentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
        return Inspector2Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    } else {
        // Fall back to default credentials
        return Inspector2Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

#### Method: `getAllVulnerabilitiesForProject(projectId)`

```java
public List<VulnerabilitySummaryDto> getAllVulnerabilitiesForProject(String projectId) {
    // Get project configuration to extract AWS Account ID
    ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

    // Create project-specific Inspector2Client
    try (Inspector2Client client = createProjectInspectorClient(projectId)) {
        // Fetch vulnerabilities with account ID filter
        return getAllVulnerabilitiesWithClient(client, config.getAwsAccountId());
    }
}
```

#### Method: `getAllVulnerabilitiesWithClient(client, awsAccountId)`

```java
private List<VulnerabilitySummaryDto> getAllVulnerabilitiesWithClient(
    Inspector2Client client, 
    String awsAccountId) {
    
    // Build filter criteria
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

    // Build and execute request
    ListFindingsRequest request = ListFindingsRequest.builder()
            .maxResults(100)
            .filterCriteria(filterCriteria)
            .build();

    ListFindingsResponse response = client.listFindings(request);
    // ... process response
}
```

### 2. ProjectConfiguration (DynamoDB Model)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ProjectConfiguration {
    private String projectId;
    private String projectName;
    private String githubOwner;
    private String githubRepo;
    private String awsRegion;        // ◄── Used to create Inspector2Client
    private String awsAccountId;     // ◄── Used to filter findings
    private String secretsPath;
    private Boolean enabled;
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;
    private Map<String, String> metadata;

    // ... getters with @DynamoDbAttribute annotations
}
```

### 3. Secrets Manager Structure

For each project, secrets are stored at: `devops-agent/projects/{projectId}`

```json
{
  "aws-access-key": "AKIAIOSFODNN7EXAMPLE",
  "aws-secret-key": "wJalrXUtnFEMI/K7MDENG/bPxRfiCY",
  "github-token": "ghp_xxxxxxxxxxxx"
}
```

## Data Flow

### Step 1: API Request
```http
GET /api/vulnerabilities?projectId=my-project
Authorization: Bearer <JWT_TOKEN>
```

### Step 2: Fetch Configuration from DynamoDB
```java
ProjectConfiguration config = projectConfigurationService.getConfiguration("my-project");
// Returns:
// {
//   "projectId": "my-project",
//   "awsRegion": "eu-west-1",
//   "awsAccountId": "123456789012",
//   "secretsPath": "devops-agent/projects/my-project",
//   "enabled": true
// }
```

### Step 3: Fetch Credentials from Secrets Manager
```java
Map<String, String> secrets = secretsManagerService.getSecrets("my-project");
// Returns:
// {
//   "aws-access-key": "AKIAIOSFODNN7EXAMPLE",
//   "aws-secret-key": "wJalrXUtnFEMI/K7MDENG/bPxRfiCY"
// }
```

### Step 4: Create Inspector2Client
```java
Inspector2Client client = Inspector2Client.builder()
    .region(Region.of("eu-west-1"))  // From DynamoDB
    .credentialsProvider(StaticCredentialsProvider.create(credentials))  // From Secrets Manager
    .build();
```

### Step 5: Build Request with Account ID Filter
```java
FilterCriteria filterCriteria = FilterCriteria.builder()
    .findingStatus(List.of(StringFilter.builder().value("ACTIVE").build()))
    .awsAccountId(List.of(StringFilter.builder().value("123456789012").build()))  // From DynamoDB
    .build();

ListFindingsRequest request = ListFindingsRequest.builder()
    .filterCriteria(filterCriteria)
    .build();
```

### Step 6: Call AWS Inspector2 API
```java
ListFindingsResponse response = client.listFindings(request);
```

### Step 7: Return Results
```json
{
  "success": true,
  "data": [
    {
      "id": "abc123...",
      "cveId": "CVE-2024-12345",
      "severity": "HIGH",
      "cvssScore": 7.5,
      "packageName": "mysql-connector",
      "status": "ACTIVE"
    }
  ]
}
```

## Benefits

### ✅ Multi-Project Support
- Each project can have its own AWS credentials
- Each project can target different AWS regions
- Each project can filter by different AWS accounts

### ✅ Security
- Sensitive credentials stored in AWS Secrets Manager
- Non-sensitive config in DynamoDB
- Project isolation

### ✅ Flexibility
- Can use project-specific credentials or default credentials
- Can filter by account ID or not
- Backward compatible with existing code

### ✅ Proper Resource Management
- Uses try-with-resources for Inspector2Client
- Automatic cleanup of resources

## Configuration Example

### DynamoDB - ProjectConfigurations Table

```json
{
  "projectId": "project-alpha",
  "projectName": "Alpha Application",
  "githubOwner": "my-org",
  "githubRepo": "alpha-app",
  "awsRegion": "eu-west-1",
  "awsAccountId": "111111111111",
  "enabled": true,
  "secretsPath": "devops-agent/projects/project-alpha",
  "createdAt": 1703203200000,
  "updatedAt": 1703203200000,
  "createdBy": "admin@example.com"
}
```

### Secrets Manager - devops-agent/projects/project-alpha

```json
{
  "aws-access-key": "AKIAIOSFODNN7EXAMPLE1",
  "aws-secret-key": "wJalrXUtnFEMI/K7MDENG/bPxRfiCY1",
  "github-token": "ghp_token_alpha"
}
```

## Testing

### 1. Create Project Configuration in DynamoDB

Use AWS Console or SDK to create a project configuration with all required fields including `awsAccountId`.

### 2. Store Credentials in Secrets Manager

```bash
aws secretsmanager create-secret \
  --name devops-agent/projects/my-project \
  --secret-string '{"aws-access-key":"AKIA...","aws-secret-key":"wJal...","github-token":"ghp_..."}'
```

### 3. Test API Endpoint

```bash
curl -X GET "http://localhost:8080/api/vulnerabilities?projectId=my-project" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. Verify Logs

```
[INFO] Fetching vulnerabilities for projectId: my-project
[INFO] Creating Inspector2Client for projectId: my-project
[DEBUG] Using project-specific AWS credentials for projectId: my-project
[INFO] Inspector2 client region: eu-west-1
[INFO] Fetching all ACTIVE vulnerabilities from AWS Inspector2
[DEBUG] Adding AWS Account ID filter: 123456789012
[DEBUG] Calling Inspector2 listFindings with filterCriteria={...}
[INFO] Fetched 15 ACTIVE vulnerabilities from AWS Inspector2
```

## Summary

✅ **AwsInspectorService** now properly integrates with multi-project architecture  
✅ **Project configurations** fetched from DynamoDB  
✅ **AWS credentials** fetched from Secrets Manager  
✅ **Region-specific** Inspector2Client created per project  
✅ **Account ID filtering** applied in API requests  
✅ **Resource management** with try-with-resources  
✅ **Backward compatible** with default credentials  
✅ **Build successful** - no compilation errors

