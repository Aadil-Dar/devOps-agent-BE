# GET /api/auth/me - Get Current User Details

## Overview
After successful login, the frontend should call the `/me` endpoint to retrieve complete user details including project information, permissions, and account status.

---

## API Endpoint

**Method**: `GET`  
**URL**: `http://localhost:8080/api/auth/me`  
**Authentication**: Required (JWT Token)

### Headers
```
Authorization: Bearer <jwt_token_from_login>
```

---

## Response Example

### User with Project Assignment

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "roles": ["USER"],
  "projectId": "project-123",
  "projectName": "My Production Application",
  "enableToUseDevops": true,
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

### User without Project Assignment

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "username": "jane_smith",
  "email": "jane@example.com",
  "roles": ["USER"],
  "projectId": null,
  "projectName": null,
  "enableToUseDevops": false,
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

### Admin User

```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "username": "admin",
  "email": "admin@devops.com",
  "roles": ["ADMIN", "USER"],
  "projectId": null,
  "projectName": null,
  "enableToUseDevops": true,
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

---

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | User's unique identifier (UUID) |
| `username` | String | User's username |
| `email` | String | User's email address |
| `roles` | Array | User's roles (e.g., ["USER"], ["ADMIN", "USER"]) |
| `projectId` | String/null | ID of the assigned project, or null if not assigned |
| `projectName` | String/null | Name of the assigned project, or null if not assigned |
| `enableToUseDevops` | Boolean | Whether the user has DevOps access enabled |
| `enabled` | Boolean | Whether the user account is active |
| `createdAt` | Long | Unix timestamp (milliseconds) when user was created |
| `updatedAt` | Long | Unix timestamp (milliseconds) when user was last updated |

---

## Frontend Integration

### Login Flow

```javascript
// Step 1: Login
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password123'
  })
});

const loginData = await loginResponse.json();

// Step 2: Store token
localStorage.setItem('token', loginData.token);

// Step 3: Fetch complete user details
const meResponse = await fetch('/api/auth/me', {
  headers: {
    'Authorization': `Bearer ${loginData.token}`
  }
});

const userDetails = await meResponse.json();

// Step 4: Store all user details
localStorage.setItem('userId', userDetails.id);
localStorage.setItem('username', userDetails.username);
localStorage.setItem('email', userDetails.email);
localStorage.setItem('roles', JSON.stringify(userDetails.roles));
localStorage.setItem('projectId', userDetails.projectId || '');
localStorage.setItem('projectName', userDetails.projectName || '');
localStorage.setItem('enableToUseDevops', userDetails.enableToUseDevops);

// Step 5: Redirect based on role and access
if (userDetails.roles.includes('ADMIN')) {
  navigate('/admin/dashboard');
} else if (userDetails.projectId || userDetails.enableToUseDevops) {
  navigate('/dashboard');
} else {
  navigate('/access-denied');
}
```

### React Hook Example

```javascript
import { useState, useEffect } from 'react';

function useCurrentUser() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          throw new Error('No token found');
        }

        const response = await fetch('/api/auth/me', {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });

        if (!response.ok) {
          throw new Error('Failed to fetch user');
        }

        const data = await response.json();
        setUser(data);
      } catch (err) {
        setError(err.message);
        // Redirect to login if token is invalid
        if (err.message === 'No token found') {
          window.location.href = '/login';
        }
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  return { user, loading, error };
}

