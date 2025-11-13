#!/bin/bash

# JWT Authentication Test Script
# This script tests the JWT authentication endpoints

API_URL="${1:-http://localhost:8080}"
USERNAME="testuser_$(date +%s)"
EMAIL="test_$(date +%s)@example.com"
PASSWORD="testpass123"

echo "=== JWT Authentication Test ==="
echo "API URL: $API_URL"
echo ""

# Test 1: Register
echo "1. Testing registration..."
REGISTER_RESPONSE=$(curl -s -X POST "$API_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USERNAME\",
    \"email\": \"$EMAIL\",
    \"password\": \"$PASSWORD\"
  }")

echo "$REGISTER_RESPONSE" | jq '.'

# Extract tokens
ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.refreshToken')

if [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Registration failed!"
  exit 1
fi
echo "✅ Registration successful!"
echo ""

# Test 2: Access protected endpoint with token
echo "2. Testing protected endpoint with valid token..."
USERS_RESPONSE=$(curl -s -X GET "$API_URL/api/v1/users" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "$USERS_RESPONSE" | jq '.'
if echo "$USERS_RESPONSE" | jq -e '.content' > /dev/null 2>&1; then
  echo "✅ Protected endpoint access successful!"
else
  echo "❌ Protected endpoint access failed!"
fi
echo ""

# Test 3: Access protected endpoint without token
echo "3. Testing protected endpoint without token..."
NO_AUTH_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X GET "$API_URL/api/v1/users")
HTTP_STATUS=$(echo "$NO_AUTH_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)

if [ "$HTTP_STATUS" = "401" ] || [ "$HTTP_STATUS" = "403" ]; then
  echo "✅ Correctly rejected request without token (HTTP $HTTP_STATUS)"
else
  echo "❌ Should have rejected request without token"
fi
echo ""

# Test 4: Login with same credentials
echo "4. Testing login..."
LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USERNAME\",
    \"password\": \"$PASSWORD\"
  }")

echo "$LOGIN_RESPONSE" | jq '.'
NEW_ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')

if [ "$NEW_ACCESS_TOKEN" != "null" ]; then
  echo "✅ Login successful!"
else
  echo "❌ Login failed!"
fi
echo ""

# Test 5: Refresh token
echo "5. Testing token refresh..."
REFRESH_RESPONSE=$(curl -s -X POST "$API_URL/api/v1/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }")

echo "$REFRESH_RESPONSE" | jq '.'
REFRESHED_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.accessToken')

if [ "$REFRESHED_ACCESS_TOKEN" != "null" ]; then
  echo "✅ Token refresh successful!"
else
  echo "❌ Token refresh failed!"
fi
echo ""

# Test 6: Login with wrong password
echo "6. Testing login with wrong password..."
WRONG_PASSWORD_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$API_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USERNAME\",
    \"password\": \"wrongpassword\"
  }")

HTTP_STATUS=$(echo "$WRONG_PASSWORD_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)

if [ "$HTTP_STATUS" = "401" ]; then
  echo "✅ Correctly rejected wrong password (HTTP $HTTP_STATUS)"
else
  echo "❌ Should have rejected wrong password"
fi
echo ""

echo "=== Test Summary ==="
echo "All basic JWT authentication flows have been tested."
echo ""
echo "Your tokens:"
echo "Access Token: $ACCESS_TOKEN"
echo "Refresh Token: $REFRESH_TOKEN"

