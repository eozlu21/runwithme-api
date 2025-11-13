# JWT Authentication Quick Reference

## Quick Start

### 1. Register a new user:
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "email": "john@test.com", "password": "pass123"}'
```

### 2. Login:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "password": "pass123"}'
```

### 3. Use the token:
```bash
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE"
```

### 4. Refresh token:
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "YOUR_REFRESH_TOKEN_HERE"}'
```

## What Changed

### Protected Endpoints
All `/api/v1/*` endpoints now require authentication EXCEPT:
- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/refresh`

Also still public:
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/**`

### New Files Added
1. `JwtTokenProvider.kt` - Generates and validates JWT tokens
2. `JwtAuthenticationFilter.kt` - Intercepts requests and validates tokens
3. `CustomUserDetailsService.kt` - Loads user details for authentication
4. `AuthController.kt` - Handles login, register, refresh
5. `AuthService.kt` - Business logic for authentication
6. `AuthDto.kt` - DTOs for auth requests/responses

### Configuration
`application.properties` now includes:
```properties
jwt.secret=your-256-bit-secret-key-change-this-in-production
jwt.expiration=86400000           # 24 hours
jwt.refresh-expiration=604800000  # 7 days
```

## Testing

Run the automated test:
```bash
./test-jwt-auth.sh
# or for remote server:
./test-jwt-auth.sh http://your-server:8080
```

## For Production Deployment

### Add GitHub Secret
Go to your GitHub repo → Settings → Secrets → Actions → New secret:
- Name: `JWT_SECRET`
- Value: Generate a strong secret (see below)

### Generate Strong Secret
```bash
# Using OpenSSL:
openssl rand -base64 64

# Using Python:
python3 -c "import secrets; print(secrets.token_urlsafe(64))"

# Using Node.js:
node -e "console.log(require('crypto').randomBytes(64).toString('base64'))"
```

### Environment Variable on EC2
If deploying manually:
```bash
export JWT_SECRET="your-generated-secret-here"
```

Or in Docker:
```bash
docker run -e JWT_SECRET="your-secret" ...
```

## Token Flow

```
1. User registers/logs in
   → Server returns accessToken + refreshToken

2. Client stores both tokens
   → LocalStorage, SessionStorage, or HttpOnly cookies

3. Client makes API request with accessToken
   → Header: "Authorization: Bearer <accessToken>"

4. Access token expires (24h)
   → Client uses refreshToken to get new tokens

5. Refresh token expires (7 days)
   → User must login again
```

## Common Errors

**"Full authentication is required"**
- Missing Authorization header
- Token not prefixed with "Bearer "

**"JWT signature does not match"**
- JWT_SECRET mismatch
- Token was created with different secret

**"JWT expired"**
- Use refresh token endpoint to get new token

**401 Unauthorized**
- Invalid credentials (login)
- Expired or invalid token (protected endpoints)

## Integration Examples

### React/Next.js
```javascript
// Login
const login = async (username, password) => {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const { accessToken, refreshToken } = await res.json();
  localStorage.setItem('accessToken', accessToken);
  localStorage.setItem('refreshToken', refreshToken);
};

// API call with auth
const fetchUsers = async () => {
  const token = localStorage.getItem('accessToken');
  const res = await fetch('/api/v1/users', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return res.json();
};
```

### Android (Kotlin)
```kotlin
// Login
val retrofit = Retrofit.Builder()
    .baseUrl("http://your-api.com")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val authService = retrofit.create(AuthService::class.java)
val response = authService.login(LoginRequest("john", "pass123")).execute()
val accessToken = response.body()?.accessToken

// Add interceptor for auth
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        chain.proceed(request)
    }
    .build()
```

### iOS (Swift)
```swift
// Login
let loginData = ["username": "john", "password": "pass123"]
let url = URL(string: "http://your-api.com/api/v1/auth/login")!
var request = URLRequest(url: url)
request.httpMethod = "POST"
request.setValue("application/json", forHTTPHeaderField: "Content-Type")
request.httpBody = try? JSONSerialization.data(withJSONObject: loginData)

// Store token in Keychain (recommended)
// Use for authenticated requests:
request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
```

## Security Checklist

- [ ] Use HTTPS in production
- [ ] Generate strong JWT_SECRET (64+ characters)
- [ ] Store tokens securely on client
- [ ] Implement token refresh logic
- [ ] Handle token expiration gracefully
- [ ] Add rate limiting (optional, for production)
- [ ] Use short-lived access tokens (15-30 min for production)
- [ ] Implement logout (optional: token blacklist)
- [ ] Validate inputs on registration
- [ ] Use strong password requirements

## Need Help?

1. Check `JWT_AUTHENTICATION_GUIDE.md` for detailed documentation
2. Test with Swagger UI at http://localhost:8080/swagger-ui.html
3. Run `./test-jwt-auth.sh` to verify setup