// Usage in component
function Dashboard() {
  const { user, loading, error } = useCurrentUser();

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div>
      <h1>Welcome, {user.username}!</h1>
      {user.projectName && <p>Project: {user.projectName}</p>}
      {user.enableToUseDevops && <p>DevOps Access: Enabled</p>}
    </div>
  );
}
```

### API Utility Function

```javascript
// api.js
export async function getCurrentUser() {
  const token = localStorage.getItem('token');
  
  if (!token) {
    throw new Error('Not authenticated');
  }

  const response = await fetch('/api/auth/me', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (response.status === 401) {
    // Token expired or invalid
    localStorage.clear();
    window.location.href = '/login';
    throw new Error('Session expired');
  }

  if (!response.ok) {
    throw new Error('Failed to fetch user details');
  }

  return await response.json();
}

// Usage
import { getCurrentUser } from './api';

async function loadUserDetails() {
  try {
    const user = await getCurrentUser();
    console.log('User details:', user);
    
    // Store in state/context
    setCurrentUser(user);
    
    // Use the details
    if (user.projectName) {
      console.log(`User is working on: ${user.projectName}`);
    }
  } catch (error) {
    console.error('Error loading user:', error);
  }
}
```

---

## Use Cases

### Use Case 1: Dashboard Initialization
```javascript
// When user lands on dashboard after login
useEffect(() => {
  async function initDashboard() {
    const user = await getCurrentUser();
    
    // Display welcome message with project name
    setWelcomeMessage(`Welcome to ${user.projectName || 'DevOps Platform'}`);
    
    // Show/hide features based on access
    if (user.enableToUseDevops) {
      enableDevOpsFeatures();
    }
    
    // Set page title
    document.title = `${user.projectName || 'Dashboard'} - DevOps`;
  }
  
  initDashboard();
}, []);
```

### Use Case 2: Permission Checks
```javascript
function FeatureButton({ feature }) {
  const { user } = useCurrentUser();
  
  // Check if user has access
  const hasAccess = user?.enableToUseDevops || user?.roles.includes('ADMIN');
  
  if (!hasAccess) {
    return <div>Contact admin for access</div>;
  }
  
  return <button>Use {feature}</button>;
}
```

### Use Case 3: Project Context
```javascript
// ProjectProvider.js
export function ProjectProvider({ children }) {
  const [project, setProject] = useState(null);
  
  useEffect(() => {
    async function loadProject() {
      const user = await getCurrentUser();
      
      if (user.projectId) {
        setProject({
          id: user.projectId,
          name: user.projectName
        });
      }
    }
    
    loadProject();
  }, []);
  
  return (
    <ProjectContext.Provider value={project}>
      {children}
    </ProjectContext.Provider>
  );
}
```

---

## Error Responses

### 401 Unauthorized - Invalid/Expired Token
```json
{
  "message": "Not authenticated"
}
```

**Action**: Redirect user to login page

### 401 Unauthorized - No Token
```json
{
  "message": "Not authenticated"
}
```

**Action**: Redirect user to login page

---

## Postman Testing

### Step 1: Login to Get Token
```
POST http://localhost:8080/api/auth/login

Body (raw JSON):
{
  "email": "admin@devops.com",
  "password": "admin123"
}

Copy the "token" from response
```

### Step 2: Get Current User Details
```
GET http://localhost:8080/api/auth/me

Headers:
Authorization: Bearer <paste_token_here>

Expected Response:
{
  "id": "...",
  "username": "admin",
  "email": "admin@devops.com",
  "roles": ["ADMIN", "USER"],
  "projectId": null,
  "projectName": null,
  "enableToUseDevops": true,
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

---

## Benefits

✅ **Complete Profile**: Get all user details in one call  
✅ **Project Context**: Includes both projectId and projectName  
✅ **Permission Info**: Know what user can/cannot do  
✅ **Single Source of Truth**: Always get latest user status  
✅ **Token Validation**: Automatically validates JWT token  
✅ **Error Handling**: Clear error responses for auth issues  

---

## Best Practices

1. **Call After Login**: Always call `/me` after successful login to get complete details
2. **Cache Response**: Store user details in state/context, don't call on every page
3. **Refresh on Update**: Call again after user profile updates
4. **Handle Errors**: Redirect to login on 401 errors
5. **Use for Auth Checks**: Use returned data to show/hide features

---

## Security Notes

- Token is validated on every request
- User details are fetched from authenticated session
- Password is never included in response
- Project name is safely fetched (returns null if project deleted)
- All timestamps are in UTC milliseconds

---

**Last Updated**: December 21, 2024  
**Status**: ✅ Complete and Working  
**Build**: ✅ Successful

