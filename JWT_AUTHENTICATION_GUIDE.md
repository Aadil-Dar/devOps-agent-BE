# JWT Authentication Implementation Guide

## Overview
This application now includes JWT (JSON Web Token) authentication for secure API access. The implementation follows Spring Security best practices and provides a complete authentication system.

## Architecture

### Components

1. **JwtUtil** - Handles JWT token generation, validation, and parsing
2. **JwtAuthenticationFilter** - Intercepts requests to validate JWT tokens
3. **JwtAuthenticationEntryPoint** - Handles unauthorized access attempts
4. **SecurityConfig** - Configures Spring Security with JWT authentication
5. **UserService** - Manages user data and implements UserDetailsService
6. **AuthController** - Provides authentication endpoints

## Configuration

### Application Properties

The JWT configuration is defined in `application.properties`:

```properties
# JWT Configuration
jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration=86400000
```

- **jwt.secret**: Base64-encoded secret key for signing tokens (change this in production!)
- **jwt.expiration**: Token expiration time in milliseconds (default: 24 hours)

## Default Users

Two default users are created on application startup:

### Admin User
- **Username**: `admin`
- **Password**: `admin123`
- **Email**: `admin@devops.com`
- **Roles**: `ADMIN`, `USER`

### Regular User
- **Username**: `user`
- **Password**: `user123`
- **Email**: `user@devops.com`
- **Roles**: `USER`

## API Endpoints

### Authentication Endpoints (Public)

