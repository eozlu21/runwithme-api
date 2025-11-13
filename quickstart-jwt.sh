#!/bin/bash

# Quick Start Script for JWT Authentication
# Run this script to test your JWT implementation

echo "=================================="
echo "JWT Authentication Quick Start"
echo "=================================="
echo ""

# Check if server is running
echo "1. Checking if server is running..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running"
    echo ""
    echo "Start the server with:"
    echo "  ./gradlew bootRun"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo ""
echo "2. Testing registration..."
TIMESTAMP=$(date +%s)
USERNAME="demo_$TIMESTAMP"
EMAIL="demo_$TIMESTAMP@example.com"
PASSWORD="password123"

REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USERNAME\",
    \"email\": \"$EMAIL\",
    \"password\": \"$PASSWORD\"
  }")

ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.accessToken')

if [ "$ACCESS_TOKEN" != "null" ] && [ -n "$ACCESS_TOKEN" ]; then
    echo "✅ Registration successful!"
    echo ""
    echo "Your credentials:"
    echo "  Username: $USERNAME"
    echo "  Password: $PASSWORD"
    echo ""
    echo "Your access token (first 50 chars):"
    echo "  ${ACCESS_TOKEN:0:50}..."
    echo ""

    # Save to file for easy access
    cat > jwt-demo-credentials.txt <<EOF
Username: $USERNAME
Password: $PASSWORD
Email: $EMAIL

Access Token:
$ACCESS_TOKEN

Refresh Token:
$(echo "$REGISTER_RESPONSE" | jq -r '.refreshToken')

Created: $(date)
EOF

    echo "Full credentials saved to: jwt-demo-credentials.txt"
    echo ""

    # Test protected endpoint
    echo "3. Testing protected endpoint with token..."
    USERS_RESPONSE=$(curl -s -X GET http://localhost:8080/api/v1/users \
      -H "Authorization: Bearer $ACCESS_TOKEN")

    if echo "$USERS_RESPONSE" | jq -e '.content' > /dev/null 2>&1; then
        echo "✅ Successfully accessed protected endpoint!"
        USER_COUNT=$(echo "$USERS_RESPONSE" | jq '.content | length')
        echo "   Found $USER_COUNT user(s) in the system"
    else
        echo "⚠️  Could not access protected endpoint"
    fi

    echo ""
    echo "=================================="
    echo "🎉 JWT Authentication is working!"
    echo "=================================="
    echo ""
    echo "Next steps:"
    echo "1. View API docs: http://localhost:8080/swagger-ui.html"
    echo "2. Run full tests: ./test-jwt-auth.sh"
    echo "3. Read guide: JWT_AUTHENTICATION_GUIDE.md"
    echo "4. Quick reference: JWT_QUICK_REFERENCE.md"
    echo ""
    echo "To use your token in curl:"
    echo "  curl -H 'Authorization: Bearer $ACCESS_TOKEN' http://localhost:8080/api/v1/users"
    echo ""
    echo "To use in Swagger UI:"
    echo "  1. Go to http://localhost:8080/swagger-ui.html"
    echo "  2. Click 'Authorize' button"
    echo "  3. Enter: Bearer $ACCESS_TOKEN"
    echo ""
else
    echo "❌ Registration failed"
    echo ""
    echo "Response:"
    echo "$REGISTER_RESPONSE" | jq '.'
    echo ""
    echo "Check that:"
    echo "1. Database is accessible"
    echo "2. Application started successfully"
    echo "3. No duplicate username/email"
fi

