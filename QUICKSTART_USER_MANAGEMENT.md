# 🚀 User Management System - Quick Start Guide

## ✅ Implementation Complete!

The backend now has a complete user management and authentication system with DynamoDB storage, role-based access control, and project-level authorization.

## 📋 Quick Reference

### Default Admin Account
```
Username: admin
Password: admin123
```

### Key Endpoints

#### Authentication
- **POST** `/api/auth/register` - Register new user
- **POST** `/api/auth/login` - Login and get JWT
- **GET** `/api/auth/me` - Get current user info

#### Admin User Management
- **GET** `/api/admin/users` - List all users
- **POST** `/api/admin/users/{userId}/assign-project` - Assign project
- **POST** `/api/admin/users/{userId}/toggle-access` - Enable/disable access
- **PUT** `/api/admin/users/{userId}` - Update user
- **DELETE** `/api/admin/users/{userId}` - Delete user

## 🔄 How It Works

### 1️⃣ User Registration Flow
```
User registers → Account created → Cannot login yet
→ Admin grants access → User can login
```

### 2️⃣ Login Logic

**Admin:**
- Always succeeds ✅
- Full access to all features

**Regular User:**
- Checks if `projectId` is assigned OR `enableToUseDevops = true`
- ✅ YES → Login successful
- ❌ NO → 403 Error: "Please contact admin for project access"

## 📦 What's Included

### New Files Created:
1. ✅ **UserManagementController.java** - Admin APIs
2. ✅ **ProjectIdInterceptor.java** - Project access validation  
3. ✅ **WebConfig.java** - Interceptor configuration
4. ✅ **USER_MANAGEMENT_GUIDE.md** - Full implementation guide
5. ✅ **USER_MANAGEMENT_API.md** - Complete API documentation
6. ✅ **test-user-management.sh** - Automated test script
7. ✅ **IMPLEMENTATION_SUMMARY.md** - Detailed summary

### Files Modified:
1. ✅ **User.java** - Added DynamoDB annotations, projectId, enableToUseDevops
2. ✅ **UserService.java** - DynamoDB storage instead of in-memory
3. ✅ **JwtResponse.java** - Added id, projectId, enableToUseDevops
4. ✅ **AuthController.java** - Enhanced login with access checks
5. ✅ **DynamoDbConfig.java** - Added users table
6. ✅ **DynamoDbInitService.java** - Auto-create tables
7. ✅ **application.properties** - Users table config

## 🧪 Testing

### Run Automated Tests
```bash
./test-user-management.sh
```

### Manual Test Flow
```bash
# 1. Admin login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2. Register new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","email":"test@example.com","password":"test123"}'

# 3. Try login (will fail - no access)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"test123"}'
# Expected: 403 - "Access denied. Please contact admin for project access."

# 4. Admin enables access (replace {userId} and {admin-token})
curl -X POST http://localhost:8080/api/admin/users/{userId}/toggle-access \
  -H "Authorization: Bearer {admin-token}" \
  -H "Content-Type: application/json" \
  -d '{"enable":true}'

# 5. Try login again (will succeed)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"test123"}'
# Expected: 200 - Returns JWT with user details
```

## 💻 Frontend Integration

### Login Component
```javascript
async function login(username, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });

  if (response.status === 403) {
    // No access - show message
    showAccessDeniedScreen();
    return;
  }

  if (response.ok) {
    const data = await response.json();
    
    // Store in localStorage
    localStorage.setItem('token', data.token);
    localStorage.setItem('userId', data.id);
    localStorage.setItem('username', data.username);
    localStorage.setItem('email', data.email);
    localStorage.setItem('roles', JSON.stringify(data.roles));
    localStorage.setItem('projectId', data.projectId || '');
    localStorage.setItem('enableToUseDevops', data.enableToUseDevops);
    
    // Redirect based on role
    if (data.roles.includes('ADMIN')) {
      navigate('/admin/dashboard');
    } else {
      navigate('/dashboard');
    }
  }
}
```

### API Requests
```javascript
// Include token and projectId in all requests
const headers = {
  'Authorization': `Bearer ${localStorage.getItem('token')}`,
  'X-Project-Id': localStorage.getItem('projectId'),
  'Content-Type': 'application/json'
};

fetch('/api/some-endpoint', { headers });
```

### Access Denied Screen
```javascript
function AccessDeniedScreen() {
  return (
    <div>
      <h1>Access Restricted</h1>
      <p>Please contact your administrator for project access.</p>
      <p>Your account has been created but needs approval.</p>
      <button onClick={logout}>Logout</button>
    </div>
  );
}
```

## 📊 User Data Model

```javascript
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "roles": ["USER", "ADMIN"],
  "projectId": "project-id or null",
  "enableToUseDevops": true/false,
  "enabled": true/false,
  "createdAt": timestamp,
  "updatedAt": timestamp
}
```

## 🗄️ Database

### DynamoDB Tables (Auto-created)
- **devops-users** - User accounts
- **devops-projects** - Project configurations

## 🔐 Security Features

- ✅ BCrypt password hashing
- ✅ JWT authentication (24h expiration)
- ✅ Role-based access control
- ✅ Project-level isolation
- ✅ Request validation via interceptors
- ✅ Admin-only operations protected

## 📚 Documentation

Read the detailed guides:
1. **USER_MANAGEMENT_GUIDE.md** - Complete implementation details
2. **USER_MANAGEMENT_API.md** - Full API reference
3. **IMPLEMENTATION_SUMMARY.md** - Technical summary

## 🚀 Running the Application

```bash
# Start LocalStack (for local development)
docker-compose up -d

# Build the application
./gradlew build

# Run the application
./gradlew bootRun
```

## ✨ Key Features

✅ **User Registration** - Users can create accounts
✅ **Admin Approval** - Admin controls who gets access
✅ **Project Assignment** - Users assigned to specific projects
✅ **Access Control** - Login checks for project access
✅ **JWT Tokens** - Secure authentication
✅ **DynamoDB Storage** - Persistent user data
✅ **Admin Panel APIs** - Complete user management
✅ **Project Isolation** - Users can only access assigned projects

## 🎯 Frontend TODO

### Login Page
- [ ] Handle 403 response (show "Contact admin" screen)
- [ ] Store all user data in localStorage
- [ ] Redirect admin to admin panel
- [ ] Redirect users to dashboard

### Admin Panel
- [ ] User list view
- [ ] Assign project to user
- [ ] Enable/disable DevOps access
- [ ] View user details
- [ ] Delete users

### Protected Routes
- [ ] Include Authorization header
- [ ] Include X-Project-Id header
- [ ] Handle 401 (redirect to login)
- [ ] Handle 403 (show access denied)

## 🐛 Troubleshooting

**User can't login?**
→ Check if enableToUseDevops is true OR projectId is assigned

**403 on API requests?**
→ Check JWT token and X-Project-Id header

**DynamoDB errors?**
→ Ensure LocalStack is running (local) or AWS credentials configured (prod)

## 📞 Need Help?

Check the documentation files for detailed information:
- Implementation details → **USER_MANAGEMENT_GUIDE.md**
- API reference → **USER_MANAGEMENT_API.md**
- Technical details → **IMPLEMENTATION_SUMMARY.md**

---

**Status:** ✅ Complete and Ready to Use
**Build:** ✅ Successful
**Tests:** ✅ Passing

**Next Steps:** Integrate with frontend and test end-to-end!

