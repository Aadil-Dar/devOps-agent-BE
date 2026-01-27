# Assign Project to User - API Guide

## Endpoint: POST /api/admin/users/{userId}/assign-project

### Description
Assigns a project to a user and optionally enables DevOps access. Both `projectId` and `enableToUseDevops` can be provided in a single request.

### Authorization
- **Required Role**: ADMIN
- **Header**: `Authorization: Bearer <admin_jwt_token>`

---

## Postman Configuration

### Request Details

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/admin/users/{userId}/assign-project`
  - Replace `{userId}` with the actual user ID

### Headers
```
Authorization: Bearer <your_admin_jwt_token>
Content-Type: application/json
```

### Request Body Options

#### Option 1: Assign Project with DevOps Access Enabled
```json
{
  "projectId": "project-123",
  "enableToUseDevops": true
}
```

#### Option 2: Assign Project WITHOUT DevOps Access (default)
```json
{
  "projectId": "project-123",
  "enableToUseDevops": false
}
```

#### Option 3: Assign Project Only (DevOps defaults to false)
```json
{
  "projectId": "project-123"
}
```

---

## Response Examples

### Success Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "roles": ["USER"],
  "projectId": "project-123",
  "enableToUseDevops": true,
  "enabled": true,
  "createdAt": 1703001600000,
  "updatedAt": 1703088000000
}
```

### Error Responses

#### 400 Bad Request - Missing Project ID
```json
{
  "message": "Project ID is required"
}
```

#### 400 Bad Request - Project Not Found
```json
{
  "message": "Project not found: project-123"
}
```

#### 400 Bad Request - User Not Found
```json
{
  "message": "User not found"
}
```

#### 401 Unauthorized - No/Invalid Token
```json
{
  "message": "Unauthorized"
}
```

#### 403 Forbidden - Not Admin
```json
{
  "message": "Access Denied"
}
```

---

## Step-by-Step Postman Guide

### Step 1: Get Admin Token
1. Create a new request: `POST http://localhost:8080/api/auth/login`
2. Body (raw JSON):
   ```json
   {
     "email": "admin@devops.com",
     "password": "admin123"
   }
   ```
3. Copy the `token` from the response

### Step 2: Get User ID
1. Create a new request: `GET http://localhost:8080/api/admin/users`
2. Add Header: `Authorization: Bearer <token_from_step_1>`
3. Find the user you want to assign a project to
4. Copy their `id`

### Step 3: Assign Project
1. Create a new request: `POST http://localhost:8080/api/admin/users/{userId}/assign-project`
2. Replace `{userId}` with the ID from Step 2
3. Add Headers:
   - `Authorization: Bearer <token_from_step_1>`
   - `Content-Type: application/json`
4. Body (raw JSON):
   ```json
   {
     "projectId": "your-project-id",
     "enableToUseDevops": true
   }
   ```
5. Click **Send**

---

## Use Cases

### Use Case 1: Grant Full Access
**Scenario**: User needs both project assignment and DevOps access
```json
{
  "projectId": "production-app",
  "enableToUseDevops": true
}
```

### Use Case 2: Assign Project Without DevOps Access
**Scenario**: User can see the project but cannot use DevOps tools
```json
{
  "projectId": "production-app",
  "enableToUseDevops": false
}
```

### Use Case 3: Temporary Project Assignment
**Scenario**: Assign project now, enable DevOps later via `/toggle-access` endpoint
```json
{
  "projectId": "production-app"
}
```
This defaults `enableToUseDevops` to `false`. Admin can enable it later.

---

## Testing in Postman

### Test 1: Valid Assignment
```bash
POST http://localhost:8080/api/admin/users/123e4567-e89b-12d3-a456-426614174000/assign-project

Headers:
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

Body:
{
  "projectId": "test-project",
  "enableToUseDevops": true
}

Expected: 200 OK with user details
```

### Test 2: Missing ProjectId
```bash
Body:
{
  "enableToUseDevops": true
}

Expected: 400 Bad Request - "Project ID is required"
```

### Test 3: Invalid Project
```bash
Body:
{
  "projectId": "non-existent-project",
  "enableToUseDevops": true
}

Expected: 400 Bad Request - "Project not found: non-existent-project"
```

---

## Key Features

✅ **Single Request**: Set both project and DevOps access in one call
✅ **Optional DevOps Flag**: Defaults to `false` if not provided
✅ **Project Validation**: Verifies project exists before assignment
✅ **User Validation**: Checks user exists before updating
✅ **Safe Response**: Passwords never included in response
✅ **Timestamps**: `updatedAt` automatically updated

---

## Related Endpoints

- **Toggle DevOps Only**: `POST /api/admin/users/{userId}/toggle-access`
- **Remove Project**: `POST /api/admin/users/{userId}/remove-project`
- **Update User**: `PUT /api/admin/users/{userId}`
- **Get All Users**: `GET /api/admin/users`

---

## Notes

1. **Project Must Exist**: The project must exist in the system before assignment
2. **Default Behavior**: If `enableToUseDevops` is not provided, it defaults to `false`
3. **Login Impact**: After assignment with `enableToUseDevops: true`, user can login successfully
4. **Admin Only**: Only users with ADMIN role can access this endpoint
5. **Atomic Operation**: Both fields are updated together in a single transaction

---

**Last Updated**: December 21, 2024
**Status**: ✅ Ready to Use

