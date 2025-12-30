# RunWithMe Push Notification System

This document describes the push notification system for the RunWithMe API, which uses Firebase Cloud Messaging (FCM) to send notifications to iOS, Android, and web clients.

## Table of Contents

1. [Overview](#overview)
2. [Server Setup](#server-setup)
3. [Mobile Client Integration](#mobile-client-integration)
4. [API Endpoints](#api-endpoints)
5. [Notification Types](#notification-types)
6. [Best Practices](#best-practices)

---

## Overview

The push notification system automatically sends notifications when:

- **New Messages**: When a user sends a chat message and the recipient is offline (not connected via WebSocket)
- **Friend Requests**: When someone sends a friend request
- **Friend Request Accepted**: When someone accepts your friend request
- **Comments**: When someone comments on your post
- **Likes**: When someone likes your post

### Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Mobile App    │────▶│   RunWithMe API  │────▶│   Firebase FCM  │
│  (iOS/Android)  │◀────│                  │◀────│                 │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                        │
        │ 1. Register FCM token  │
        │ 2. Receive messages    │
        └────────────────────────┘
```

---

## Server Setup

### 1. Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use an existing one
3. Enable Cloud Messaging in your project settings

### 2. Generate Service Account Key

1. In Firebase Console, go to Project Settings > Service Accounts
2. Click "Generate new private key"
3. Save the JSON file securely (never commit to Git!)

### 3. Configure Environment Variables

Add these environment variables to your `.env` file:

```env
# Enable push notifications
FIREBASE_ENABLED=true

# Your Firebase project ID
FIREBASE_PROJECT_ID=your-firebase-project-id

# Option 1: Path to service account JSON file
FIREBASE_CREDENTIALS_PATH=/path/to/firebase-service-account.json

# Option 2: JSON string (for CI/CD and secrets management)
# FIREBASE_CREDENTIALS_JSON={"type":"service_account","project_id":"...",...}
```

### 4. Verify Setup

Start the server and check the logs for:
```
Firebase initialized successfully for project: your-firebase-project-id
```

Or check the status endpoint:
```bash
curl http://localhost:8080/api/v1/notifications/status
```

---

## Mobile Client Integration

### Android Setup

#### 1. Add Firebase SDK

In your `app/build.gradle`:
```gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-messaging'
}
```

#### 2. Get FCM Token

```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val token = task.result
        // Register token with your backend
        registerDeviceToken(token, "ANDROID")
    }
}
```

#### 3. Handle Token Refresh

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Register new token with backend
        registerDeviceToken(token, "ANDROID")
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        // Handle incoming notification
        message.notification?.let {
            showNotification(it.title, it.body)
        }
    }
}
```

#### 4. Create Notification Channels (Android 8+)

```kotlin
private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channels = listOf(
            NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("social", "Social", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("activity", "Activity", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("runs", "Run Invitations", NotificationManager.IMPORTANCE_HIGH),
        )
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }
}
```

### iOS Setup

#### 1. Add Firebase SDK

In your `Podfile`:
```ruby
pod 'Firebase/Messaging'
```

#### 2. Configure APNs

1. Create an APNs key in Apple Developer Portal
2. Upload the key to Firebase Console > Cloud Messaging > APNs Authentication Key

#### 3. Get FCM Token

```swift
Messaging.messaging().token { token, error in
    if let token = token {
        // Register token with your backend
        self.registerDeviceToken(token: token, platform: "IOS")
    }
}
```

#### 4. Handle Token Refresh

```swift
extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if let token = fcmToken {
            registerDeviceToken(token: token, platform: "IOS")
        }
    }
}
```

---

## API Endpoints

### Register Device Token

Register a device for push notifications.

```http
POST /api/v1/notifications/devices
Authorization: Bearer <token>
Content-Type: application/json

{
  "token": "fcm-device-token-from-firebase-sdk",
  "platform": "ANDROID",  // or "IOS", "WEB"
  "deviceName": "John's Pixel 8"  // optional
}
```

**Response:**
```json
{
  "id": 1,
  "platform": "ANDROID",
  "deviceName": "John's Pixel 8",
  "isActive": true,
  "createdAt": "2025-12-30T10:30:00.000Z"
}
```

### Unregister Device Token

Remove a device from receiving notifications.

```http
DELETE /api/v1/notifications/devices
Authorization: Bearer <token>
Content-Type: application/json

{
  "token": "fcm-device-token-to-remove"
}
```

### Get Registered Devices

List all devices registered for the current user.

```http
GET /api/v1/notifications/devices
Authorization: Bearer <token>
```

**Response:**
```json
{
  "devices": [
    {
      "id": 1,
      "platform": "ANDROID",
      "deviceName": "John's Pixel 8",
      "isActive": true,
      "createdAt": "2025-12-30T10:30:00.000Z"
    }
  ],
  "activeCount": 1
}
```

### Unregister All Devices

Deactivate all devices (useful for "logout from all devices").

```http
DELETE /api/v1/notifications/devices/all
Authorization: Bearer <token>
```

**Response:**
```json
{
  "deactivatedCount": 3
}
```

### Check Status

Check if push notifications are enabled on the server.

```http
GET /api/v1/notifications/status
```

**Response:**
```json
{
  "enabled": true,
  "provider": "Firebase Cloud Messaging"
}
```

---

## Notification Types

### NEW_MESSAGE

Sent when a chat message is delivered and the recipient is offline.

```json
{
  "title": "john_doe",
  "body": "Hey, are you free for a run tomorrow?",
  "data": {
    "type": "NEW_MESSAGE",
    "messageId": "123",
    "senderId": "uuid-of-sender",
    "senderUsername": "john_doe",
    "conversationId": "uuid-of-sender",
    "click_action": "OPEN_CHAT"
  }
}
```

### FRIEND_REQUEST

Sent when someone sends a friend request.

```json
{
  "title": "New Friend Request",
  "body": "john_doe wants to be your friend",
  "data": {
    "type": "FRIEND_REQUEST",
    "requestId": "456",
    "senderUsername": "john_doe",
    "click_action": "OPEN_FRIEND_REQUESTS"
  }
}
```

### FRIEND_REQUEST_ACCEPTED

Sent when someone accepts your friend request.

```json
{
  "title": "Friend Request Accepted",
  "body": "jane_doe accepted your friend request",
  "data": {
    "type": "FRIEND_REQUEST_ACCEPTED",
    "accepterUsername": "jane_doe",
    "click_action": "OPEN_FRIENDS"
  }
}
```

### NEW_COMMENT

Sent when someone comments on your post.

```json
{
  "title": "New Comment",
  "body": "john_doe commented: Great run!",
  "data": {
    "type": "NEW_COMMENT",
    "postId": "789",
    "commenterUsername": "john_doe",
    "click_action": "OPEN_POST"
  }
}
```

### NEW_LIKE

Sent when someone likes your post.

```json
{
  "title": "New Like",
  "body": "john_doe liked your post",
  "data": {
    "type": "NEW_LIKE",
    "postId": "789",
    "likerUsername": "john_doe",
    "click_action": "OPEN_POST"
  }
}
```

---

## Best Practices

### Token Management

1. **Register on Login**: Always register the device token after successful authentication
2. **Handle Token Refresh**: Update the token when Firebase refreshes it
3. **Unregister on Logout**: Remove the token when the user logs out
4. **Multiple Devices**: Users can have multiple devices; all will receive notifications

### Error Handling

The server automatically:
- Deactivates invalid tokens (e.g., when app is uninstalled)
- Handles rate limiting from Firebase
- Retries failed sends with exponential backoff (TODO)

### Notification Channels (Android)

The server specifies these channel IDs:
- `messages` - For chat messages (high importance)
- `social` - For friend requests (default importance)
- `activity` - For likes and comments (low importance)
- `runs` - For run invitations (high importance)
- `general` - For other notifications

Make sure to create these channels in your Android app.

### Deep Linking

Use the `click_action` field in notification data to navigate to the correct screen:
- `OPEN_CHAT` - Open chat with the sender
- `OPEN_FRIEND_REQUESTS` - Open friend requests screen
- `OPEN_FRIENDS` - Open friends list
- `OPEN_POST` - Open specific post
- `OPEN_APP` - Default app screen

---

## Troubleshooting

### Notifications not arriving

1. Check server logs for Firebase initialization
2. Verify FCM token is valid
3. Check if token is registered: `GET /api/v1/notifications/devices`
4. Test with Firebase Console's "Cloud Messaging" tester

### Token registration fails

1. Verify Firebase SDK is properly configured in your app
2. Check network connectivity
3. Ensure Firebase project has Cloud Messaging enabled

### Duplicate notifications

1. Make sure you're not registering the same token multiple times
2. Check for multiple Firebase initializations in your app

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-12-30 | Initial release with message, friend request, and activity notifications |
