# Pull Request Controller - Multi-Project Support

## Overview
The `PullRequestController` has been updated to support multi-project configuration. It now fetches GitHub repository details (owner, repo, token) from DynamoDB and AWS Secrets Manager based on the provided `projectId`.

---

## What Changed

### 1. GitHubService Updates
- **Added ProjectConfigurationService** dependency to fetch project config from DynamoDB
- **Added SecretsManagerService** dependency to fetch GitHub tokens from Secrets Manager
- **Renamed hardcoded values** to `defaultRepositoryOwner`, `defaultRepositoryName`, `defaultGithubToken`
- **Added GitHubConfig** helper class to hold project-specific configuration
- **Added `getGitHubConfig(projectId)`** method to fetch project configuration
- **Updated `getOpenPullRequests(projectId)`** to accept projectId parameter
- **Updated `getPullRequest(projectId, number)`** to accept projectId parameter

### 2. PullRequestController Updates
- **Added `projectId` parameter** to all endpoints (optional, defaults to default config)
- **Updated endpoint signatures** to pass projectId to GitHubService methods

---

## Architecture

```
┌─────────────────────────┐
│ PullRequestController   │
│ GET /api/pull-requests  │
│ ?projectId=project-123  │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│   GitHubService         │
│ getGitHubConfig()       │
└───────┬─────────────────┘
        │
        ├────────────────────┐
        │                    │
        ▼                    ▼
┌──────────────────┐  ┌────────────────────┐
│ DynamoDB         │  │ Secrets Manager    │
│ ProjectConfig    │  │ GitHub Token       │
│ - githubOwner    │  │ - githubToken      │
│ - githubRepo     │  │                    │
└──────────────────┘  └────────────────────┘
```

---

## API Endpoints

### 1. Get All Open Pull Requests

**GET** `/api/pull-requests`

**Query Parameters:**
- `projectId` (optional): The project ID to fetch PRs for. If not provided, uses default configuration.

**Example Requests:**

```bash
# Using default project configuration
GET http://localhost:8080/api/pull-requests

# Using specific project
GET http://localhost:8080/api/pull-requests?projectId=project-123
```

**Response:**
```json
[
  {
    "number": 42,
    "id": "PR-42",
    "title": "Add new feature",
    "state": "OPEN",
    "status": "SUCCESS",
    "author": "John Doe",
    "avatar": "https://avatars.githubusercontent.com/...",
    "url": "https://github.com/owner/repo/pull/42",
    "createdAt": "2 days ago",
    "updatedAt": "1 hour ago",
    "branch": "feature/new-feature",
    "targetBranch": "main",
    "commits": 5,
    "additions": 120,
    "deletions": 30,
    "filesChanged": 8,
    "reviewers": ["Jane Smith"],
    "labels": ["feature", "enhancement"],
    "priority": "Normal",
    "pipeline": "Passed",
    "pipelineType": "Mock Auth",
    "aiSuggestion": "Consider adding unit tests for the new feature."
  }
]
```

---

### 2. Get Specific Pull Request

**GET** `/api/pull-requests/{number}`

**Path Parameters:**
- `number`: The pull request number

**Query Parameters:**
- `projectId` (optional): The project ID to fetch PR for. If not provided, uses default configuration.

**Example Requests:**

```bash
# Using default project configuration
GET http://localhost:8080/api/pull-requests/42

# Using specific project
GET http://localhost:8080/api/pull-requests/42?projectId=project-123
```

**Response:**
```json
{
  "number": 42,
  "id": "PR-42",
  "title": "Add new feature",
  "state": "OPEN",
  "status": "SUCCESS",
  "author": "John Doe",
  "avatar": "https://avatars.githubusercontent.com/...",
  "url": "https://github.com/owner/repo/pull/42",
  "createdAt": "2 days ago",
  "updatedAt": "1 hour ago",
  "branch": "feature/new-feature",
  "targetBranch": "main",
  "commits": 5,
  "additions": 120,
  "deletions": 30,
  "filesChanged": 8,
  "reviewers": ["Jane Smith"],
  "labels": ["feature", "enhancement"],
  "priority": "Normal",
  "pipeline": "Passed",
  "pipelineType": "Mock Auth",
  "aiSuggestion": "Consider adding unit tests for the new feature."
}
```

---

## Configuration Requirements

### DynamoDB - ProjectConfiguration

The project must exist in the `devops-projects` DynamoDB table with the following fields:

