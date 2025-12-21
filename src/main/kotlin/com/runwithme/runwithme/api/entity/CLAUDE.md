# Entity Directory

JPA entity models - represents database tables and MongoDB documents.

## Purpose

Entities define:
- Database table structure via JPA annotations
- Relationships between tables (using IDs, not @OneToMany)
- Field constraints and validations
- Enum types for fixed value sets

## Files by Domain

### User Domain
| File | Description |
|------|-------------|
| `User.kt` | Core user account (email, password hash, verification status) |
| `UserProfile.kt` | Extended profile info (name, bio, profile picture) |
| `ProfileVisibility.kt` | Enum: PUBLIC, FRIENDS_ONLY, PRIVATE |

### Route Domain
| File | Description |
|------|-------------|
| `Route.kt` | Running route with metadata (name, distance, elevation) |
| `RoutePoint.kt` | Individual GPS coordinate on a route |
| `RouteLike.kt` | User's like on a route |

### Feed Domain
| File | Description |
|------|-------------|
| `FeedPost.kt` | Post with type (TEXT, ROUTE, RUN_SESSION) |
| `FeedPostComment.kt` | Comment on a post |
| `FeedPostLike.kt` | User's like on a post |
| `FeedPostRoute.kt` | Links a FeedPost to a Route |
| `FeedPostRunSession.kt` | Links a FeedPost to a RunSession |

### Run Session Domain
| File | Description |
|------|-------------|
| `RunSession.kt` | Active or completed running session |
| `RunSessionPoint.kt` | GPS tracking point during a run |

### Friendship Domain
| File | Description |
|------|-------------|
| `Friendship.kt` | Confirmed friendship between two users |
| `FriendRequest.kt` | Pending friend request |
| `FriendRequestStatus.kt` | Enum: PENDING, ACCEPTED, REJECTED |

### Other
| File | Description |
|------|-------------|
| `EmailVerificationToken.kt` | Token for email verification flow |
| `Message.kt` | Direct message (MongoDB document) |

## Conventions

- Use `@Entity` and `@Table` for PostgreSQL entities
- Use `@Document` for MongoDB documents
- UUID for User ID, Long for other entity IDs
- `OffsetDateTime` for all timestamps
- Weak relationships: store foreign key as field, not JPA relationship
- No `@OneToMany` or `@ManyToMany` - use repository queries instead

## Common Entity Pattern

```kotlin
@Entity
@Table(name = "resources")
data class Resource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    val updatedAt: OffsetDateTime? = null
)
```

## ID Strategy

- `User`: UUID (generated externally or via `UUID.randomUUID()`)
- All other entities: Long with `GenerationType.IDENTITY`
