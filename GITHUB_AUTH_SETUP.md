# GitHub Authentication Setup

## Issue Fixed
The `getOpenPullRequests()` method was failing due to missing authentication. The GitHub client was connecting anonymously, which has very limited API access.

## Solution
The application now supports two authentication methods:

### Option 1: OAuth Token (Recommended)
Set only the `github.token` property in your `application.properties`:

```properties
github.token=ghp_your_personal_access_token_here
```

### Option 2: Username and Personal Access Token
Set both `github.username` and `github.password` properties:

```properties
github.username=Aadikl
github.password=ghp_your_personal_access_token_here
```

**Note:** Despite the property name being "password", you should use your Personal Access Token (PAT) here, not your GitHub password. GitHub deprecated password authentication.

## How to Configure

### For Local Development
1. Open `src/main/resources/application.properties`
2. Replace `ghp_your_token_here` with your actual GitHub Personal Access Token
3. Restart the application

### Using Environment Variables (Recommended for Production)
You can override properties using environment variables:

```bash
export GITHUB_USERNAME=Aadikl
export GITHUB_PASSWORD=ghp_your_actual_token
```

Or when running the JAR:
```bash
java -jar devops-agent.jar --github.username=Aadikl --github.password=ghp_your_token
```

### For Docker
Add to your `docker-compose.yml`:
```yaml
environment:
  - GITHUB_USERNAME=Aadikl
  - GITHUB_PASSWORD=ghp_your_token
```

## Security Best Practices
⚠️ **IMPORTANT**: Never commit your actual token to Git!

1. Use environment variables for sensitive data
2. Add `application-local.properties` to `.gitignore` for local overrides
3. Use GitHub Secrets for CI/CD pipelines
4. Rotate tokens regularly

## Creating a GitHub Personal Access Token
1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scopes:
   - `repo` (for private repos)
   - `public_repo` (for public repos only)
4. Copy the token immediately (you won't see it again)
5. Add it to your configuration

## Testing
After configuration, restart your application and the logs should show:
```
Initializing GitHub client with username and password
```

Now the `getOpenPullRequests()` method should work without authentication errors.

