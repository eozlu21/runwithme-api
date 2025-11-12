# Production Testing - Curl Examples

## Setup
Replace `YOUR_PRODUCTION_URL` with your actual production URL (e.g., `https://api.runwithme.com`)

```bash
# Set your production URL
export BASE_URL="YOUR_PRODUCTION_URL"
# Or for local testing:
# export BASE_URL="http://localhost:8080"
```

---

## 1. User Endpoints Testing

### 1.1 Create a New User
```bash
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser01",
    "email": "testuser01@example.com",
    "password": "SecurePassword123!"
  }'
```

**Expected Response (201 Created):**
```json
{
  "userId": 1,
  "username": "testuser01",
  "email": "testuser01@example.com",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Save the `userId` for subsequent requests!**

---

### 1.2 Create Another User (for pagination testing)
```bash
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser02",
    "email": "testuser02@example.com",
    "password": "AnotherPassword456!"
  }'
```

---

### 1.3 Get All Users (Paginated)
```bash
# First page (default: 10 items)
curl -X GET "${BASE_URL}/api/v1/users?page=0&size=10"

# Custom page size
curl -X GET "${BASE_URL}/api/v1/users?page=0&size=5"

# Second page
curl -X GET "${BASE_URL}/api/v1/users?page=1&size=10"
```

**Expected Response:**
```json
{
  "content": [
    {
      "userId": 1,
      "username": "testuser01",
      "email": "testuser01@example.com",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### 1.4 Get User by ID
```bash
# Replace {id} with actual user ID
curl -X GET "${BASE_URL}/api/v1/users/1"
```

---

### 1.5 Get User by Username
```bash
curl -X GET "${BASE_URL}/api/v1/users/username/testuser01"
```

---

### 1.6 Get User by Email
```bash
# Note: URL encode the @ symbol as %40
curl -X GET "${BASE_URL}/api/v1/users/email/testuser01%40example.com"

# Or use quotes
curl -X GET "${BASE_URL}/api/v1/users/email/testuser01@example.com"
```

---

### 1.7 Update User
```bash
# Update username only
curl -X PUT "${BASE_URL}/api/v1/users/1" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser01_updated"
  }'

# Update email only
curl -X PUT "${BASE_URL}/api/v1/users/1" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newemail@example.com"
  }'

# Update password only
curl -X PUT "${BASE_URL}/api/v1/users/1" \
  -H "Content-Type: application/json" \
  -d '{
    "password": "NewSecurePassword789!"
  }'

# Update multiple fields
curl -X PUT "${BASE_URL}/api/v1/users/1" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser01_final",
    "email": "final@example.com",
    "password": "FinalPassword999!"
  }'
```

---

### 1.8 Delete User
```bash
# WARNING: This will delete the user permanently
curl -X DELETE "${BASE_URL}/api/v1/users/1"
```

**Expected Response:** 204 No Content (empty response body)

---

## 2. User Profile Endpoints Testing

### 2.1 Create User Profile (Complete Example)
```bash
# First create a user if you don't have one
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "profiletest",
    "email": "profiletest@example.com",
    "password": "TestPassword123!"
  }'

# Then create profile using the returned userId (e.g., 2)
curl -X POST "${BASE_URL}/api/v1/user-profiles" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "firstName": "John",
    "lastName": "Doe",
    "pronouns": "he/him",
    "birthday": "1990-05-15",
    "expertLevel": "intermediate",
    "profilePic": "https://example.com/pictures/john.jpg",
    "profileVisibility": true,
    "regionId": 1,
    "subregionId": 10,
    "countryId": 100,
    "stateId": 34,
    "cityId": 2344
  }'
```

---

### 2.2 Create Minimal Profile (Only Required Fields)
```bash
curl -X POST "${BASE_URL}/api/v1/user-profiles" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 3,
    "profileVisibility": true
  }'
```

---

### 2.3 Get All User Profiles (Paginated)
```bash
curl -X GET "${BASE_URL}/api/v1/user-profiles?page=0&size=10"
```

---

### 2.4 Get User Profile by User ID
```bash
curl -X GET "${BASE_URL}/api/v1/user-profiles/2"
```

---

### 2.5 Update User Profile (Partial Update)
```bash
# Update name only
curl -X PUT "${BASE_URL}/api/v1/user-profiles/2" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jonathan",
    "lastName": "Doe-Smith"
  }'

# Update expert level
curl -X PUT "${BASE_URL}/api/v1/user-profiles/2" \
  -H "Content-Type: application/json" \
  -d '{
    "expertLevel": "advanced"
  }'

# Update visibility
curl -X PUT "${BASE_URL}/api/v1/user-profiles/2" \
  -H "Content-Type: application/json" \
  -d '{
    "profileVisibility": false
  }'

# Update location
curl -X PUT "${BASE_URL}/api/v1/user-profiles/2" \
  -H "Content-Type: application/json" \
  -d '{
    "regionId": 2,
    "subregionId": 20,
    "countryId": 200,
    "stateId": 50,
    "cityId": 5000
  }'

# Update multiple fields
curl -X PUT "${BASE_URL}/api/v1/user-profiles/2" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jonathan",
    "expertLevel": "expert",
    "profileVisibility": true,
    "cityId": 9999
  }'
```

---

### 2.6 Delete User Profile
```bash
curl -X DELETE "${BASE_URL}/api/v1/user-profiles/2"
```

**Expected Response:** 204 No Content

---

## 3. Complete Registration Flow Test

```bash
# Step 1: Create User Account
echo "Creating user account..."
USER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "completesuser",
    "email": "complete@example.com",
    "password": "CompletePass123!"
  }')

echo "$USER_RESPONSE"

# Extract userId (requires jq - install with: brew install jq)
USER_ID=$(echo "$USER_RESPONSE" | jq -r '.userId')
echo "Created user with ID: $USER_ID"

# Step 2: Create User Profile
echo "Creating user profile..."
curl -X POST "${BASE_URL}/api/v1/user-profiles" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": $USER_ID,
    \"firstName\": \"Complete\",
    \"lastName\": \"User\",
    \"birthday\": \"1995-03-20\",
    \"expertLevel\": \"beginner\",
    \"profileVisibility\": true
  }"
```

---

## 4. Error Testing

### 4.1 Test Duplicate Username
```bash
# Create first user
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "duplicate",
    "email": "first@example.com",
    "password": "Pass123!"
  }'

# Try to create another with same username (should fail)
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "duplicate",
    "email": "second@example.com",
    "password": "Pass456!"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "error": "Username already exists"
}
```

---

### 4.2 Test Duplicate Email
```bash
curl -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "different",
    "email": "first@example.com",
    "password": "Pass789!"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "error": "Email already exists"
}
```

---

### 4.3 Test Non-Existent User
```bash
# Try to get user with invalid ID
curl -X GET "${BASE_URL}/api/v1/users/99999"
```

**Expected Response:** 404 Not Found (empty body)

---

### 4.4 Test Duplicate Profile Creation
```bash
# Create profile
curl -X POST "${BASE_URL}/api/v1/user-profiles" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "firstName": "Test"
  }'

# Try to create another profile for same user (should fail)
curl -X POST "${BASE_URL}/api/v1/user-profiles" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "firstName": "Another"
  }'
```

**Expected Response (400 Bad Request):**
```json
{
  "error": "User profile already exists for user ID: 1"
}
```

---

## 5. Pretty Print & Verbose Output

### With Pretty JSON Output (requires jq)
```bash
curl -s -X GET "${BASE_URL}/api/v1/users/1" | jq '.'
```

### With Verbose Output (see headers)
```bash
curl -v -X GET "${BASE_URL}/api/v1/users/1"
```

### Save Response to File
```bash
curl -X GET "${BASE_URL}/api/v1/users/1" -o user_response.json
```

---

## 6. Bulk Testing Script

Save this as `test_api.sh`:

```bash
#!/bin/bash

BASE_URL="${1:-http://localhost:8080}"

echo "Testing API at: $BASE_URL"
echo "================================"

# Test 1: Create User
echo -e "\n1. Creating user..."
curl -s -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bulktest",
    "email": "bulktest@example.com",
    "password": "BulkTest123!"
  }' | jq '.'

# Test 2: Get All Users
echo -e "\n2. Getting all users..."
curl -s -X GET "${BASE_URL}/api/v1/users?page=0&size=5" | jq '.content | length'

# Test 3: Get by Username
echo -e "\n3. Getting user by username..."
curl -s -X GET "${BASE_URL}/api/v1/users/username/bulktest" | jq '.username'

# Test 4: Update User
echo -e "\n4. Updating user..."
curl -s -X PUT "${BASE_URL}/api/v1/users/1" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "updated@example.com"
  }' | jq '.email'

echo -e "\n================================"
echo "Testing complete!"
```

**Run with:**
```bash
chmod +x test_api.sh
./test_api.sh https://your-production-url.com
```

---

## 7. Health Check

```bash
# Check if API is up
curl -X GET "${BASE_URL}/actuator/health"
```

---

## Notes

1. **Replace URLs**: Don't forget to replace `${BASE_URL}` or `YOUR_PRODUCTION_URL`
2. **Save User IDs**: After creating users, save their IDs for subsequent requests
3. **URL Encoding**: Email addresses with @ need URL encoding in path parameters
4. **HTTPS**: Always use HTTPS in production
5. **jq Tool**: Install jq for pretty JSON output: `brew install jq` (macOS) or `apt-get install jq` (Linux)
6. **Response Codes**: 
   - 200 = Success (GET, PUT)
   - 201 = Created (POST)
   - 204 = No Content (DELETE)
   - 400 = Bad Request
   - 404 = Not Found

