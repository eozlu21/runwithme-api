# JWT Authentication Usage Guide

## Overview
JWT authentication has been added to your Spring Boot API. All API endpoints under `/api/v1/` (except `/api/v1/auth/**`) now require authentication.

## Configuration

### Environment Variables
Set the following in production (via Docker, EC2, etc.):
```bash
JWT_SECRET=your-very-long-secure-secret-key-at-least-256-bits
```

The secret should be base64 encoded or at least 256 bits long for HS256 algorithm.

### Token Expiration
- Access Token: 24 hours (86400000 ms)
- Refresh Token: 7 days (604800000 ms)

You can modify these in `application.properties`.

## API Endpoints

### 1. Register a New User
**POST** `/api/v1/auth/register`

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "user": {
    "userId": 1,
    "username": "johndoe",
    "email": "john@example.com",
    "createdAt": "2025-11-13T10:30:00Z"
  }
}
```

### 2. Login
**POST** `/api/v1/auth/login`

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "password123"
  }'
```

**Response:** Same as register response.

### 3. Refresh Access Token
**POST** `/api/v1/auth/refresh`

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

**Response:** New access and refresh tokens.

### 4. Access Protected Endpoints
Use the `accessToken` in the `Authorization` header with `Bearer` prefix:

```bash
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## Client Implementation Examples

### JavaScript/Frontend
```javascript
// Store tokens after login/register
const { accessToken, refreshToken, user } = response.data;
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);

// Add to all API requests
const config = {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
};

// Make authenticated request
axios.get('http://localhost:8080/api/v1/users', config)
  .then(response => console.log(response.data))
  .catch(error => {
    if (error.response.status === 401) {
      // Token expired, refresh it
      refreshAccessToken();
    }
  });

// Refresh token function
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  const response = await axios.post('http://localhost:8080/api/v1/auth/refresh', {
    refreshToken
  });
  localStorage.setItem('accessToken', response.data.accessToken);
  localStorage.setItem('refreshToken', response.data.refreshToken);
}
```

### Python
```python
import requests

# Login
response = requests.post('http://localhost:8080/api/v1/auth/login', json={
    'username': 'johndoe',
    'password': 'password123'
})
tokens = response.json()
access_token = tokens['accessToken']

# Use token in requests
headers = {'Authorization': f'Bearer {access_token}'}
response = requests.get('http://localhost:8080/api/v1/users', headers=headers)
users = response.json()
```

### Java/Android
```java
// Add token to request
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("http://localhost:8080/api/v1/users")
    .addHeader("Authorization", "Bearer " + accessToken)
    .build();

Response response = client.newCall(request).execute();
```

## Testing with Swagger UI

1. Start your application
2. Go to http://localhost:8080/swagger-ui.html
3. Register or login using the auth endpoints
4. Copy the `accessToken` from the response
5. Click the "Authorize" button at the top of Swagger UI
6. Enter: `Bearer <your-access-token>`
7. Now you can test all protected endpoints

## Security Best Practices

1. **Use HTTPS in production** - Never send JWT over HTTP
2. **Rotate JWT secret** - Change it periodically
3. **Short expiration** - Keep access tokens short-lived (15-30 min in production)
4. **Secure storage** - Store tokens securely on client (HttpOnly cookies for web)
5. **Validate on server** - Always validate tokens on the backend
6. **Handle token expiration** - Implement automatic refresh logic

## Common HTTP Status Codes

- `200` - Success
- `400` - Bad request (validation error, duplicate username/email)
- `401` - Unauthorized (invalid credentials or expired token)
- `403` - Forbidden (valid token but insufficient permissions)
- `404` - Not found

## Troubleshooting

### "401 Unauthorized" on protected endpoints
- Ensure you're sending the Authorization header
- Check token hasn't expired
- Verify token format: `Bearer <token>`

### "Invalid JWT signature"
- JWT_SECRET mismatch between environments
- Token was generated with different secret

### "Token expired"
- Use the refresh token endpoint to get a new access token
- If refresh token is also expired, user must login again