#### 1. Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "admin",
  "email": "admin@devops.com",
  "roles": ["ADMIN", "USER"]
}
```

#### 2. Register
```bash
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123",
  "email": "newuser@devops.com",
  "roles": ["USER"]
}
```

**Response:**
```json
{
  "message": "User registered successfully!"
}
```

#### 3. Validate Token
```bash
GET /api/auth/validate
Authorization: Bearer <your-jwt-token>
```

**Response:**
```json
{
  "message": "Token is valid"
}
```

#### 4. Get Current User
```bash
GET /api/auth/me
Authorization: Bearer <your-jwt-token>
```

**Response:**
```json
{
  "id": "uuid",
  "username": "admin",
  "email": "admin@devops.com",
  "roles": ["ADMIN", "USER"]
}
```

### Protected Endpoints

All other API endpoints now require authentication. Include the JWT token in the Authorization header:

```bash
Authorization: Bearer <your-jwt-token>
```

### Admin-Only Endpoints

Endpoints under `/api/admin/**` require the `ADMIN` role.

## Usage Examples

### 1. cURL Examples

#### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

#### Register New User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "developer",
    "password": "dev123456",
    "email": "developer@devops.com",
    "roles": ["USER"]
  }'
```

#### Access Protected Endpoint
```bash
# First, save the token from login response
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Then use it to access protected endpoints
curl -X GET http://localhost:8080/api/pipelines \
  -H "Authorization: Bearer $TOKEN"
```

### 2. JavaScript/Fetch Example

```javascript
// Login
async function login(username, password) {
  const response = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  // Store token in localStorage
  localStorage.setItem('token', data.token);
  return data;
}

// Access protected endpoint
async function fetchPipelines() {
  const token = localStorage.getItem('token');
  
  const response = await fetch('http://localhost:8080/api/pipelines', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  return await response.json();
}
```

### 3. Postman Setup

1. **Login Request**
   - Method: POST
   - URL: `http://localhost:8080/api/auth/login`
   - Body (JSON):
     ```json
     {
       "username": "admin",
       "password": "admin123"
     }
     ```
   - Add test script to save token:
     ```javascript
     pm.test("Save token", function() {
       var jsonData = pm.response.json();
       pm.environment.set("jwt_token", jsonData.token);
     });
     ```

2. **Protected Requests**
   - Add Authorization header: `Bearer {{jwt_token}}`
   - Or use Authorization tab -> Type: Bearer Token -> Token: `{{jwt_token}}`

## Security Features

### 1. Token-Based Authentication
- Stateless authentication using JWT
- No session management required
- Tokens contain user identity and roles

### 2. Password Encryption
- Passwords are encrypted using BCrypt
- Strength: 10 rounds (default)

### 3. Role-Based Access Control (RBAC)
- Method-level security with `@PreAuthorize`
- URL-based security in SecurityConfig
- Support for multiple roles per user

### 4. Token Validation
- Signature verification
- Expiration check
- User existence validation

### 5. CORS Configuration
- Configured in `CorsConfig.java`
- Allows cross-origin requests for web clients

## Advanced Usage

### Using Method Security

You can add method-level security to your controllers:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public ResponseEntity<?> getAllUsers() {
    // Only accessible by ADMIN role
}

@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
@GetMapping("/projects")
public ResponseEntity<?> getProjects() {
    // Accessible by both ADMIN and USER roles
}

@PreAuthorize("isAuthenticated()")
@GetMapping("/profile")
public ResponseEntity<?> getProfile() {
    // Accessible by any authenticated user
}
```

### Getting Current User in Controllers

```java
@GetMapping("/my-data")
public ResponseEntity<?> getMyData() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    User currentUser = (User) authentication.getPrincipal();
    
    // Use currentUser.getUsername(), currentUser.getRoles(), etc.
    return ResponseEntity.ok(currentUser);
}
```

## Error Responses

### 401 Unauthorized
Returned when:
- No token is provided
- Token is invalid or expired
- User doesn't exist

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/pipelines"
}
```

### 403 Forbidden
Returned when user doesn't have required role:

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied"
}
```

## Production Considerations

### 1. Change JWT Secret
Generate a strong, random secret key:

```bash
# Generate a secure random key
openssl rand -base64 64
```

Update `application.properties`:
```properties
jwt.secret=YOUR_GENERATED_SECRET_HERE
```

### 2. Use Environment Variables
Instead of hardcoding secrets:

```properties
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION:86400000}
```

### 3. Enable HTTPS
Always use HTTPS in production to prevent token interception.

### 4. Implement Token Refresh
Consider implementing refresh tokens for better security:
- Short-lived access tokens (15-30 minutes)
- Long-lived refresh tokens (7-30 days)
- Refresh token endpoint

### 5. Token Blacklist
Implement token blacklisting for logout functionality:
- Store revoked tokens in Redis/Database
- Check blacklist before validating tokens

### 6. Rate Limiting
Implement rate limiting on authentication endpoints to prevent brute force attacks.

### 7. User Storage
Replace in-memory user storage with a database:
- PostgreSQL, MySQL, or MongoDB
- Use Spring Data JPA or Spring Data MongoDB
- Implement proper user management (CRUD operations)

## Troubleshooting

### Token Expired Error
- Check `jwt.expiration` value
- Request a new token by logging in again

### Invalid Token Error
- Ensure token is properly formatted: `Bearer <token>`
- Check that the secret key hasn't changed
- Verify token wasn't modified

### 403 Forbidden on Admin Endpoints
- Verify user has ADMIN role
- Check token contains correct roles
- Ensure SecurityConfig role mappings are correct

## Testing

### Unit Tests
```java
@Test
void testTokenGeneration() {
    User user = User.builder()
        .username("testuser")
        .password("password")
        .roles(List.of("USER"))
        .build();
    
    String token = jwtUtil.generateToken(user);
    assertNotNull(token);
}
```

### Integration Tests
```java
@Test
void testLoginEndpoint() throws Exception {
    mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists());
}
```

## Migration Guide

If you have existing endpoints, they are now protected. Update your clients to:

1. Login first to obtain a token
2. Include the token in subsequent requests
3. Handle 401/403 responses by re-authenticating

## References

- [JWT Official Website](https://jwt.io/)
- [JJWT Library](https://github.com/jwtk/jjwt)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [OAuth 2.0 Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)

