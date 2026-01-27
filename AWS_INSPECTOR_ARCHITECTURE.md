# AWS Inspector Multi-Project Architecture Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         User / Frontend                         │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ HTTP Request
                     │ GET /api/vulnerabilities?projectId=xxx
                     │
┌────────────────────▼────────────────────────────────────────────┐
│              AwsInspectorController                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ • GET /api/vulnerabilities?projectId={id}                │  │
│  │ • GET /api/vulnerabilities/{vulnId}?projectId={id}       │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ if projectId provided
                     │
┌────────────────────▼────────────────────────────────────────────┐
│              AwsInspectorService                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ getAllVulnerabilitiesForProject(projectId)              │  │
│  │ getVulnerabilityByIdForProject(projectId, id)           │  │
│  │                                                           │  │
│  │ createProjectInspectorClient(projectId) ─────────────┐   │  │
│  └──────────────────────────────────────────────────────│───┘  │
└─────────────────────────────────────────────────────────│───────┘
                     │                                    │
                     │                                    │
        ┌────────────▼────────────┐          ┌────────────▼─────────────┐
        │ ProjectConfiguration    │          │ SecretsManagerService    │
        │      Service            │          │                          │
        └────────────┬────────────┘          └────────────┬─────────────┘
                     │                                    │
                     │                                    │
        ┌────────────▼────────────┐          ┌────────────▼─────────────┐
        │  Amazon DynamoDB        │          │  AWS Secrets Manager     │
        │  ┌──────────────────┐   │          │  ┌───────────────────┐  │
        │  │ ProjectConfig    │   │          │  │ AWS Credentials   │  │
        │  │ • projectId      │   │          │  │ • aws-access-key  │  │
        │  │ • projectName    │   │          │  │ • aws-secret-key  │  │
        │  │ • awsRegion      │   │          │  │ • github-token    │  │
        │  │ • enabled        │   │          │  └───────────────────┘  │
        │  └──────────────────┘   │          └──────────────────────────┘
        └─────────────────────────┘
                     │
                     │ Config + Credentials
                     │
        ┌────────────▼─────────────────────────────────────────────────┐
        │        Create Inspector2Client with Project Credentials      │
        │        • Set Region from config                              │
        │        • Set AWS Access/Secret Key from Secrets Manager      │
        └────────────┬─────────────────────────────────────────────────┘
                     │
                     │ listFindings()
                     │
        ┌────────────▼────────────┐
        │  AWS Inspector2 API     │
        │  (Project-Specific)     │
        └────────────┬────────────┘
                     │
                     │ Vulnerabilities
                     │
        ┌────────────▼────────────┐
        │  Return to User         │
        └─────────────────────────┘
```

## Data Flow - Without ProjectId (Backward Compatible)

```
User Request
    │
    └──> /api/vulnerabilities
            │
            └──> AwsInspectorController
                    │
                    └──> AwsInspectorService.getAllVulnerabilities()
                            │
                            └──> Uses Default Inspector2Client Bean
                                    │
                                    └──> AWS Inspector2 API
                                            │
                                            └──> Return Vulnerabilities
```

## Data Flow - With ProjectId (Multi-Project)

```
User Request
    │
    └──> /api/vulnerabilities?projectId=abc-123
            │
            └──> AwsInspectorController
                    │
                    └──> AwsInspectorService.getAllVulnerabilitiesForProject("abc-123")
                            │
                            ├──> createProjectInspectorClient("abc-123")
                            │       │
                            │       ├──> ProjectConfigurationService
                            │       │       └──> DynamoDB: Get project config
                            │       │
                            │       └──> SecretsManagerService
                            │               └──> Secrets Manager: Get credentials
                            │
                            ├──> Create new Inspector2Client
                            │       └──> With project-specific credentials & region
                            │
                            ├──> getAllVulnerabilitiesWithClient(client)
                            │       └──> AWS Inspector2 API (project-specific)
                            │
                            └──> Auto-close client (try-with-resources)
```

## Project Configuration Storage

```
┌─────────────────────────────────────────────────────────────────┐
│                         DynamoDB Table                          │
│                      (ProjectConfigurations)                    │
├─────────────────────────────────────────────────────────────────┤
│  ProjectId (PK)      │  ProjectName  │  GitHubRepo  │  Enabled │
├──────────────────────┼───────────────┼──────────────┼──────────┤
│  abc-123             │  Project A    │  org/repo-a  │  true    │
│  def-456             │  Project B    │  org/repo-b  │  true    │
│  ghi-789             │  Project C    │  org/repo-c  │  false   │
└──────────────────────┴───────────────┴──────────────┴──────────┘
         │
         │ Reference: secretsPath = "devops-agent/projects/{projectId}"
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AWS Secrets Manager                          │
├─────────────────────────────────────────────────────────────────┤
│  Secret Name: devops-agent/projects/abc-123                     │
│  {                                                               │
│    "github-token": "ghp_xxxxxxxxxxxx",                          │
│    "aws-access-key": "AKIAXXXXXXXXXXXXXXXX",                    │
│    "aws-secret-key": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

## Admin API Flow (Project Creation)

```
Admin User
    │
    └──> POST /api/admin/projects/upload
            │
            └──> AdminProjectController.uploadProject()
                    │
                    ├──> Generate UUID for projectId
                    │
                    ├──> ProjectConfigurationService.saveConfiguration()
                    │       └──> Save to DynamoDB (non-sensitive data)
                    │
                    └──> SecretsManagerService.storeSecrets()
                            └──> Save to Secrets Manager (sensitive data)
                                    │
                                    └──> Return projectId to admin
```

## Security Model

```
┌──────────────────────────────────────────────────────────────┐
│                     Separation of Concerns                    │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  DynamoDB (Non-Sensitive)          Secrets Manager (Sensitive)│
│  ├─ Project Name                   ├─ AWS Access Key         │
│  ├─ GitHub Owner/Repo              ├─ AWS Secret Key         │
│  ├─ AWS Region                     └─ GitHub Token           │
│  ├─ Enabled Status                                            │
│  ├─ Created/Updated Timestamps                                │
│  └─ Metadata                                                  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

## Key Features

1. **Multi-Tenancy**: Each project has isolated AWS credentials
2. **Backward Compatible**: Works with/without projectId parameter
3. **Secure**: Credentials stored in AWS Secrets Manager
4. **Flexible**: Projects can use different AWS regions
5. **Resource-Safe**: Auto-closes clients with try-with-resources
6. **Cacheable**: Secrets are cached for performance
7. **Auditable**: All operations are logged with projectId

## API Endpoints Summary

| Endpoint | Method | Parameters | Description |
|----------|--------|------------|-------------|
| `/api/vulnerabilities` | GET | `projectId` (optional) | List all vulnerabilities |
| `/api/vulnerabilities/{id}` | GET | `projectId` (optional) | Get vulnerability details |
| `/api/admin/projects/upload` | POST | Project data | Create new project |
| `/api/admin/projects` | GET | - | List all projects |
| `/api/admin/projects/{id}` | GET | - | Get project details |
| `/api/admin/projects/{id}` | PUT | Project data | Update project |
| `/api/admin/projects/{id}` | DELETE | - | Delete project |
| `/api/admin/projects/{id}/toggle` | PATCH | - | Enable/disable project |

