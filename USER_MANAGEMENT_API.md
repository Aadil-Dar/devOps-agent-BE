# User Management API Documentation

## Table of Contents
1. [Overview](#overview)
2. [Authentication Flow](#authentication-flow)
3. [API Endpoints](#api-endpoints)
4. [Request Examples](#request-examples)
5. [Frontend Integration](#frontend-integration)

## Overview

The User Management API provides complete authentication and authorization capabilities with:
- JWT-based authentication
- Role-based access control (ADMIN, USER)
- Project-level authorization
- DynamoDB persistence
- Admin user management

## Authentication Flow

### User Registration → Admin Approval → User Access

```
┌─────────────┐
│ 1. Register │  User creates account
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│ Account Created         │  enableToUseDevops = false
│ - No project assigned   │  projectId = null
│ - Cannot login yet      │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ 2. Admin Grants Access  │  Admin assigns:
│ - Assign project, OR    │  - projectId, OR
│ - Enable DevOps access  │  - enableToUseDevops = true
└──────┬──────────────────┘
       │
       ▼
┌─────────────┐
│ 3. Login OK │  User can now login
└─────────────┘
```

## API Endpoints

### Public Endpoints (No Authentication)

#### POST /api/auth/register
Register a new user account.

**Request:**
```json
{
  "username": "string",      // Required, 3-20 characters
  "email": "string",         // Required, valid email
  "password": "string"       // Required, 6-40 characters
}
```

**Response:**
```json
{
  "message": "User registered successfully!"
}
```

**Errors:**
- 400: Username already taken
- 400: Email already in use
- 400: Validation errors

---

#### POST /api/auth/login
Authenticate and receive JWT token.

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Success Response (Admin):**
```json
{
  "token": "eyJhbGci...",
  "type": "Bearer",
  "id": "user-uuid",
  "username": "admin",
  "email": "admin@example.com",
  "roles": ["ADMIN", "USER"],
  "projectId": null,
  "enableToUseDevops": true
}
```

**Success Response (User with Access):**
```json
{
  "token": "eyJhbGci...",
  "type": "Bearer",
  "id": "user-uuid",
  "username": "john",
  "email": "john@example.com",
  "roles": ["USER"],
  "projectId": "project-123",
  "enableToUseDevops": true
}
```

**Error Response (No Access):**
```json
{
  "message": "Access denied. Please contact admin for project access."
}
```
HTTP Status: 403

**Error Response (Invalid Credentials):**
```json
{
  "message": "Error: Invalid username or password"
}
```
HTTP Status: 400

---

### Authenticated Endpoints

#### GET /api/auth/me
Get current authenticated user information.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "email": "john@example.com",
  "roles": ["USER"],
  "projectId": "project-123",
  "enableToUseDevops": true
}
```

---

#### GET /api/auth/validate
Validate JWT token.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "message": "Token is valid"
}
```

**Error:**
```json
{
  "message": "Invalid token"
}
```
HTTP Status: 401

---

### Admin Endpoints (Require ADMIN Role)

#### GET /api/admin/users
Get all users in the system.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response:**
```json
[
  {
    "id": "user-uuid",
    "username": "john",
    "email": "john@example.com",
    "roles": ["USER"],
    "projectId": "project-123",
    "enableToUseDevops": true,
    "enabled": true,
    "createdAt": 1234567890000,
    "updatedAt": 1234567890000
  }
]
```

---

#### GET /api/admin/users/{userId}
Get specific user by ID.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "email": "john@example.com",
  "roles": ["USER"],
  "projectId": "project-123",
  "enableToUseDevops": true,
  "enabled": true,
  "createdAt": 1234567890000,
  "updatedAt": 1234567890000
}
```

---

#### POST /api/admin/users/{userId}/assign-project
Assign a project to a user. Also automatically enables DevOps access.

**Headers:**
```
Authorization: Bearer <admin-token>
Content-Type: application/json
```

**Request:**
```json
{
  "projectId": "project-123"
}
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "projectId": "project-123",
  "enableToUseDevops": true,
  ...
}
```

**Errors:**
- 400: Project not found
- 400: Project ID is required

---

#### POST /api/admin/users/{userId}/remove-project
Remove project assignment from user.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "projectId": null,
  ...
}
```

---

#### POST /api/admin/users/{userId}/toggle-access
Enable or disable DevOps access for user.

**Headers:**
```
Authorization: Bearer <admin-token>
Content-Type: application/json
```

**Request:**
```json
{
  "enable": true
}
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "enableToUseDevops": true,
  ...
}
```

---

#### PUT /api/admin/users/{userId}
Update user details.

**Headers:**
```
Authorization: Bearer <admin-token>
Content-Type: application/json
```

**Request:**
```json
{
  "email": "newemail@example.com",
  "projectId": "project-456",
  "enableToUseDevops": false,
  "enabled": true,
  "roles": ["USER"]
}
```

**Response:**
```json
{
  "id": "user-uuid",
  "username": "john",
  "email": "newemail@example.com",
  "projectId": "project-456",
  "enableToUseDevops": false,
  "enabled": true,
  "roles": ["USER"],
  ...
}
```

---

#### DELETE /api/admin/users/{userId}
Delete a user. Cannot delete admin users.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response:**
```json
{
  "message": "User deleted successfully"
}
```

**Errors:**
- 400: Cannot delete admin user
- 404: User not found

---

#### GET /api/admin/users/by-project/{projectId}
Get all users assigned to a specific project.

**Headers:**
```
Authorization: Bearer <admin-token>
```

**Response:**
```json
[
  {
    "id": "user-uuid-1",
    "username": "john",
    "projectId": "project-123",
    ...
  },
  {
    "id": "user-uuid-2",
    "username": "jane",
    "projectId": "project-123",
    ...
  }
]
```

---

## Request Examples

### Example 1: Complete User Registration Flow

```bash
# Step 1: User registers
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "password": "securepass123"
  }'

# Response: {"message": "User registered successfully!"}

# Step 2: User tries to login (WILL FAIL)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "securepass123"
  }'

# Response: {"message": "Access denied. Please contact admin for project access."}
# HTTP Status: 403

# Step 3: Admin enables DevOps access
ADMIN_TOKEN="<admin-jwt-token>"
USER_ID="<new-user-id>"

curl -X POST http://localhost:8080/api/admin/users/$USER_ID/toggle-access \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "enable": true
  }'

# Step 4: User tries to login again (WILL SUCCEED)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "securepass123"
  }'

# Response: {"token": "...", "username": "newuser", ...}
```

### Example 2: Assign Project to User

```bash
# Admin assigns project to user
curl -X POST http://localhost:8080/api/admin/users/{userId}/assign-project \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "my-project-123"
  }'

# This also automatically sets enableToUseDevops = true
```

### Example 3: Admin Login

```bash
# Default admin credentials
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'

# Response includes full user details with roles
```

---

## Frontend Integration

### localStorage Structure

After successful login, store:

```javascript
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
});

const data = await loginResponse.json();

// Store all relevant data
localStorage.setItem('token', data.token);
localStorage.setItem('userId', data.id);
localStorage.setItem('username', data.username);
localStorage.setItem('email', data.email);
localStorage.setItem('roles', JSON.stringify(data.roles));
localStorage.setItem('projectId', data.projectId || '');
localStorage.setItem('enableToUseDevops', data.enableToUseDevops);
```

### Making Authenticated Requests

```javascript
// Include token in all requests
const token = localStorage.getItem('token');
const projectId = localStorage.getItem('projectId');

fetch('/api/some-endpoint', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'X-Project-Id': projectId,  // For project-specific requests
    'Content-Type': 'application/json'
  }
});
```

### Login Component Example

```javascript
async function handleLogin(username, password) {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    if (response.status === 403) {
      // No access
      showError('Please contact admin for project access');
      return;
    }

    if (!response.ok) {
      showError('Invalid username or password');
      return;
    }

    const data = await response.json();
    
    // Store user data
    storeUserData(data);

    // Check user role and redirect
    if (data.roles.includes('ADMIN')) {
      navigate('/admin/dashboard');
    } else if (data.projectId || data.enableToUseDevops) {
      navigate('/dashboard');
    } else {
      showError('Access denied');
    }
  } catch (error) {
    console.error('Login error:', error);
    showError('Login failed');
  }
}
```

### Admin User Management Component

```javascript
// Get all users
async function fetchUsers() {
  const token = localStorage.getItem('token');
  const response = await fetch('/api/admin/users', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
}

// Assign project to user
async function assignProject(userId, projectId) {
  const token = localStorage.getItem('token');
  const response = await fetch(`/api/admin/users/${userId}/assign-project`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ projectId })
  });
  return await response.json();
}

// Enable DevOps access
async function enableAccess(userId) {
  const token = localStorage.getItem('token');
  const response = await fetch(`/api/admin/users/${userId}/toggle-access`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ enable: true })
  });
  return await response.json();
}
```

### Access Denied Screen Component

```javascript
function AccessDeniedScreen() {
  return (
    <div className="access-denied">
      <h1>Access Restricted</h1>
      <p>Please contact your administrator for project access.</p>
      <p>Your account has been created but needs approval.</p>
      <button onClick={logout}>Logout</button>
    </div>
  );
}
```

---

## Security Considerations

1. **Token Storage**: Store JWT in localStorage or httpOnly cookies
2. **Token Expiration**: Default 24 hours (86400000ms)
3. **Password Requirements**: Minimum 6 characters
4. **Role Checks**: All admin endpoints enforce ROLE_ADMIN
5. **Project Isolation**: Users can only access their assigned project
6. **Password Hashing**: BCrypt with default strength

---

## Error Handling

### Common Error Responses

**401 Unauthorized:**
```json
{
  "message": "Invalid token"
}
```

**403 Forbidden:**
```json
{
  "message": "Access denied. Please contact admin for project access."
}
```

**400 Bad Request:**
```json
{
  "message": "Error: Username is already taken!"
}
```

**404 Not Found:**
```json
{
  "message": "User not found"
}
```

**500 Internal Server Error:**
```json
{
  "message": "Error fetching users: <error details>"
}
```

---

## Testing

Use the provided test script:

```bash
./test-user-management.sh
```

This script tests:
- Admin login
- User registration
- Access denial without permission
- Admin granting access
- Successful user login after access grant
- All admin user management operations

---

## Default Credentials

**Admin Account:**
- Username: `admin`
- Password: `admin123`
- Roles: ADMIN, USER
- Access: Full system access

**⚠️ IMPORTANT:** Change the default admin password in production!

