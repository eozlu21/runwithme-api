# Repository Directory

Data access layer - Spring Data JPA interfaces for database operations.

## Purpose

Repositories provide:
- CRUD operations via Spring Data JPA
- Custom query methods using method naming conventions
- Native queries for complex operations (especially geospatial)
- MongoDB document access for chat messages

## Files

| File | Entity | Database |
|------|--------|----------|
| `UserRepository.kt` | `User` | PostgreSQL |
| `UserProfileRepository.kt` | `UserProfile` | PostgreSQL |
| `RouteRepository.kt` | `Route` | PostgreSQL |
| `RoutePointRepository.kt` | `RoutePoint` | PostgreSQL |
| `RouteLikeRepository.kt` | `RouteLike` | PostgreSQL |
| `FeedPostRepository.kt` | `FeedPost` | PostgreSQL |
| `FeedPostCommentRepository.kt` | `FeedPostComment` | PostgreSQL |
| `FeedPostLikeRepository.kt` | `FeedPostLike` | PostgreSQL |
| `FeedPostRouteRepository.kt` | `FeedPostRoute` | PostgreSQL |
| `FeedPostRunSessionRepository.kt` | `FeedPostRunSession` | PostgreSQL |
| `RunSessionRepository.kt` | `RunSession` | PostgreSQL |
| `RunSessionPointRepository.kt` | `RunSessionPoint` | PostgreSQL |
| `FriendshipRepository.kt` | `Friendship` | PostgreSQL |
| `FriendRequestRepository.kt` | `FriendRequest` | PostgreSQL |
| `MessageRepository.kt` | `Message` | MongoDB |
| `EmailVerificationTokenRepository.kt` | `EmailVerificationToken` | PostgreSQL |

## Conventions

- Extend `JpaRepository<Entity, IdType>` for PostgreSQL entities
- Extend `MongoRepository<Document, IdType>` for MongoDB documents
- Use method naming for simple queries: `findByEmail`, `findByUserId`
- Use `@Query` for complex JPQL/native queries
- UUID for User IDs, Long for other entity IDs

## Common Patterns

```kotlin
// Standard JPA repository
interface ResourceRepository : JpaRepository<Resource, Long> {
    fun findByUserId(userId: UUID): List<Resource>
    fun findByUserIdAndStatus(userId: UUID, status: Status): List<Resource>
    fun existsByUserIdAndResourceId(userId: UUID, resourceId: Long): Boolean

    @Query("SELECT r FROM Resource r WHERE r.createdAt > :since ORDER BY r.createdAt DESC")
    fun findRecentResources(@Param("since") since: OffsetDateTime): List<Resource>
}

// MongoDB repository
interface MessageRepository : MongoRepository<Message, String> {
    fun findByConversationIdOrderByTimestampDesc(conversationId: String): List<Message>
}
```

## Geospatial Queries

Some repositories use PostGIS for geospatial operations:
- `RouteRepository` and `RoutePointRepository` handle coordinate data
- Hibernate Spatial types used for point/geometry columns
