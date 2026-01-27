# Get All Users API - Updated Response

## Endpoint: GET /api/admin/users

### What Changed
The `getAllUsers()` endpoint now returns the `projectName` for each user in addition to `projectId`.

- If user has a `projectId` assigned, the API fetches the project details and includes the `projectName`
- If user has no project assigned, `projectName` will be `null`
- If project doesn't exist (deleted), `projectName` will be `null`

---

## Postman Request

**Method**: `GET`  
**URL**: `http://localhost:8080/api/admin/users`  
**Headers**:
```
Authorization: Bearer <admin_jwt_token>
```

---

## Updated Response Example

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "roles": ["USER"],
    "projectId": "project-123",
    "projectName": "My Production App",  ← NEW!
    "enableToUseDevops": true,
    "enabled": true,
    "createdAt": 1703001600000,
    "updatedAt": 1703088000000
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "username": "jane_smith",
    "email": "jane@example.com",
    "roles": ["USER"],
    "projectId": null,
    "projectName": null,  ← NULL when no project assigned
    "enableToUseDevops": false,
    "enabled": true,
    "createdAt": 1703001600000,
    "updatedAt": 1703088000000
  },
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
]
```

---

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | User unique identifier (UUID) |
| `username` | String | User's username |
| `email` | String | User's email address |
| `roles` | Array | User's roles (e.g., ["USER"], ["ADMIN", "USER"]) |
| `projectId` | String/null | Assigned project ID or null |
| `projectName` | String/null | **NEW!** Project name fetched from project config, or null |
| `enableToUseDevops` | Boolean | Whether user has DevOps access enabled |
| `enabled` | Boolean | Whether user account is active |
| `createdAt` | Long | Unix timestamp (milliseconds) when user was created |
| `updatedAt` | Long | Unix timestamp (milliseconds) when user was last updated |

---

## Benefits

✅ **No Additional API Call**: Frontend gets project name directly in user list  
✅ **Better UX**: Display project name instead of just ID  
✅ **Safe**: Returns null if project doesn't exist (won't crash)  
✅ **Consistent**: All user endpoints (getAllUsers, getUserById, etc.) now return projectName  

---

## Frontend Usage

```javascript
// Fetch all users
const response = await fetch('/api/admin/users', {
  headers: {
    'Authorization': `Bearer ${adminToken}`
  }
});

const users = await response.json();

// Display in a table
users.forEach(user => {
  console.log(`${user.username} - ${user.projectName || 'No Project'}`);
});

// Example output:
// john_doe - My Production App
// jane_smith - No Project
// admin - No Project
```

---

## Error Handling

The API gracefully handles errors:
- If project fetch fails, `projectName` will be `null` (logged as warning)
- User list still returns successfully
- No impact on other user data

---

## Testing in Postman

### Step 1: Login as Admin
```bash
POST http://localhost:8080/api/auth/login

Body:
{
  "email": "admin@devops.com",
  "password": "admin123"
}

Copy the token
```

### Step 2: Get All Users
```bash
GET http://localhost:8080/api/admin/users

Headers:
Authorization: Bearer <token_from_step_1>

Response will include projectName for each user
```

---

## Related Endpoints

All these endpoints also return `projectName`:

- `GET /api/admin/users/{userId}` - Get single user
- `POST /api/admin/users/{userId}/assign-project` - Assign project (returns updated user with projectName)
- `GET /api/admin/users/by-project/{projectId}` - Get users by project
- `PUT /api/admin/users/{userId}` - Update user

---

**Updated**: December 21, 2024  
**Status**: ✅ Complete and Working  
**Build**: ✅ Successful