```json
{
  "projectId": "project-123",
  "projectName": "My Production App",
  "githubOwner": "my-org",
  "githubRepo": "my-repo",
  "awsRegion": "us-east-1",
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

### AWS Secrets Manager

The project must have secrets stored in Secrets Manager at:
`devops-agent/projects/{projectId}`

**Required Secret:**
```json
{
  "githubToken": "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```

---

## How It Works

### Step-by-Step Flow:

1. **Request Received**: `GET /api/pull-requests?projectId=project-123`

2. **Controller**: PullRequestController receives the request and calls `gitHubService.getOpenPullRequests("project-123")`

3. **Fetch Configuration**: GitHubService calls `getGitHubConfig("project-123")`
   
4. **Get Project Config** from DynamoDB:
   ```java
   ProjectConfiguration config = projectConfigurationService.getConfiguration("project-123")
   // Returns: { githubOwner: "my-org", githubRepo: "my-repo", ... }
   ```

5. **Get GitHub Token** from Secrets Manager:
   ```java
   Map<String, String> secrets = secretsManagerService.getSecrets("project-123")
   String githubToken = secrets.get("githubToken")
   // Returns: "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
   ```

6. **Build GitHubConfig** object:
   ```java
   GitHubConfig config = new GitHubConfig(
       "my-org",      // owner from DynamoDB
       "my-repo",     // repo from DynamoDB
       "ghp_xxx..."   // token from Secrets Manager
   )
   ```

7. **Fetch PRs** from GitHub using the config:
   ```java
   // GraphQL query to GitHub API
   // Authorization: bearer ghp_xxx...
   // Query: repository(owner: "my-org", name: "my-repo")
   ```

8. **Return Results** to the controller, which sends response to client

---

## Error Handling

### Project Not Found (400 Bad Request)
```json
{
  "error": "Project not found: project-123"
}
```

### Missing GitHub Token (500 Internal Server Error)
```json
{
  "error": "GitHub token not found for project: project-123"
}
```

### GitHub API Error (500 Internal Server Error)
```json
{
  "error": "GraphQL request failed with status: 401"
}
```

---

## Frontend Integration

### Fetch PRs for Logged-In User's Project

```javascript
// Get user's projectId from localStorage
const projectId = localStorage.getItem('projectId');

// Fetch pull requests for user's project
const response = await fetch(`/api/pull-requests?projectId=${projectId}`, {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('token')}`
  }
});

const pullRequests = await response.json();
```

### Admin Fetching PRs for Any Project

```javascript
// Admin can fetch PRs for any project
async function fetchPRsForProject(projectId) {
  const response = await fetch(`/api/pull-requests?projectId=${projectId}`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  });
  
  return await response.json();
}
```

### Display Pull Requests

```jsx
function PullRequestList() {
  const [prs, setPRs] = useState([]);
  const projectId = localStorage.getItem('projectId');

  useEffect(() => {
    async function loadPRs() {
      const response = await fetch(`/api/pull-requests?projectId=${projectId}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      
      const data = await response.json();
      setPRs(data);
    }
    
    loadPRs();
  }, [projectId]);

  return (
    <div>
      <h2>Open Pull Requests</h2>
      {prs.map(pr => (
        <div key={pr.number}>
          <h3>#{pr.number}: {pr.title}</h3>
          <p>Author: {pr.author}</p>
          <p>Status: {pr.status}</p>
          <p>{pr.aiSuggestion}</p>
        </div>
      ))}
    </div>
  );
}
```

---

## Testing in Postman

### Step 1: Create a Project Configuration

```bash
POST http://localhost:8080/api/admin/projects

Headers:
Authorization: Bearer <admin_token>
Content-Type: application/json

Body:
{
  "projectId": "test-project",
  "projectName": "Test Project",
  "githubOwner": "your-github-org",
  "githubRepo": "your-repo",
  "awsRegion": "us-east-1",
  "credentials": {
    "githubToken": "ghp_your_actual_github_token"
  }
}
```

### Step 2: Fetch Pull Requests

```bash
GET http://localhost:8080/api/pull-requests?projectId=test-project

Headers:
Authorization: Bearer <user_token>
```

### Step 3: Get Specific Pull Request

```bash
GET http://localhost:8080/api/pull-requests/42?projectId=test-project

Headers:
Authorization: Bearer <user_token>
```

---

## Benefits

✅ **Multi-Tenant Support** - Multiple projects with different GitHub repos  
✅ **Secure** - GitHub tokens stored in AWS Secrets Manager  
✅ **Flexible** - Supports default config for backward compatibility  
✅ **Scalable** - Easy to add new projects without code changes  
✅ **Isolated** - Each project's PRs are separate  

---

## Default Configuration

If no `projectId` is provided, the system uses the default configuration from `application.properties`:

```properties
github.repository.owner=sdc-pune
github.repository.name=shoma-ui
github.token=your_default_token
```

This ensures backward compatibility with existing implementations.

---

## Security Considerations

1. **GitHub Token**: Stored securely in AWS Secrets Manager, never in DynamoDB
2. **Access Control**: ProjectIdInterceptor validates user can only access their assigned project
3. **Caching**: Secrets are cached (5 minutes) for performance
4. **Logging**: Sensitive data (tokens) are never logged

---

## Related Documentation

- **Project Upload API**: See `MULTI_PROJECT_SETUP.md`
- **User Management**: See `USER_MANAGEMENT_GUIDE.md`
- **AWS Secrets Manager**: See `AWS_DEPLOYMENT.md`

---

**Last Updated**: December 21, 2024  
**Status**: ✅ Complete and Working  
**Build**: ✅ Successful

