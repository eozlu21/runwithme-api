# RunWithMe WebSocket Chat API Documentation

This document provides comprehensive frontend integration guide for the real-time chat functionality using STOMP over WebSocket.

## Table of Contents

1. [Overview](#overview)
2. [Connection Setup](#connection-setup)
3. [Authentication](#authentication)
4. [STOMP Destinations](#stomp-destinations)
5. [Message Types & Payloads](#message-types--payloads)
6. [TypeScript Interfaces](#typescript-interfaces)
7. [Code Examples](#code-examples)
8. [Error Handling](#error-handling)
9. [Reconnection Strategy](#reconnection-strategy)
10. [REST API Endpoints](#rest-api-endpoints)

---

## Overview

The RunWithMe chat system uses **STOMP over WebSocket** with **SockJS** fallback for real-time messaging. All WebSocket messages are wrapped in a `ChatEvent` object that contains a `type` field to distinguish between different event types.

### Key Features

- **Real-time message delivery**: Messages are instantly delivered to connected recipients
- **Read receipts**: When you mark messages as read, the original sender is notified in real-time
- **Unread count**: REST endpoint to get total unread messages for badge display
- **Offline support**: Messages are persisted and can be fetched via REST when reconnecting

---

## Connection Setup

### Dependencies

Install the required libraries:

```bash
# npm
npm install @stomp/stompjs sockjs-client

# yarn
yarn add @stomp/stompjs sockjs-client

# For TypeScript types
npm install --save-dev @types/sockjs-client
```

### WebSocket Endpoint

| Environment | URL |
|-------------|-----|
| Development | `ws://localhost:8080/ws` |
| Production | `wss://your-api-domain.com/ws` |

### Connection with SockJS

```typescript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/ws'; // Use https:// in production

const stompClient = new Client({
  webSocketFactory: () => new SockJS(WS_URL),
  connectHeaders: {
    Authorization: `Bearer ${yourJwtToken}`,
  },
  debug: (str) => console.log('[STOMP]', str),
  reconnectDelay: 5000,
  heartbeatIncoming: 4000,
  heartbeatOutgoing: 4000,
});
```

---

## Authentication

Authentication is performed during the STOMP CONNECT handshake using JWT token in the `Authorization` header.

### Connect Headers

```typescript
{
  Authorization: 'Bearer <your-jwt-token>'
}
```

### Important Notes

1. **Token must be valid** - Expired or invalid tokens will cause connection failure
2. **Token refresh** - If your token expires, disconnect and reconnect with a new token
3. **No anonymous connections** - All WebSocket connections require authentication

---

## STOMP Destinations

### Subscribe Destinations (Receiving Messages)

| Destination | Description | Payload |
|-------------|-------------|---------|
| `/user/queue/messages` | **Personal message queue** - Receives all chat events (new messages, read receipts) | `ChatEvent` |
| `/topic/public` | Public broadcast messages (optional) | `ChatEvent` |

### Send Destinations (Sending Messages)

| Destination | Description | Payload |
|-------------|-------------|---------|
| `/app/chat` | Send a private message | `CreateMessageRequest` |

---

## Message Types & Payloads

All messages received on `/user/queue/messages` are wrapped in a `ChatEvent` object:

### ChatEvent Structure

```json
{
  "type": "NEW_MESSAGE" | "READ_RECEIPT",
  "payload": { ... },
  "timestamp": "2025-12-30T14:30:00.000Z"
}
```

### Event Type: `NEW_MESSAGE`

Received when someone sends you a message OR when your own sent message is confirmed.

```json
{
  "type": "NEW_MESSAGE",
  "payload": {
    "id": 123,
    "senderId": "uuid-of-sender",
    "recipientId": "uuid-of-recipient",
    "senderUsername": "john_doe",
    "recipientUsername": "jane_doe",
    "content": "Hello there!",
    "createdAt": "2025-12-30T14:30:00.000Z",
    "isRead": false
  },
  "timestamp": "2025-12-30T14:30:00.000Z"
}
```

**How to handle:**
- If `senderId` matches current user → This is confirmation of your sent message
- If `recipientId` matches current user → This is an incoming message from someone else

### Event Type: `READ_RECEIPT`

Received when someone reads your messages.

```json
{
  "type": "READ_RECEIPT",
  "payload": {
    "readByUserId": "uuid-of-reader",
    "readByUsername": "jane_doe",
    "messageIds": [123, 124, 125]
  },
  "timestamp": "2025-12-30T14:35:00.000Z"
}
```

**How to handle:**
- Update the `isRead` status to `true` for the messages with IDs in `messageIds`
- This allows you to show "read" checkmarks in your UI

---

## TypeScript Interfaces

Copy these interfaces into your frontend codebase:

```typescript
// ==================== Enums ====================

export enum MessageType {
  NEW_MESSAGE = 'NEW_MESSAGE',
  READ_RECEIPT = 'READ_RECEIPT',
}

// ==================== DTOs ====================

export interface MessageDto {
  id: number;
  senderId: string; // UUID
  recipientId: string; // UUID
  senderUsername: string | null;
  recipientUsername: string | null;
  content: string;
  createdAt: string; // ISO 8601 datetime
  isRead: boolean;
}

export interface ReadReceiptPayload {
  readByUserId: string; // UUID
  readByUsername: string;
  messageIds: number[];
}

export interface ChatEvent {
  type: MessageType;
  payload: MessageDto | ReadReceiptPayload;
  timestamp: string; // ISO 8601 datetime
}

// Type guards for payload discrimination
export function isNewMessageEvent(event: ChatEvent): event is ChatEvent & { payload: MessageDto } {
  return event.type === MessageType.NEW_MESSAGE;
}

export function isReadReceiptEvent(event: ChatEvent): event is ChatEvent & { payload: ReadReceiptPayload } {
  return event.type === MessageType.READ_RECEIPT;
}

// ==================== Request DTOs ====================

export interface CreateMessageRequest {
  recipientId: string; // UUID of the recipient
  content: string;
}

export interface MarkMessagesReadRequest {
  messageIds: number[];
}

// ==================== Response DTOs ====================

export interface MarkMessagesReadResponse {
  updatedCount: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
```

---

## Code Examples

### Complete React Hook Example

```typescript
import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { 
  ChatEvent, 
  MessageType, 
  MessageDto, 
  ReadReceiptPayload,
  CreateMessageRequest,
  isNewMessageEvent,
  isReadReceiptEvent 
} from './types';

const WS_URL = process.env.REACT_APP_WS_URL || 'http://localhost:8080/ws';

interface UseChatWebSocketOptions {
  token: string;
  onNewMessage?: (message: MessageDto) => void;
  onReadReceipt?: (payload: ReadReceiptPayload) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (error: Error) => void;
}

export function useChatWebSocket({
  token,
  onNewMessage,
  onReadReceipt,
  onConnected,
  onDisconnected,
  onError,
}: UseChatWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    if (!token) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        if (process.env.NODE_ENV === 'development') {
          console.log('[STOMP]', str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: () => {
        console.log('WebSocket connected');
        setIsConnected(true);
        onConnected?.();

        // Subscribe to personal message queue
        client.subscribe('/user/queue/messages', (message: IMessage) => {
          try {
            const event: ChatEvent = JSON.parse(message.body);
            handleChatEvent(event);
          } catch (error) {
            console.error('Failed to parse chat event:', error);
          }
        });
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setIsConnected(false);
        onDisconnected?.();
      },

      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
        onError?.(new Error(frame.headers['message'] || 'STOMP error'));
      },

      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        onError?.(new Error('WebSocket connection error'));
      },
    });

    const handleChatEvent = (event: ChatEvent) => {
      switch (event.type) {
        case MessageType.NEW_MESSAGE:
          if (isNewMessageEvent(event)) {
            onNewMessage?.(event.payload);
          }
          break;

        case MessageType.READ_RECEIPT:
          if (isReadReceiptEvent(event)) {
            onReadReceipt?.(event.payload);
          }
          break;

        default:
          console.warn('Unknown chat event type:', event.type);
      }
    };

    client.activate();
    clientRef.current = client;

    return () => {
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
    };
  }, [token, onNewMessage, onReadReceipt, onConnected, onDisconnected, onError]);

  // Send a private message via WebSocket
  const sendMessage = useCallback((request: CreateMessageRequest) => {
    if (!clientRef.current?.active) {
      console.error('Cannot send message: WebSocket not connected');
      return false;
    }

    clientRef.current.publish({
      destination: '/app/chat',
      body: JSON.stringify(request),
    });

    return true;
  }, []);

  // Disconnect manually
  const disconnect = useCallback(() => {
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
    }
  }, []);

  return {
    isConnected,
    sendMessage,
    disconnect,
  };
}
```

### Usage in a Component

```tsx
import React, { useState, useEffect } from 'react';
import { useChatWebSocket } from './useChatWebSocket';
import { MessageDto, ReadReceiptPayload, CreateMessageRequest } from './types';

interface ChatComponentProps {
  token: string;
  currentUserId: string;
  recipientId: string;
}

export function ChatComponent({ token, currentUserId, recipientId }: ChatComponentProps) {
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [inputText, setInputText] = useState('');

  const { isConnected, sendMessage } = useChatWebSocket({
    token,
    
    onNewMessage: (message) => {
      console.log('Received new message:', message);
      
      // Add message to list (avoid duplicates)
      setMessages((prev) => {
        const exists = prev.some((m) => m.id === message.id);
        if (exists) return prev;
        return [...prev, message];
      });
    },
    
    onReadReceipt: (payload) => {
      console.log('Received read receipt:', payload);
      
      // Update isRead status for the messages
      setMessages((prev) =>
        prev.map((msg) =>
          payload.messageIds.includes(msg.id)
            ? { ...msg, isRead: true }
            : msg
        )
      );
    },
    
    onConnected: () => {
      console.log('Chat connected!');
    },
    
    onError: (error) => {
      console.error('Chat error:', error);
    },
  });

  const handleSend = () => {
    if (!inputText.trim()) return;

    const request: CreateMessageRequest = {
      recipientId,
      content: inputText.trim(),
    };

    const success = sendMessage(request);
    if (success) {
      setInputText('');
    }
  };

  return (
    <div>
      <div>Status: {isConnected ? '🟢 Connected' : '🔴 Disconnected'}</div>
      
      <div className="messages">
        {messages.map((msg) => (
          <div 
            key={msg.id} 
            className={msg.senderId === currentUserId ? 'sent' : 'received'}
          >
            <p>{msg.content}</p>
            <span>{msg.isRead ? '✓✓' : '✓'}</span>
            <time>{new Date(msg.createdAt).toLocaleTimeString()}</time>
          </div>
        ))}
      </div>

      <div className="input-area">
        <input
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSend()}
          placeholder="Type a message..."
        />
        <button onClick={handleSend} disabled={!isConnected}>
          Send
        </button>
      </div>
    </div>
  );
}
```

### Vanilla JavaScript Example

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class ChatWebSocket {
  constructor(wsUrl, token) {
    this.wsUrl = wsUrl;
    this.token = token;
    this.client = null;
    this.messageHandlers = [];
    this.readReceiptHandlers = [];
  }

  connect() {
    this.client = new Client({
      webSocketFactory: () => new SockJS(this.wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${this.token}`,
      },
      reconnectDelay: 5000,
      
      onConnect: () => {
        console.log('Connected to chat');
        
        this.client.subscribe('/user/queue/messages', (message) => {
          const event = JSON.parse(message.body);
          this.handleEvent(event);
        });
      },
    });

    this.client.activate();
  }

  handleEvent(event) {
    switch (event.type) {
      case 'NEW_MESSAGE':
        this.messageHandlers.forEach(handler => handler(event.payload));
        break;
      case 'READ_RECEIPT':
        this.readReceiptHandlers.forEach(handler => handler(event.payload));
        break;
    }
  }

  sendMessage(recipientId, content) {
    if (!this.client?.active) {
      throw new Error('Not connected');
    }
    
    this.client.publish({
      destination: '/app/chat',
      body: JSON.stringify({ recipientId, content }),
    });
  }

  onMessage(handler) {
    this.messageHandlers.push(handler);
  }

  onReadReceipt(handler) {
    this.readReceiptHandlers.push(handler);
  }

  disconnect() {
    this.client?.deactivate();
  }
}

// Usage
const chat = new ChatWebSocket('http://localhost:8080/ws', 'your-jwt-token');

chat.onMessage((message) => {
  console.log('New message:', message);
});

chat.onReadReceipt((receipt) => {
  console.log('Messages read:', receipt.messageIds);
});

chat.connect();

// Send a message
chat.sendMessage('recipient-uuid', 'Hello!');
```

---

## Error Handling

### Connection Errors

```typescript
const client = new Client({
  // ... config

  onStompError: (frame) => {
    // STOMP protocol level error
    const errorMessage = frame.headers['message'];
    console.error('STOMP error:', errorMessage);
    
    // Common errors:
    // - "Unauthorized" - Invalid or expired JWT token
    // - "Access Denied" - User doesn't have permission
  },

  onWebSocketError: (event) => {
    // WebSocket transport error
    console.error('WebSocket error:', event);
    
    // Could be:
    // - Network issue
    // - Server unavailable
    // - CORS error
  },

  onWebSocketClose: (event) => {
    console.log('WebSocket closed:', event.code, event.reason);
    
    // Common close codes:
    // 1000 - Normal closure
    // 1001 - Going away (server shutting down)
    // 1006 - Abnormal closure (no close frame)
  },
});
```

### Message Parsing Errors

```typescript
client.subscribe('/user/queue/messages', (message) => {
  try {
    const event: ChatEvent = JSON.parse(message.body);
    
    // Validate event structure
    if (!event.type || !event.payload) {
      console.error('Invalid event structure:', event);
      return;
    }
    
    handleChatEvent(event);
  } catch (error) {
    console.error('Failed to parse message:', error, message.body);
  }
});
```

---

## Reconnection Strategy

The `@stomp/stompjs` library handles reconnection automatically with `reconnectDelay`. However, you should handle token refresh:

```typescript
const createClient = (token: string) => {
  return new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    
    onStompError: async (frame) => {
      if (frame.headers['message']?.includes('Unauthorized')) {
        // Token expired - get a new one
        const newToken = await refreshToken();
        
        // Reconnect with new token
        client.deactivate();
        const newClient = createClient(newToken);
        newClient.activate();
      }
    },
  });
};
```

### Handling Missed Messages

When reconnecting after being offline, fetch missed messages via REST:

```typescript
const fetchMissedMessages = async (otherUserId: string, lastMessageId: number) => {
  const response = await fetch(
    `/api/v1/chat/history/${otherUserId}?page=0&size=50`,
    {
      headers: { Authorization: `Bearer ${token}` },
    }
  );
  
  const data = await response.json();
  
  // Filter messages newer than last known message
  return data.content.filter((msg: MessageDto) => msg.id > lastMessageId);
};
```

---

## REST API Endpoints

Use these REST endpoints alongside WebSocket for complete functionality:

### Send Message (Alternative to WebSocket)

```
POST /api/v1/chat/send
Authorization: Bearer <token>
Content-Type: application/json

{
  "recipientId": "uuid-string",
  "content": "Hello!"
}
```

**Response:** `MessageDto`

### Get Chat History

```
GET /api/v1/chat/history/{otherUserId}?page=0&size=20
Authorization: Bearer <token>
```

**Response:** `PageResponse<MessageDto>`

### Get All Messages

```
GET /api/v1/chat/history?page=0&size=20&friendsOnly=false
Authorization: Bearer <token>
```

**Response:** `PageResponse<MessageDto>`

### Mark Messages as Read

```
POST /api/v1/chat/read
Authorization: Bearer <token>
Content-Type: application/json

{
  "messageIds": [1, 2, 3]
}
```

**Response:** `{ "updatedCount": 3 }`

**Side Effect:** Sends `READ_RECEIPT` event to original sender(s) via WebSocket

### Get Unread Count

```
GET /api/v1/chat/unread-count
Authorization: Bearer <token>
```

**Response:** `{ "unreadCount": 5 }`

**Use Case:** Display notification badge count on app load

### Get Connected Users (Debug)

```
GET /api/v1/chat/connected-users
Authorization: Bearer <token>
```

**Response:** `["username1", "username2"]`

---

## Quick Reference Card

| Action | Method |
|--------|--------|
| Connect | SockJS to `/ws` with `Authorization: Bearer <token>` header |
| Subscribe to messages | `/user/queue/messages` |
| Send private message | Publish to `/app/chat` with `CreateMessageRequest` |
| Handle new message | Check `event.type === 'NEW_MESSAGE'` |
| Handle read receipt | Check `event.type === 'READ_RECEIPT'` |
| Mark messages read | `POST /api/v1/chat/read` (triggers read receipt push) |
| Get unread count | `GET /api/v1/chat/unread-count` |
| Load history | `GET /api/v1/chat/history/{userId}` |

---

## Troubleshooting

### "Unauthorized" on connect

- Check JWT token is valid and not expired
- Ensure `Authorization` header format is exactly `Bearer <token>` (note the space)

### Not receiving messages

1. Verify subscription to `/user/queue/messages` (not `/queue/messages`)
2. Check that connection completed successfully (wait for `onConnect` callback)
3. Verify sender is using correct recipient UUID

### Read receipts not arriving

1. Ensure sender is connected to WebSocket
2. Verify `POST /api/v1/chat/read` call completed successfully
3. Check message IDs are valid and belong to the authenticated user

### Messages duplicated

- Check for re-subscription on reconnect (unsubscribe first)
- Use message ID for deduplication in your state management

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-12-30 | Initial release with ChatEvent wrapper, read receipts, unread count |
